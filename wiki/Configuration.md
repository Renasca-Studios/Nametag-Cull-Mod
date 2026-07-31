`config/culltag.properties` is written on first start and rewritten every time it is read, so
a key you delete comes back with its default and an unparseable value is replaced with one.
Edits take effect on `/culltag reload` or a restart.

| Key | Default | Range | What it does |
|---|---|---|---|
| `enabled` | `true` | | Master switch. Turning it off restores every nametag immediately rather than just stopping the sweep. |
| `max_distance` | `32` | 1 to 64 | How far apart two things can be and still be checked. Beyond this they are left exactly as vanilla draws them. |
| `check_interval_ticks` | `10` | 1 to 40 | Server ticks between sweeps. 10 is about twice a second; 4 is about five times a second. |
| `cull_entity_nametags` | `true` | | Also cull named mobs and armour stands, not just players. |

Notes on each:

- **`max_distance`** is the main performance dial, because it decides how many things get a
  ray. It is capped at 64 because vanilla stops drawing nametags at 64 blocks, so checking
  further can never change what anybody sees.
- **`check_interval_ticks`** is a latency dial, not an accuracy one. At 10 a player stepping
  out of cover can keep a hidden nametag for up to half a second. Lower it if that half second
  matters in PvP; the cost scales linearly.
- **`cull_entity_nametags`** covers named mobs and named armour stands, which vanilla draws
  through walls exactly as it draws a player's. Invisible entities are always skipped, whatever
  this is set to, so armour stands used as holograms stay readable through terrain. Turn it off
  if you have a large number of named visible mobs in one place and want the sweep cheaper.

Which blocks hide a nametag is **not** in this file. It is the `#culltag:transparent` block
tag, which ships covering glass and stained glass, every glass pane, iron bars, chains,
ladders and scaffolding. Anything in that tag is seen straight through; anything else with a
collision shape blocks. Override it with a datapack the same way you would any vanilla tag:

```json
// data/yourpack/tags/block/... is not it; override CullTag's own tag instead:
// data/culltag/tags/block/transparent.json
{ "replace": false, "values": ["minecraft:oak_leaves"] }
```

`enabled` is also written back to the file by `/culltag enable` and `/culltag disable`, so a
toggle made in game survives a restart.
