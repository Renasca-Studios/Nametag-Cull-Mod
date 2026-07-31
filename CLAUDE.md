# CullTag

100% server-side Fabric mod for Minecraft 26.1.2. It hides player nametags that vanilla
would otherwise render through solid blocks, by raycasting between players on the server and
rewriting the clientbound packets. Players connect with unmodified clients, so every visible
string the mod produces is read by a client that has never heard of CullTag.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Audit

`python scripts/audit.py` before every commit that changes behaviour, and before every
release. Exit code 1 on any ERROR. See `.claude/skills/culltag-audit/SKILL.md` for what each
rule is protecting and why.

## Player-facing text

Always `Component.translatableWithFallback(key, english, args...)`, with the key present in
`src/main/resources/assets/culltag/lang/en_us.json` and the fallback string byte-identical to
the value there.

- Never `Component.literal` for prose. It cannot be translated.
- Never `Component.translatable` alone. This mod's whole premise is that players are on
  vanilla clients, so a bare key renders to the player as the literal text
  `culltag.something`.
- Proper nouns, numbers and player names stay as positional args, not translated strings.
- Singular and plural are separate keys, not a spliced-in `"s"`.

## No em dashes

Not anywhere: Java, comments, Markdown, the changelog, the wiki, config file comments, lang
strings. `scripts/audit.py` fails the build on one.

Use a comma for an aside, a colon before an explanation, a semicolon between two full
clauses, or parentheses for a parenthetical. Pick per sentence; a blind comma substitution
produces comma splices about a quarter of the time.

## Conventions

- Do not bump `mod_version` in `gradle.properties` without being asked. If the current
  version is unreleased, fold new work into it.
- Commit straight to master. The whole history is master-direct.
- `README.md` is the product description and doubles as the Modrinth page. No version
  history in it, and no second copy of it anywhere else.
- Releases are tagged `vX.Y.Z` as annotated tags. `git push` does not push tags.
