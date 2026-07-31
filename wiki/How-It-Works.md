Every `check_interval_ticks`, the server walks each pair of online players, plus each named
mob or armour stand near each player if `cull_entity_nametags` is on, and casts a ray for
each. Pairs in different dimensions, and pairs further apart than `max_distance`, are skipped
and any override they were carrying is removed.

**The ray ends at the nametag, not at the eyes.** The tag floats above the head, so an eye to
eye test answers the wrong question in both directions: a player crouched behind a one-block
wall whose tag is plainly visible would be hidden, and one whose eyes cleared a ledge but
whose tag did not would be shown. The end point is read from the entity's own `NAME_TAG`
attachment, which is the same point the client renders against. That makes the test
asymmetric, so a player pair costs two rays rather than one.

**Glass is not a wall.** The occlusion test is not the game's usual collision raycast, which
asks "would I walk into this" and answers yes for glass, panes and iron bars. A block hides a
nametag if it has a collision shape and is **not** in the `#culltag:transparent` block tag.
See [Configuration](Configuration) for what is in that tag and how to change it.

**Hiding is one trick, used for everything.** A client never draws a sneaking entity's nametag
through blocks, so the server sends the viewer a metadata packet with the sneaking bit set on
the target and the client applies its own existing rule. While the override is on, every later
metadata packet for that entity is rewritten on its way out so a routine sync cannot clear it.
When sight comes back, the target's real flags are sent. The bit is read by the renderer for
every entity type, not just players, which is why named mobs and armour stands work with no
second mechanism. It is also not the crouch pose, which comes from a separate field, so a
hidden player is not made to look crouched.

Three things worth knowing about the cost:

- **There is no cache between sweeps.** Everything in range is recast every time. An earlier
  version skipped the ray when a pair was already blocked and neither player had moved, which
  was wrong: a door opening or a block breaking restores sight without either player moving,
  and those pairs stayed hidden forever. Recasting is cheap enough that the trade is not close.
- **Packets are only sent on a change.** The sweep is constant work; the network traffic is
  proportional to how often things actually move in and out of cover.
- **Spectators, invisible entities and anything past 64 blocks are skipped before the ray.**
  None of them can have a nametag drawn, so a ray would decide nothing.

Fluids are ignored, so water and lava never hide a nametag.
