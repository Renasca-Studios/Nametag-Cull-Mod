CullTag is server-side only and touches one thing: outgoing packets about player nametags. The
places it can conflict with something else are all downstream of that.

**[PassableFoliage](https://modrinth.com/mod/passable-foliage) is a hard incompatibility.** It
changes leaf collision shapes on both sides, and the two do not agree, so a ray that the server
thinks hits leaves passes straight through on the client and nametags flicker near any tree.
Fabric Loader refuses to start with both installed rather than letting that happen quietly.

**Scoreboard teams and `crouch_hides_nametag`.** While a crouching player is hidden from
someone, that one viewer's client is told the player is on the `culltag_hidden` team. If the
player was already on a real team, that viewer loses its colour and prefix for as long as the
crouch lasts. Everyone else, and the server's own scoreboard, are unaffected. If your server
colours players by team, or uses team colour to signal friendly fire, turn
`crouch_hides_nametag` off. Line-of-sight culling does not use teams and is unaffected either
way.

The mod also creates the `culltag_hidden` team on the world scoreboard at startup, so it shows
up in `/team list` and persists in the save. Deleting it by hand while the server is running
will break crouch hiding until the next restart.

**Glass, panes, bars and fences block sight.** The raycast uses collision shapes, and all of
those have one, so a player standing behind a glass wall in full view loses their nametag. This
is a deliberate simplification rather than a bug, but it is the behaviour most likely to be
reported as one.

**Nickname and display-name mods** are fine. Team membership is keyed on the scoreboard name,
which is the account name, so a changed display name does not break crouch hiding.

**Other mods that write entity metadata** are fine. CullTag rewrites the shared-flags byte on
its way out rather than changing it on the entity, so anything else reading or writing that
byte server-side sees the real value.

**Mods that add their own nametag rendering** on the client cannot be culled by this, because
CullTag works by giving a vanilla client the inputs that make vanilla's own rules hide the tag.
A client-side mod that draws its own labels is outside that.

**Named mobs and armour stands are not culled.** Only players are. A named mob still shows its
tag through walls exactly as vanilla does.
