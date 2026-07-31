# Changelog

## 1.1.2

- Added nametag culling for named mobs and armour stands. A named animal or a labelled armour stand gave a base away through terrain exactly like a player did. Invisible ones are left alone, so hologram armour stands stay readable. Turn it off with `cull_entity_nametags`.
- Added `#culltag:transparent`, a block tag listing everything you can see through. Change it with a datapack to cover blocks from other mods, or to make something hide nametags that currently does not.
- Added translation support. Every message the mod prints can be replaced by a language file, and English is built in, so nothing changes if you do not add one.
- Changed glass so it no longer hides anyone. Glass, stained glass, panes, iron bars, chains, ladders and scaffolding are all see-through, and hiding a nametag behind something you can see straight through was never the point.
- Changed what the sight check aims at, from a player's eyes to the nametag itself. A nametag peeking over a low wall now stays visible, and one tucked behind a ledge stays hidden even when the player's eyes are not.
- Changed `max_distance` to top out at 64. Vanilla stops drawing nametags at 64 blocks, so anything higher was doing nothing at all. Existing configs above 64 are lowered to it on load.
- Changed the minimum Fabric Loader version to 0.19.3.
- Removed `crouch_hides_nametag`. Crouching to vanish was a stealth mechanic rather than a fix for anything vanilla gets wrong, and it cost a crouching player their team colour in the eyes of anyone they were hidden from. The `culltag_hidden` team it left in your world is cleaned up for you on first start.
- Fixed nametags showing through walls until somebody moved. Logging in behind a wall, respawning behind a wall, walking into range from far off, or re-enabling the mod all left nametags fully visible until the two players moved in and out of each other's sight.
- Fixed nametags staying hidden at long range, in the open, after two players had walked apart.
- Fixed nametags staying hidden after a player died, changed dimension, or logged out and back in.

## 1.1.1

- Added separate line-of-sight and crouch counts to `/culltag stats`, and to the message `/culltag disable` prints.
- Added [PassableFoliage](https://modrinth.com/mod/passable-foliage) as a declared incompatibility. Nametags flickered near trees with it installed, so the game now refuses to start with both rather than letting it happen.
- Fixed nametags being hidden between players standing in clear sight of each other, and occasionally still visible after one of them walked behind cover. Opening a door or breaking a block never brought a nametag back.
- Fixed `crouch_hides_nametag` doing nothing to a crouching player in clear view, which is the only case the setting was for. While hidden this way a player showed no team colour to anyone they were hidden from.
- Fixed `/culltag disable` leaving some nametags hidden.

## 1.1.0

- Added `crouch_hides_nametag` (on by default): a crouching player's nametag is hidden from everyone, whether or not anything is in the way.
- Changed `/culltag reload` to report the new setting alongside the existing ones.

## 1.0.0

- Initial release. A nametag is hidden from anyone whose view of it is blocked, worked out entirely on the server, so players connect with unmodified clients and install nothing.
- Added `/culltag enable`, `/culltag disable`, `/culltag reload` and `/culltag stats`.
- Added `config/culltag.properties` with `enabled`, `max_distance` and `check_interval_ticks`.
