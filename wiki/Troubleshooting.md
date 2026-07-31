Start with `/culltag stats`. It answers most of these in one line.

**Nothing is being hidden at all.**

- `enabled=false` in the config, or `/culltag disable` was run and wrote it there.
- Sweeps climbing with raycasts at zero means every pair is out of range or in another
  dimension. Check `max_distance` against how far apart the players actually are.
- Sweeps not climbing at all means the tick hook is not running, which usually means the mod
  failed to load. Check the server log for a mixin error at startup; `culltag.mixins.json`
  failing is fatal, so the server would not have started.
- Confirm the players are actually far enough apart to matter. Two players in the same room
  with a clear view of each other are supposed to see each other's nametags.

**A nametag is stuck hidden.**

- Check whether the player is crouching. With `crouch_hides_nametag` on, that is the setting
  working as intended.
- `/culltag disable` restores everything unconditionally, including overrides a client is
  holding that the server has lost track of. If a nametag comes back on disable and gets stuck
  again on enable, that is a bug worth reporting with the two players' positions.
- After a hot jar swap, run `/culltag disable` then `/culltag enable`. Clients can be holding
  overrides pushed by the previous jar that the new one knows nothing about.

**A player appears permanently crouched.**

That is the line-of-sight mechanism, not a pose bug: hiding a nametag through walls is done by
telling the viewer that the player is sneaking. It corrects the moment sight is restored. If a
player looks crouched in full open view with their nametag missing, and stays that way, that is
a stuck override; report it.

**Nametags flicker.**

- Check for [PassableFoliage](https://modrinth.com/mod/passable-foliage) or anything else that
  changes block collision shapes on the client only. That is the usual cause and the reason
  PassableFoliage is blocked outright.
- Players standing on the exact boundary of `max_distance` will flicker as they cross it.
  Nothing to fix, but it explains the symptom.

**Team colours disappear while somebody crouches.**

Working as designed, and unavoidable with the mechanism crouch hiding uses. See
[Compatibility](Compatibility). Turn `crouch_hides_nametag` off.

**Performance.**

`/culltag stats` reports average sweep time over the last 100 sweeps. If it is genuinely a
problem, raise `check_interval_ticks` before lowering `max_distance`: the interval divides the
whole cost, while the distance only trims the pairs at the edge. Note that sweep cost grows
with the square of how many players are close together, so a hundred players spread across a
world is cheap and a hundred players in a lobby is not.
