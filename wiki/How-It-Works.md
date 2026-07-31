Every `check_interval_ticks`, the server walks each unordered pair of online players and casts
one ray between their eye positions. Pairs in different dimensions, and pairs further apart
than `max_distance`, are skipped and any override they were carrying is removed. One ray
serves both directions, which halves the work.

Hiding is done in one of two ways, because neither one covers both cases.

**Line of sight blocked: force the sneaking flag.** Vanilla clients never draw a sneaking
player's nametag through blocks, so the server sends the viewer a metadata packet with the
sneaking bit set on the target, and the client applies its own existing rule. While the
override is on, every later metadata packet for that entity is rewritten on its way out so a
routine sync cannot clear it. When sight comes back, the target's real flags are sent and the
pose corrects immediately.

**Crouching: a per-viewer scoreboard team.** The sneak trick cannot help here, because vanilla
still draws a sneaking player's nametag at close range with a clear view, which is exactly the
case `crouch_hides_nametag` is for. Instead CullTag creates a real team called
`culltag_hidden` with `nametagVisibility=NEVER` and never puts anyone on it server-side. Team
membership packets are clientbound, so the server can tell one client that a player is on that
team while telling everyone else nothing. See [Compatibility](Compatibility) for what this
costs.

Two things worth knowing about the cost:

- **There is no caching between sweeps.** Every in-range pair is recast every time. An earlier
  version skipped the ray when a pair was already blocked and neither player had moved, which
  was wrong: a door opening or a block breaking restores sight without either player moving,
  and those pairs stayed hidden forever. Recasting is measurably cheap enough that the trade
  is not close.
- **Packets are only sent on a change.** The sweep is constant work; the network traffic is
  proportional to how often players actually walk in and out of cover.

The ray uses block collision shapes and ignores fluids, so water and lava do not block a
nametag but glass does. It also runs eye to eye rather than eye to nametag, so a player whose
tag pokes above a one-block wall is still treated as hidden.
