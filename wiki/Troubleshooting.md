Start with `/culltag stats`. It answers most of these in one line.

**Nothing is being hidden at all.**

- `enabled=false` in the config, or `/culltag disable` was run and wrote it there.
- Sweeps climbing with rays at zero means everything is out of range, in another dimension, or
  filtered out as a spectator or invisible. Check `max_distance` against how far apart things
  actually are.
- Sweeps not climbing at all means the tick hook is not running, which usually means the mod
  failed to load. Check the server log for a mixin error at startup; `culltag.mixins.json`
  failing is fatal, so the server would not have started.
- Two players in the same room with a clear view of each other are supposed to see each
  other's nametags. That is the mod working.

**A nametag shows through glass, a pane or a fence.**

Intended. Those blocks are in `#culltag:transparent` because you can see through them, and
hiding a nametag behind something you can see through is the opposite of what the mod is for.
Add blocks to, or remove them from, that tag with a datapack; see
[Configuration](Configuration).

**A named mob's nametag still shows through walls.**

- `cull_entity_nametags=false` in the config.
- The mob is invisible. Invisible entities are deliberately never culled, so that hologram
  armour stands stay readable.

**A nametag is stuck hidden.**

- `/culltag disable` restores everything unconditionally, including overrides a client is
  holding that the server has lost track of. If a nametag comes back on disable and gets stuck
  again on enable, that is a bug worth reporting with the two positions involved.
- After a hot jar swap, run `/culltag disable` then `/culltag enable`. Clients can be holding
  overrides pushed by the previous jar that the new one knows nothing about.

**A nametag disappears when the target is only *nearly* behind cover.**

The ray ends at the nametag, not the eyes, so what matters is whether the floating tag is
reachable, not whether the body is. That is deliberate and matches what the client would
draw.

**Nametags flicker.**

- Check for [PassableFoliage](https://modrinth.com/mod/passable-foliage) or anything else that
  changes block collision shapes on the client only. That is the usual cause and the reason
  PassableFoliage is blocked outright.
- Things sitting on the exact boundary of `max_distance` will flicker as they cross it.
  Nothing to fix, but it explains the symptom.

**Performance.**

`/culltag stats` reports average sweep time over the last 100 sweeps. If it is genuinely a
problem, raise `check_interval_ticks` before lowering `max_distance`: the interval divides the
whole cost, while the distance only trims the things at the edge. Turning off
`cull_entity_nametags` is the other big lever if you have a lot of named visible mobs. Note
that player cost grows with the square of how many players are close together, so a hundred
players spread across a world is cheap and a hundred players in a lobby is not.

**A `culltag_hidden` team is in my `/team list`.**

Left over from 1.1.x, which used it for a feature that no longer exists. It is deleted
automatically the first time the server starts on a version newer than 1.1.1.
