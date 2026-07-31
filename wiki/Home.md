CullTag hides nametags that vanilla would otherwise draw straight through solid blocks, for
players and for named mobs alike. Everything happens on the server, so players connect with
unmodified clients and install nothing.

- [Configuration](Configuration) - every key in `config/culltag.properties`, and the block tag
  that decides what counts as see-through.
- [Commands](Commands) - the four `/culltag` subcommands and how to read what they report.
- [How It Works](How-It-Works) - the sight test, the hiding trick, and what each costs.
- [Compatibility](Compatibility) - other mods, modded blocks, and holograms.
- [Troubleshooting](Troubleshooting) - what to check when a nametag is stuck, missing, or
  showing when it should not be.

Installation is a single jar in the server's `mods` folder, alongside
[Fabric API](https://modrinth.com/mod/fabric-api). Nothing goes on the client.

These pages are kept in the mod repository under `wiki/` and copied here, so the version that
matches a given release is the one in that release's tag.
