# Changelog

## Unreleased

- Added nametag culling for named mobs and armour stands, which vanilla draws through walls exactly as it draws a player's, so a named animal gave a base away just as well. Controlled by `cull_entity_nametags` (on by default). Invisible entities are always left alone, so armour stands used as holograms stay readable through terrain.
- Added the `#culltag:transparent` block tag, which decides what counts as see-through. Change it with a datapack instead of waiting for a release.
- Added a language file, so every message CullTag prints can be translated. English is built into each message and is what a vanilla client shows, so nothing changes if you have no language file.
- Changed nametags to stop being hidden behind glass. Glass, stained glass, every glass pane, iron bars, chains, ladders and scaffolding no longer hide anyone, because you can see straight through all of them and hiding a nametag behind them was the opposite of the point.
- Changed the sight test to aim at the nametag rather than at the eyes. A tag peeking over a low wall now stays visible, and a tag hidden behind a ledge is hidden even when the player's eyes clear it.
- Changed `max_distance` to cap at 64 rather than 512. Vanilla stops drawing nametags at 64 blocks, so anything beyond that was work that could never change what a player sees. Existing configs above 64 are clamped.
- Changed the sweep to skip spectators and anything invisible before casting, since neither can have a nametag drawn.
- Removed `crouch_hides_nametag`. It was a stealth mechanic rather than a fix for anything vanilla got wrong, and the only way to implement it cost the viewer the crouching player's real team colour. The `culltag_hidden` scoreboard team it left behind in your world is deleted automatically on first start.
- Fixed nametags showing through walls until somebody moved. A pair that was already behind cover the first time CullTag saw them kept full vanilla nametags until their line of sight changed at least once, which meant logging in behind a wall, respawning behind a wall, walking into range from far away, or re-enabling the mod all left the wallhack intact.
- Fixed nametags staying hidden at long range. Once a player walked past `max_distance` the override was never taken back off, so they stayed nameless in plain open sight until they came back into range with a clear view.
- Fixed nametag overrides being left behind on players who died, changed dimension, or logged out and back in.
- Fixed the jar claiming the MIT licence without carrying a copy of it.

## 1.1.1

- Added separate line-of-sight and crouch counts to `/culltag stats`, and to the message `/culltag disable` prints.
- Added [PassableFoliage](https://modrinth.com/mod/passable-foliage) as a declared incompatibility. It changes leaf collision on the client but not the server, which made nametags flicker near trees, so the loader now refuses to start with both mods installed rather than letting it happen.
- Fixed nametags being hidden between players standing in clear sight of each other, and occasionally still visible after one of them walked behind cover. Sight was only rechecked when a player moved, so a door opening or a block breaking never brought a nametag back.
- Fixed `crouch_hides_nametag` having no visible effect. Crouching players were still fully named to anyone with a clear view of them, which is exactly the case the setting is for. Note that while a player is hidden this way they appear to have no scoreboard team, so team colours are lost in that one viewer's eyes; turn the setting off if your server colours players by team.
- Fixed `/culltag disable` leaving some nametags hidden, including after a hot jar swap.

## 1.1.0

- Added `crouch_hides_nametag` (on by default): a crouching player's nametag is hidden from everyone, whether or not anything is in the way.
- Added a README.
- Changed `/culltag reload` to report the new setting alongside the existing ones.

## 1.0.0

- Initial release. A player's nametag is hidden from anyone whose view of them is blocked by blocks, worked out entirely on the server, so players connect with unmodified clients and install nothing.
- Added `/culltag enable`, `/culltag disable`, `/culltag reload` and `/culltag stats`.
- Added `config/culltag.properties` with `enabled`, `max_distance` and `check_interval_ticks`.
