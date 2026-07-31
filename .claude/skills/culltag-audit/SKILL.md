---
name: culltag-audit
description: Run the CullTag codebase audit and interpret its findings. Use before any release, before committing a batch of behaviour changes, after touching the LOS sweep or either override mechanism, after adding a config key, after adding or changing player-facing text, and after any Minecraft, Fabric Loader or Fabric API bump. Also use when asked to check code quality, look for dead code, or find nametags that get stuck hidden or stuck visible.
---

# CullTag audit

```bash
python scripts/audit.py            # full report
python scripts/audit.py --quiet    # errors only, for a pre-commit gate

./gradlew runServer &              # then, for behaviour rather than tidiness:
python scripts/functest.py         # drives Carpet fake players over rcon
```

Exit code 1 on any ERROR. WARNINGs never fail the run; they are judgement calls, and two of
the warning classes are deliberately *lists* rather than complaints.

**Run it before every release, and after any change to the sweep, either override mechanism,
the config, the displayed text, or a dependency version.** It is not a general linter; every
rule exists because that exact mistake shipped, or because it is the next instance of a class
that shipped three times in one release.

## The bug class this codebase actually has

**A per-viewer override that goes on and never comes off, or never goes on at all.** CullTag
does its whole job by lying to one client at a time: it tells viewer V that target T is
sneaking. That lie is state living in two places at once, on the server and inside V's
client, and every shipped bug has been the two disagreeing.

- 1.1.1: a "skip the raycast if this pair was blocked and neither endpoint moved" cache was
  unsound. A door opening or a block breaking restores sight without either endpoint moving,
  so pairs got stuck as blocked forever.
- 1.1.1: `crouch_hides_nametag` did nothing. It reused the force-sneak mechanism, which
  cannot hide a nametag at close range with clear sight, which is the only case the setting
  is for. It parsed, it saved, it was reported by `/culltag reload`, and it had no effect.
- 1.1.1: `/culltag disable` did not fully restore, because it only cleared the pairs the
  server was tracking and the client could be holding more.
- Fixed after 1.1.1: an override was set on a pair and then the pair went out of
  `max_distance`, at which point the code stopped tracking them without taking the override
  off. They stayed nameless at range.
- Fixed after 1.1.1: a pair that was *already* blocked the first time the engine saw them was
  never hidden, because the change detector defaulted "what did we last tell this client" to
  "whatever we are about to say", so the first observation was never a change.

A fourth category, which the `crouch_hides_nametag` removal is the end of: **a feature that
answers the wrong question.** The collision raycast asked "would I walk into this", so glass
hid nametags. The eye-to-eye ray asked about the body rather than the tag that floats above
it. Both compiled, both ran, both were wrong in a way no amount of state discipline would
have caught.

Three rules follow, and they are what most of the ERROR checks protect:

- **One record of what the client was told, and it lives on the connection.** The hidden set
  on `NametagController` *is* the state; nothing mirrors it in a map keyed by UUID.
  `LineOfSightEngine.apply` decides whether to send by asking whether the set changed, so
  "recorded" and "sent" cannot come apart. This also makes reconnects correct for free: a new
  connection has empty sets, which is exactly the state a fresh client is in.
- **Every path that stops tracking a pair must first put the pair back.** Out of range, into
  another dimension, disconnected, respawned. `release()` exists so that "stop caring about
  this pair" is spelled the same way everywhere, and the disconnect and respawn hooks in
  `CullTagMod` exist because an override cannot be allowed to outlive the thing it points at.
- **The absent state is "visible", not "unknown".** A viewer who has been told nothing is in
  the vanilla state, so the first observation of a blocked pair has to be a change.

## ERRORS: fix before shipping

