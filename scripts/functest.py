"""End-to-end functional check of CullTag over RCON, driving Carpet fake players.

This is the half that `scripts/audit.py` and a plain `runServer` boot cannot reach. Booting
proves the mixin applies; this proves a nametag actually goes away and, more importantly,
actually comes back. Every check here corresponds to something that shipped broken.

  1. a stone wall between two players hides both nametags
  2. swapping that wall for glass, then iron bars, brings them back
  3. a named cow behind a wall is hidden too
  4. moving past max_distance releases the override (the stuck-at-range bug)
  5. an entity that dies while hidden is released (the same bug, different reason)
  6. /culltag disable restores everything
  7. /culltag enable re-hides with nobody moving (the never-fires-on-first-observation bug)

Usage:
    ./gradlew runServer          # needs Carpet in run/mods, which the dev runtime has
    python scripts/functest.py

Requires rcon in run/server.properties:
    enable-rcon=true
    rcon.password=test
    rcon.port=25575

Exit code 1 if any check fails. It cleans up after itself, so the dev world is not left full
of stone platforms and fake players.
"""
import re
import select
import socket
import struct
import sys
import time

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "test"


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection((HOST, PORT), timeout=10)
        self._send(3, PASSWORD)
        if self._recv()[0] == -1:
            raise SystemExit("rcon auth failed")

    def _send(self, kind, body):
        payload = struct.pack("<ii", 0, kind) + body.encode("utf8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise SystemExit("rcon closed")
            buf += chunk
        return buf

    def _recv(self):
        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        rid, _kind = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode("utf8", "replace")

    def cmd(self, command):
        self._send(2, command)
        return self._recv()[1]


def sweeps(r):
    """Wait for at least two full sweeps so a decision is definitely made and pushed."""
    time.sleep(1.6)


failures = []


def check(label, condition, detail):
    status = "PASS" if condition else "FAIL"
    print(f"[{status}] {label} :: {detail}")
    if not condition:
        failures.append(label)


def hidden_count(r):
    out = r.cmd("culltag stats")
    m = re.search(r"nametags hidden: (\d+)", out)
    if not m:
        raise SystemExit(f"could not parse stats: {out!r}")
    return int(m.group(1)), out


def main():
    r = Rcon()
    print(r.cmd("gamerule minecraft:send_command_feedback true"))

    # A flat arena in the sky so no terrain interferes with the rays.
    r.cmd("forceload add 0 0 16 16")
    r.cmd("fill 0 100 0 12 100 12 minecraft:stone")
    r.cmd("fill 0 101 0 12 105 12 minecraft:air")
    r.cmd("fill 0 101 6 12 103 6 minecraft:stone")
    time.sleep(0.5)

    r.cmd("player Alice spawn at 6 101 3 facing 0 0")
    r.cmd("player Bob spawn at 6 101 9 facing 180 0")
    time.sleep(1.0)
    print(r.cmd("list"))
    sweeps(r)

    n, out = hidden_count(r)
    check("stone wall hides both nametags", n == 2, f"hidden={n} (expected 2) | {out}")

    r.cmd("fill 0 101 6 12 103 6 minecraft:glass")
    sweeps(r)
    n, out = hidden_count(r)
    check("glass wall hides nothing", n == 0, f"hidden={n} (expected 0)")

    r.cmd("fill 0 101 6 12 103 6 minecraft:iron_bars")
    sweeps(r)
    n, _ = hidden_count(r)
    check("iron bars hide nothing", n == 0, f"hidden={n} (expected 0)")

    r.cmd("fill 0 101 6 12 103 6 minecraft:stone")
    sweeps(r)
    n, _ = hidden_count(r)
    check("back to stone re-hides both", n == 2, f"hidden={n} (expected 2)")

    # A named cow on Bob's side of the wall: hidden from Alice, visible to Bob.
    r.cmd('summon minecraft:cow 6 101 9 {CustomName:\'{"text":"Bessie"}\','
          'CustomNameVisible:1b,NoAI:1b,NoGravity:1b}')
    sweeps(r)
    n, out = hidden_count(r)
    check("named cow behind the wall is hidden from the far player",
          n == 3, f"hidden={n} (expected 3: 2 players + cow) | {out}")

    # The 1.1.1 bug: move past max_distance and the override was never taken off.
    # Carpet has no "player tp" action, so this is the vanilla command on a real ServerPlayer.
    print("  tp ->", r.cmd("tp Bob 6 101 200").strip())
    sweeps(r)
    sweeps(r)
    n, out = hidden_count(r)
    # Both player overrides come off. Alice still hides the cow, which is on the far side of
    # the wall from her and well within range, so 1 rather than 0 is the right answer.
    check("moving out of max_distance releases the override",
          n == 1, f"hidden={n} (expected 1: cow still walled off from Alice) | {out}")

    print("  tp back ->", r.cmd("tp Bob 6 101 9").strip())
    sweeps(r)
    sweeps(r)
    n, _ = hidden_count(r)
    check("coming back into range re-hides", n == 3, f"hidden={n} (expected 3)")

    # Same shape, different reason to stop tracking: the cow dies while hidden.
    r.cmd("kill @e[type=minecraft:cow]")
    sweeps(r)
    n, _ = hidden_count(r)
    check("a hidden entity that dies is released", n == 2, f"hidden={n} (expected 2)")

    out = r.cmd("culltag disable")
    print("  disable ->", out.strip())
    sweeps(r)
    n, _ = hidden_count(r)
    check("disable restores everything", n == 0, f"hidden={n} (expected 0)")

    # This is the regression test for the bug where the first observation of an
    # already-blocked pair was never treated as a change: before the fix, nothing was hidden
    # again after a re-enable until somebody walked in and out of cover. The cow is dead by
    # now, so the two players are all that is left to hide.
    r.cmd("culltag enable")
    sweeps(r)
    n, out = hidden_count(r)
    check("enable re-hides without anybody moving",
          n == 2, f"hidden={n} (expected 2, cow is dead by now) | {out}")

    # Cleanup so the dev world is not left full of test scaffolding.
    r.cmd("player Alice kill")
    r.cmd("player Bob kill")
    r.cmd("fill 0 100 0 12 105 12 minecraft:air")
    r.cmd("forceload remove all")

    print()
    if failures:
        print("FAILURES:", ", ".join(failures))
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
