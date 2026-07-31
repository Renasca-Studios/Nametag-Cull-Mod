`config/culltag.properties` is written on first start and rewritten every time it is read, so
a key you delete comes back with its default and an unparseable value is replaced with one.
Edits take effect on `/culltag reload` or a restart.

| Key | Default | Range | What it does |
|---|---|---|---|
| `enabled` | `true` | | Master switch. Turning it off restores every nametag immediately rather than just stopping the sweep. |
| `max_distance` | `32` | 1 to 512 | How far apart two players can be and still be checked. Beyond this they are left exactly as vanilla draws them. |
| `check_interval_ticks` | `10` | 1 to 40 | Server ticks between sweeps. 10 is about twice a second; 4 is about five times a second. |
| `crouch_hides_nametag` | `true` | | Hides a crouching player's nametag from everyone, whether or not anything is in the way. |

Notes on each:

- **`max_distance`** is the main performance dial, because it decides how many pairs get a
  raycast. Raising it past about 64 has little visible effect: vanilla stops drawing player
  nametags at 64 blocks anyway, and at 32 for a sneaking player.
- **`check_interval_ticks`** is a latency dial, not an accuracy one. At 10 a player stepping
  out of cover can keep a hidden nametag for up to half a second. Lower it if that half second
  matters in PvP; the cost scales linearly.
- **`crouch_hides_nametag`** is the one setting that changes vanilla balance rather than
  restoring it, and it has a real side effect: see [Compatibility](Compatibility) before
  turning it on for a server that colours players by scoreboard team. It is on by default.

`enabled` is also written back to the file by `/culltag enable` and `/culltag disable`, so a
toggle made in game survives a restart.