| Rule | Why it exists |
|---|---|
| Unused imports | Left behind by edits that deleted the last usage. `NametagManager` carried a dead `ArrayList` import from 1.0.0 through 1.1.1. Javadoc `{@link}` counts as a usage. |
| Unused private constants | Dead tuning knobs read as live config and mislead the next reader. |
| Mixin member without `@Unique` | See below. This one shipped. |
| Mixin not listed in `culltag.mixins.json` | A mixin class that is not listed simply never applies: silently, with no load error. The feature it implements just does not exist. All three buckets (`mixins`, `client`, `server`) are unioned, because CullTag lists under `server`. |
| `injectors.defaultRequire` not 1 | With it at 1 a missing target is fatal at load. Drop it to 0 and a Minecraft rename turns the whole mod into a no-op that boots fine. |
| Literal synched-data id | See below. |
| Disconnect or respawn hook missing | Deleting one line in `CullTagMod` reintroduces the whole stuck-override class. |
| `forgetPlayer` / `forgetEntity` gone | Same, from the other end. |
| Datapack tag file missing | A `TagKey` whose JSON is absent resolves to an **empty tag**, silently. Nothing throws, nothing logs, and whatever the tag drives stops applying to anything. Losing `#culltag:transparent` would put every pane of glass back to hiding nametags with no clue why. |
| Raw collision raycast outside `SightTest` | `ClipContext.Block.COLLIDER` asks "would I walk into this", and glass, panes and iron bars all answer yes. That is what used to hide the nametag of a player standing behind a glass wall in plain view. |
| `net.minecraft.client` reference | The product claim is that players install nothing, so the jar is `environment: server`. A client class compiles fine against the merged dev classpath and throws `NoClassDefFoundError` on a real server. |
| `environment` not `server` | The same claim, stated in the metadata. |
| Config key/arg misalignment | `toPropertiesString()` interpolates positionally. A mismatch throws `MissingFormatArgumentException` **at runtime**, so the config silently stops saving. |
| Config field never read / not in the template | A knob with a default and no reader looks configurable and is not. |
| Config field never read outside `CullTagConfig` | This is the static shadow of the `crouch_hides_nametag` bug: fully wired into the config machinery, connected to nothing. That setting is gone now, but the shape it failed in is not. |
| Access widener entry never referenced | A widener prises open somebody else's class. One left behind after the code stopped using it is dead surface, and it is the first thing to check when `validateAccessWidener` starts failing on a Minecraft bump. |
| Hardcoded display text | `Component.literal("some English")` cannot be translated. |
| `Component.translatable` without a fallback | Shows a vanilla client the raw key. See below. |
| Lang key missing from `en_us.json` | The fallback hides it in English, so it only surfaces as untranslatable text in someone else's language. |
| Fallback not matching `en_us.json` | Two copies of the same sentence drift, and the mod then says different things to a vanilla client and a translated one. |
| `fabric.mod.json` license with no `LICENSE` file | `build.gradle` does `from('LICENSE')`, which bundles nothing at all when the file is absent, so the jar shipped claiming MIT with no licence text in it. |
| Em dash | House style, below. |

## `@Unique` is not optional, and this is not theoretical

1.1.1 shipped `private static rewriteFlags(...)` in the mixin with no `@Unique`. It really did
land in `ServerCommonPacketListenerImpl` under that bare name. Confirmed rather than assumed:

```bash
JAVA_TOOL_OPTIONS="-Dmixin.env.audit=true -Dmixin.debug.export=true" ./gradlew runServer
javap -p -classpath run/.mixin.out/class net.minecraft.server.network.ServerCommonPacketListenerImpl
```

`mixin.env.audit` is the important half. Boot success on its own proves nothing here, because
the target class is not loaded until a client connects, and a broken injector only throws when
the class is transformed. The audit flag forces every remaining mixin to apply at the end of
startup, so a headless boot really does exercise it. The export flag then writes the
transformed class out, and `javap` shows whether the interface, the `@Unique` fields and the
`handler$...$culltag$...` injector actually merged.

## The literal `0` in the packet

`Entity.DATA_SHARED_FLAGS_ID` is index 0 of the synched-data table and has been for years, but
writing `0` into the packet and comparing `v.id() == 0` in the filter is a silent-breakage
hazard, not a shortcut. A vanilla field defined ahead of it moves it with no compile error and
no crash; the mod just starts rewriting some unrelated value as a byte on every metadata packet
it touches. `culltag.accesswidener` exists precisely so `Entity.DATA_SHARED_FLAGS_ID.id()` can
be read, so read it.

To re-verify on a Minecraft bump, read the order of `defineId` calls out of the real class
rather than from memory:

```bash
javap -p -c -classpath <named-mc-jar> net.minecraft.world.entity.Entity \
  | grep -E "defineId|putstatic .*DATA_"
```

## Displayed text

**Always `Component.translatableWithFallback(key, english, args...)`.** Never
`Component.literal` for anything a player reads, and never `Component.translatable` alone.

The fallback is not optional politeness. The README's headline claim is *"your players don't
need to install anything"*, so the player is normally on a vanilla client that has never heard
of this mod. `Component.translatable` alone would show them `culltag.command.enable`. Sending
both means a vanilla client renders the English and a client carrying a CullTag language file
renders the translation, with no branching on either side.

Three rules follow:

- **The join is a key too.** "CullTag | Nametag culling enabled." is two pieces of text plus a
  piece of layout, so `culltag.chat.line` takes both as positional args. `CullTagText` holds
  every shape; nothing outside it builds player text, and the audit warns when something does.
- **Singular and plural are separate keys**, not a spliced-in `"s"`. The current messages are
  worded to avoid needing either, which is cheaper than two keys.
- **Proper nouns and numbers stay literal.** "CullTag" is a name, and a sweep time is a number.

