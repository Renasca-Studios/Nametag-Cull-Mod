CullTag is server-side only and touches one thing: outgoing packets about nametags. The places
it can conflict with something else are all downstream of that.

**[PassableFoliage](https://modrinth.com/mod/passable-foliage) is a hard incompatibility.** It
changes leaf collision shapes on both sides, and the two do not agree, so a ray that the server
thinks hits leaves passes straight through on the client and nametags flicker near any tree.
Fabric Loader refuses to start with both installed rather than letting that happen quietly.

**Blocks another mod adds** are handled by the `#culltag:transparent` tag. A modded glass or
window block will hide nametags until it is added to that tag, which any datapack can do
without a CullTag release. See [Configuration](Configuration).

**Mods that add named mobs** are covered automatically. Nothing about `cull_entity_nametags`
is vanilla-specific: it is every entity that reports a visible custom name.

**Hologram plugins and mods** are unaffected, as long as their armour stands are invisible,
which is nearly always how they are built. Invisible entities are never culled.

**Nickname and display-name mods** are fine. Nothing here reads a display name.

**Other mods that write entity metadata** are fine. CullTag rewrites the shared-flags byte on
its way out rather than changing it on the entity, so anything else reading or writing that
byte server-side sees the real value.

**Mods that add their own nametag rendering** on the client cannot be culled by this, because
CullTag works by giving a vanilla client the inputs that make vanilla's own rules hide the
tag. A client-side mod that draws its own labels is outside that.

**Scoreboard teams are not touched.** Version 1.1.x used a per-viewer team override for its
crouch-hiding feature, which cost that viewer the target's real team colour. Both the feature
and the mechanism are gone, and the leftover `culltag_hidden` team is deleted from the world
scoreboard the first time an upgraded server starts.
