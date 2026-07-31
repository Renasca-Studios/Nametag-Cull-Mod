<h1 align="center">CullTag</h1>
<h3 align="center">True Server-Side Nametag Culling for Fair Play</h3>

Vanilla Minecraft gives every player a free, built-in wallhack: nametags visible through solid blocks. **CullTag** fixes this unfair advantage with highly optimized, server-side raycasting.

If a player is hiding behind a wall, their nametag is gone. It’s that simple.

Because all calculations and packet interceptions happen entirely on the server, **your players don't need to install anything.** They can connect with a 100% vanilla client and immediately experience fairer gameplay, making it perfect for SMPs, PvP arenas, and hardcore survival servers.

## Key Features

* **Zero Client Setup:** 100% server-side. Vanilla and modded clients both see the correct, culled nametags automatically.
* **Aimed at the nametag, not the eyes:** the ray ends where the tag actually floats, so a tag peeking over a wall stays visible and one hidden behind it does not.
* **Glass is not a wall:** panes, bars, chains and ladders never hide a nametag. Which blocks count is a datapack tag, `#culltag:transparent`, so you can change it without waiting for a release.
* **Named mobs too:** a named mob or armour stand is the same wallhack as a player. `cull_entity_nametags` covers them, and leaves invisible ones alone so hologram stands stay readable.
* **Engineered for Performance:** sweeps run every few ticks, skip spectators and anything invisible, and never look past the 64 blocks at which vanilla stops drawing nametags. Typical cost is well under a millisecond.
* **Live Configuration:** `config/culltag.properties` holds `enabled`, `max_distance`, `check_interval_ticks` and `cull_entity_nametags`.
* **Hot-Swappable:** Toggle the entire system or reload config changes live without ever needing to restart your server.
* **Built-in Profiling:** Monitor exactly how the mod is operating under the hood with built-in performance metrics.

## Commands

Manage the mod entirely in-game (requires appropriate permissions):
* `/culltag enable` & `/culltag disable` - Toggle the culling system live. Your choice automatically persists to the config file.
* `/culltag reload` - Hot-reload any changes made to `culltag.properties`.
* `/culltag stats` - View real-time raycast performance metrics and active tracking data.

## Documentation

* **[Configuration](https://github.com/Overshock-Studios/Nametag-Cull-Mod/wiki/Configuration)** - every key with its default and range, and the block tag that decides what counts as see-through.
* **[Commands](https://github.com/Overshock-Studios/Nametag-Cull-Mod/wiki/Commands)** - what each subcommand does, and how to read `/culltag stats`.
* **[How It Works](https://github.com/Overshock-Studios/Nametag-Cull-Mod/wiki/How-It-Works)** - the sight test, the hiding trick, and what each costs.
* **[Compatibility](https://github.com/Overshock-Studios/Nametag-Cull-Mod/wiki/Compatibility)** - other mods, modded blocks, and holograms.
* **[Troubleshooting](https://github.com/Overshock-Studios/Nametag-Cull-Mod/wiki/Troubleshooting)** - what to check when a nametag is stuck, missing, or showing when it should not be.

## Requirements

* **Environment:** Server-side ONLY (Drop it in your server's `mods` folder and you're done)
* **Dependencies:** [Fabric API](https://modrinth.com/mod/fabric-api)
* **Fabric Loader:** 0.19.3 or newer

## Known Incompatibilities

* **[PassableFoliage](https://modrinth.com/mod/passable-foliage)** - mutates leaf collision shapes on both client and server, desynchronising LOS raycasts through foliage and producing erratic nametag visibility.