The one deliberate exemption is `CullTagConfig.summary()`, the `enabled=true max_distance=32`
operator diagnostics whose labels are the config file's own key names. It is passed into
`CullTagText.reloaded` as a single opaque arg rather than broken into keys, so the
`Component.literal` check never sees prose in it.

## No em dashes

Not anywhere: Java, comments, Markdown, the changelog, the wiki, the config file's own
comments, lang strings. A comma for an aside, a colon before an explanation, a semicolon
between two full clauses, parentheses for a parenthetical.

Pick per sentence. A blind substitution to commas produces comma splices about a quarter of
the time, which is worse than the dash was.

## WARNINGS: each needs a deliberate answer, not a reflex

### Mixin targets to re-verify (the porting checklist)

Not a complaint, this is the list you work through on every Minecraft update. Each
`method = "..."` is a string that has to match a real method, and `culltag.mixins.json` sets
`defaultRequire: 1`, so a rename is **fatal at load** rather than a silent no-op. That is the
behaviour we want (better a crash than nametag culling quietly not happening), but it means
the mod hard-fails on a Minecraft bump until this is checked:

- `ServerCommonPacketListenerImpl#send(Packet, ChannelFutureListener)`, the intercept point.

The two-argument overload is the one to target. The single-argument `send(Packet)` delegates to
it, so injecting into the pair catches everything; injecting into `send(Packet)` alone would
miss any caller that passes a listener.

### Per-viewer state to confirm is released on disconnect

Also a list. Each entry is a set on `NametagController`, and each one is something a client
believes because CullTag told it to. Adding a second is fine. What is not fine is adding one
that no path takes back off, so they are printed on every run to be answered rather than
assumed.

There is one today, **hidden entity IDs**. It needs no restore packet when a player leaves,
because the client is about to remove that entity anyway; what it needs is to stop being
tracked, so nothing rewrites packets for an ID the server reuses later. It also needs clearing
on respawn, because a respawned player keeps their connection and becomes a **new entity with
a new ID**, and the old one would otherwise sit in every viewer's set for the rest of the
session.

A second set existed until the `crouch_hides_nametag` removal, holding scoreboard names rather
than IDs, and the asymmetry is worth remembering if anything ever wants per-viewer state keyed
on something other than an entity: **a name-keyed override always needs the explicit removal
packet**, because a client keeps its team roster across a player leaving, so skipping it made
that player come back already hidden. Entity IDs are self-cleaning in a way names are not.

### Config key not mentioned in README.md

The README doubles as the Modrinth page, so it is where an operator looks for the key names
before they ever open the config file.

### Possibly unused private method

Heuristic: it counts `name(` calls and `::name` references, and skips anything carrying an
injector annotation, since Mixin calls those and we never do. Confirm before deleting.

### Inline fully-qualified names

`net.minecraft.server.level.ServerLevel` written inline instead of imported. Cosmetic.

### Unused lang key

A warning, not an error: a key can be legitimately staged ahead of the code that uses it.

## What the audit cannot catch

It is static analysis on a mod whose entire job is to be exactly right about what one client
has been told. It says nothing about whether the culling is *correct*. For that:

- `gradlew runServer` with `-Dmixin.env.audit=true` proves the mixin applies. It does not prove
  the mixin does the right thing, because no client connected.
- **`scripts/functest.py` is the answer to most of that.** Carpet fake players are real
  `ServerPlayer` instances with real connections, so the whole packet path runs, and `/culltag
  stats` over rcon is a readable assertion target. It covers walls, glass, bars, named mobs,
  moving out of range, an entity dying while hidden, and the disable/enable cycle. Extend it
  rather than testing by hand.
- What it still cannot see is what the *client draws*. Everything is asserted through the
  server's own hidden count, so a change that records the right state and sends the wrong
  packet would pass. Two real clients are the only check for that, and worth doing before a
  release.
- Reconnect is the one gap in `functest.py`, because Carpet fake players do not reconnect the
  way a real client does. Log a hidden player out and back in by hand.
- The `#culltag:transparent` tag is only checked for *existence*. Whether the blocks in it are
  the right blocks is a judgement call, and whether a modded glass is missing from it is
  something only a server running that mod will notice.
- Aiming at the nametag rather than the eyes is the kind of thing that is either right for
  every entity or wrong for a whole class of them. Worth eyeballing on a mob with an unusual
  hitbox, since the anchor comes from that entity's own declared attachment.
- `/culltag stats` is the cheap health check. `rays` climbing while `nametags hidden` stays at
  zero on a server where players are demonstrably behind walls means the sweep is running and
  the packets are not landing.
- For another mod's API or anything reflective, resolve it against that mod's real jar rather
  than reasoning about it. The same goes for Minecraft itself: every claim in this document
  about vanilla behaviour was read out of the named 26.1.2 jar with `javap`, not remembered.

A clean audit means the code is tidy, not that no nametag is stuck.
