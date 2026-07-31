# Changelog

## Unreleased

- Added a language file, so every message CullTag prints can be translated. English is built into each message and is what a vanilla client shows, so nothing changes if you have no language file.
- Changed to Fabric Loader 0.19.3 and Fabric API 0.155.2, still on Minecraft 26.1.2.
- Fixed nametags showing through walls until somebody moved. A pair that was already behind cover the first time CullTag saw them kept full vanilla nametags until their line of sight changed at least once, which meant logging in behind a wall, respawning behind a wall, walking into range from far away, or re-enabling the mod all left the wallhack intact.
- Fixed players staying crouched and nameless at long range. Once a hidden player walked past `max_distance` the override was never taken back off, so they stayed hunched over with no nametag in plain open sight until they came back into range with a clear view.
- Fixed crouch hiding quietly doing nothing after a player reconnected. The server still believed that player's client had been told about the hidden-nametag team, so it stopped sending them the packets that do the hiding.
- Fixed a player who logged out while crouch-hidden coming back with their nametag still hidden.
- Fixed nametag overrides being left behind on players who died or changed dimension.
- Fixed `/culltag stats` counting crouch-hidden nametags for players who had already left.
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
