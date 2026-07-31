#!/usr/bin/env python3
"""CullTag codebase audit.

Mechanical checks for the specific mistakes this codebase actually makes, rather than a
generic linter. Every rule here exists because that exact thing shipped at least once, or
because it is the next instance of a class that shipped three times in 1.1.1.

    python scripts/audit.py            # report
    python scripts/audit.py --quiet    # errors only

Exit code 1 if any ERROR is found. WARNINGs are judgement calls and never fail the run; two
of the warning classes are deliberately lists rather than complaints.
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "src" / "main" / "java"
PKG = MAIN / "com" / "culltag"
MIXINS = PKG / "mixin"
CONFIG = PKG / "CullTagConfig.java"
CONTROLLER = PKG / "NametagController.java"
ENGINE = PKG / "LineOfSightEngine.java"
ENTRYPOINT = PKG / "CullTagMod.java"
RESOURCES = ROOT / "src" / "main" / "resources"
MIXIN_JSON = RESOURCES / "culltag.mixins.json"
FABRIC_JSON = RESOURCES / "fabric.mod.json"
ACCESS_WIDENER = RESOURCES / "culltag.accesswidener"
LANG = RESOURCES / "assets" / "culltag" / "lang" / "en_us.json"

# Built with chr() on purpose: this file is scanned by its own check, so spelling the
# character out here would make the audit fail on itself.
EM_DASH = chr(0x2014)

# A dotted name ending in one of these is a filename, not a translation key.
NOT_KEY_SUFFIXES = {"properties", "json", "txt", "log", "jar", "accesswidener", "toml", "mixins"}

_INJECTOR = re.compile(
    r"@(Inject|ModifyArg|ModifyArgs|ModifyVariable|ModifyReturnValue|Redirect"
    r"|WrapOperation|WrapWithCondition|Overwrite|Accessor|Invoker|Shadow)"
)

errors: list[str] = []
warnings: list[str] = []


def strip_comments_and_strings(src: str) -> str:
    """Crude but adequate: removes block/line comments and string literals."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    return src


def java_files() -> list[pathlib.Path]:
    return sorted(MAIN.rglob("*.java"))


def method_body(src: str, signature_fragment: str) -> str | None:
    """Returns the brace-matched body of the first method whose declaration contains
    signature_fragment, or None."""
    at = src.find(signature_fragment)
    if at == -1:
        return None
    open_brace = src.find("{", at)
    if open_brace == -1:
        return None
    depth, i = 0, open_brace
    while i < len(src):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[open_brace:i + 1]
        i += 1
    return None


# ---------------------------------------------------------------- unused imports
def check_unused_imports() -> None:
    """An import left behind by an edit that deleted the last usage.

    NametagManager carried an unused java.util.ArrayList from 1.0.0 through 1.1.1.
    """
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        body = strip_comments_and_strings(re.sub(r"^import .*$", "", src, flags=re.M))
        # Javadoc {@link Foo} counts as a usage; the comment stripper has removed it already.
        javadoc_refs = set(re.findall(r"\{@(?:link|linkplain|code|value)\s+([\w.#]+)", src))
        for m in re.finditer(r"^import (?:static )?[\w.]+\.(\w+);$", src, re.M):
            name = m.group(1)
            if name == "*":
                continue
            if re.search(r"\b" + re.escape(name) + r"\b", body):
                continue
            if any(ref.split(".")[0] == name or ref.startswith(name + "#") for ref in javadoc_refs):
                continue
            errors.append(f"unused import: {f.relative_to(ROOT)} -> {name}")


# ------------------------------------------------------------ unused private members
def check_unused_private_members() -> None:
    """Dead private helpers and constants read as live tuning knobs and mislead the reader."""
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        body = strip_comments_and_strings(src)

        for m in re.finditer(r"private (?:static )?(?:final )?[\w<>\[\],. ?]+ (\w+)\s*\(", body):
            name = m.group(1)
            # An injector handler is called by Mixin, not by us; the annotation is on the
            # unstripped source, so look there rather than in the comment-stripped body.
            if _INJECTOR.search(src[max(0, src.find(name) - 400):src.find(name)].rsplit("}", 1)[-1]):
                continue
            calls = len(re.findall(r"\b" + re.escape(name) + r"\s*\(", body))
            # A method reference is a usage too. Counting only "name(" call sites flags every
            # event callback in the codebase as dead.
            refs = len(re.findall(r"::\s*" + re.escape(name) + r"\b", body))
            if calls + refs <= 1:
                warnings.append(f"possibly unused private method: {f.relative_to(ROOT)} -> {name}()")

        for m in re.finditer(r"private static final [\w<>\[\],. ]+ ([A-Z][A-Z0-9_]*)\s*=", body):
            name = m.group(1)
            if len(re.findall(r"\b" + re.escape(name) + r"\b", body)) <= 1:
                errors.append(f"unused private constant: {f.relative_to(ROOT)} -> {name}")


# ------------------------------------------------------------------- mixin hygiene
def check_mixin_unique() -> None:
    """A culltag_ prefix is convention; @Unique is what the compiler enforces.

    1.1.1 shipped a private static rewriteFlags() with no @Unique, and it really did land in
    ServerCommonPacketListenerImpl under that bare name, where any other mod picking the same
    name collides with it. Verified by exporting the transformed class with
    -Dmixin.debug.export=true and reading it back with javap. Only non-injector members need
    the annotation; mixin already owns @Inject and friends.
    """
    member = re.compile(
        r"(?:@[\w()\"$., =\-]+\s+)*private (?:static )?(?:final )?[\w<>\[\],. ?]+ (\w+)\s*[(;=]"
    )
    for f in sorted(MIXINS.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        for m in member.finditer(src):
            name = m.group(1)
            preceding = src[max(0, m.start() - 400):m.start()]
            # The match itself starts at the first annotation, so the annotations sit inside
            # group(0) rather than before it. Both have to be looked at.
            block = preceding.rsplit("}", 1)[-1] + m.group(0)
            if _INJECTOR.search(block) or "@Unique" in block:
                continue
            errors.append(
                f"mixin member without @Unique: {f.relative_to(ROOT)} -> {name} "
                f"(the prefix alone is not enforced; it merges under this bare name)"
            )


def check_mixins_registered() -> None:
    """A mixin class not listed in culltag.mixins.json never applies.

    Silently, with no load error: the feature it implements simply does not exist. CullTag
    lists its mixins under "server" because the mod is environment: server, so all three
    buckets are unioned rather than just "mixins".
    """
    if not MIXIN_JSON.exists():
        errors.append("mixins: culltag.mixins.json is missing")
        return
    try:
        cfg = json.loads(MIXIN_JSON.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        errors.append(f"mixins: culltag.mixins.json does not parse ({exc})")
        return

    listed = set()
    for bucket in ("mixins", "client", "server"):
        listed.update(cfg.get(bucket, []))

    on_disk = {f.stem for f in MIXINS.rglob("*.java")}
    for name in sorted(on_disk - listed):
        errors.append(f"mixins: {name} exists but is not listed in culltag.mixins.json")
    for name in sorted(listed - on_disk):
        errors.append(f"mixins: culltag.mixins.json lists {name}, which has no source file")

    if cfg.get("injectors", {}).get("defaultRequire") != 1:
        errors.append(
            "mixins: injectors.defaultRequire must stay 1 so a missing target is fatal at "
            "load rather than a mod that quietly does nothing"
        )


def check_mixin_target_names() -> None:
    """Every @Inject method= name is a string that has to match a real Minecraft method.

    culltag.mixins.json sets defaultRequire: 1, so a rename between Minecraft versions is
    fatal at load rather than a silent no-op. That is the behaviour we want, but it means this
    list is the porting checklist for every Minecraft update. Reported as warnings so the list
    gets printed, not so it fails.
    """
    for f in sorted(MIXINS.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        target = re.search(r"@Mixin\((?:value\s*=\s*)?(\w+)\.class", src)
        owner = target.group(1) if target else "?"
        for m in re.finditer(r'method\s*=\s*"([\w$]+)', src):
            warnings.append(
                f"mixin target to re-verify on a Minecraft update: "
                f"{f.relative_to(ROOT)} -> {owner}#{m.group(1)}"
            )


def check_mixin_hardcoded_indexes() -> None:
    """@ModifyArg index= is a hand-written descriptor assumption, fatal at load if wrong."""
    for f in sorted(MIXINS.rglob("*.java")):
        src = f.read_text(encoding="utf-8")
        for m in re.finditer(r"index\s*=\s*(\d+)", src):
            warnings.append(
                f"hardcoded mixin arg index: {f.relative_to(ROOT)} -> index={m.group(1)} "
                f"(re-verify against the target descriptor on every Minecraft update)"
            )


# ------------------------------------------------------- synched-data slot numbers
def check_no_literal_synched_data_ids() -> None:
    """A bare integer standing in for a vanilla synched-data slot breaks silently.

    The shared-flags accessor is index 0 today, and it was written as a literal 0 in both the
    packet builder and the mixin's filter through 1.1.1. A vanilla field defined ahead of it
    would move it with no compile error and no crash: the mod would just start rewriting some
    unrelated value as a byte on every metadata packet. The access widener exists so
    Entity.DATA_SHARED_FLAGS_ID.id() can be read instead.
    """
    patterns = (
        (r"new SynchedEntityData\.DataValue<>\(\s*(\d+)", "packet built with a literal slot"),
        (r"\.id\(\)\s*==\s*(\d+)", "slot compared against a literal"),
    )
    for f in java_files():
        src = strip_comments_and_strings(f.read_text(encoding="utf-8"))
        for pattern, detail in patterns:
            for m in re.finditer(pattern, src):
                line = src[: m.start()].count("\n") + 1
                errors.append(
                    f"literal synched-data id: {f.relative_to(ROOT)}:{line} -> {detail} "
                    f"({m.group(1)}); use Entity.DATA_SHARED_FLAGS_ID.id()"
                )


# ------------------------------------------------- per-viewer state must be released
def check_viewer_state_released() -> None:
    """Per-viewer packet state has to come off when the thing it points at goes away.

    All three 1.1.1 fixes were this: an override that was set and then never cleared, because
    the code path that stopped tracking a pair was not the code path that restored it. The
    structural guard is that the disconnect and respawn hooks exist at all, since deleting
    them reintroduces the whole class in one line.
    """
    if not ENTRYPOINT.exists():
        errors.append("state: CullTagMod.java is missing")
        return
    entry = ENTRYPOINT.read_text(encoding="utf-8")

    required = {
        "ServerPlayConnectionEvents.DISCONNECT":
            "a viewer who leaves must not leave other clients holding an override for them",
        "ServerPlayerEvents.AFTER_RESPAWN":
            "a respawned player is a new entity with a new ID; the old one leaks forever",
    }
    for hook, why in required.items():
        if hook not in entry:
            errors.append(f"state: {hook} is not registered in CullTagMod ({why})")

    if ENGINE.exists():
        engine = ENGINE.read_text(encoding="utf-8")
        for fn in ("forgetPlayer", "forgetEntity"):
            if f"{fn}(" not in engine:
                errors.append(f"state: LineOfSightEngine.{fn} is gone, so nothing releases overrides")


def check_viewer_state_listed() -> None:
    """A list, not a complaint: every per-viewer set that lives on the connection.

    Each one is state a client holds because CullTag told it to. Adding a third is fine; what
    is not fine is adding one that no path takes back off, so each shows up here to be
    answered on every review.
    """
    if not CONTROLLER.exists():
        return
    src = CONTROLLER.read_text(encoding="utf-8")
    for m in re.finditer(r"Set<[\w<>, ]+>\s+(culltag_get\w+)\(\)", src):
        warnings.append(
            f"per-viewer state to confirm is released on disconnect: "
            f"NametagController.{m.group(1)}()"
        )


# ------------------------------------------------------------ datapack tags exist
def check_tag_files_exist() -> None:
    """A TagKey whose datapack file is missing resolves to an empty tag, silently.

    Nothing throws and nothing logs; the feature the tag drives just stops applying to
    anything. #culltag:transparent decides which blocks are see-through, so losing the file
    would put every pane of glass back to hiding nametags with no sign of why.
    """
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for m in re.finditer(
                r"TagKey\.create\(\s*Registries\.(\w+)\s*,\s*"
                r"Identifier\.fromNamespaceAndPath\(\s*\"(\w+)\"\s*,\s*\"([\w/]+)\"", src):
            registry, namespace, path = m.group(1).lower(), m.group(2), m.group(3)
            expected = RESOURCES / "data" / namespace / "tags" / registry / f"{path}.json"
            if not expected.exists():
                errors.append(
                    f"tag: {f.relative_to(ROOT)} uses #{namespace}:{path} but "
                    f"{expected.relative_to(ROOT).as_posix()} does not exist "
                    f"(a missing tag resolves to empty and the feature quietly stops working)"
                )


def check_occlusion_goes_through_sight_test() -> None:
    """Level.clip asks "would I walk into this", which is not "can I see through this".

    Glass, panes and iron bars all have collision, so a collision raycast hid the nametag of a
    player standing behind a glass wall in plain view. SightTest exists to ask the right
    question, and a stray Level.clip call would silently reintroduce the old answer for
    whatever path it is on.
    """
    for f in java_files():
        if f.name == "SightTest.java":
            continue
        src = strip_comments_and_strings(f.read_text(encoding="utf-8"))
        for m in re.finditer(r"\.clip\(\s*new ClipContext|ClipContext\.Block\.", src):
            line = src[: m.start()].count("\n") + 1
            errors.append(
                f"raw collision raycast: {f.relative_to(ROOT)}:{line} "
                f"(use SightTest.clear; ClipContext.Block.COLLIDER treats glass as a wall)"
            )


# --------------------------------------------------------------- server-side only
def check_no_client_classes() -> None:
    """The product claim is that players install nothing, so the jar is environment: server.

    A net.minecraft.client reference compiles fine in the merged dev classpath and throws
    NoClassDefFoundError on a real dedicated server.
    """
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for m in re.finditer(r"\bnet\.minecraft\.client\.[\w.]+", src):
            line = src[: m.start()].count("\n") + 1
            errors.append(
                f"client class in a server-only mod: {f.relative_to(ROOT)}:{line} -> {m.group(0)}"
            )

    if FABRIC_JSON.exists():
        try:
            meta = json.loads(FABRIC_JSON.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return
        if meta.get("environment") != "server":
            errors.append(
                f"metadata: fabric.mod.json environment is "
                f"'{meta.get('environment')}', not 'server'"
            )


# ----------------------------------------------------------------- config integrity
def check_config_alignment() -> None:
    """The properties template and its .formatted() args must line up by name.

    A mismatch throws MissingFormatArgumentException at runtime, not compile time, so the
    config silently stops saving.
    """
    src = CONFIG.read_text(encoding="utf-8")
    try:
        start = src.index('return """')
        mid = src.index('""".formatted(', start)
        end = src.index("\n    }", mid)
    except ValueError:
        errors.append("config: could not locate the properties template")
        return

    body, blob = src[start:mid], src[mid + len('""".formatted('):end]
    keys = [
        m.group(1)
        for line in body.splitlines()
        if not line.strip().startswith("#")
        and (m := re.match(r"([A-Za-z0-9_]+)=(%[sdb])\s*$", line.strip()))
    ]

    depth, args, cur = 0, [], ""
    for ch in blob:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            if depth == 0:
                break
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip().rstrip(");").strip():
        args.append(cur.strip().rstrip(");").strip())
    args = [a for a in args if a]

    if len(keys) != len(args):
        errors.append(f"config: {len(keys)} template keys but {len(args)} format args")
    for key, arg in zip(keys, args):
        if key.replace("_", "") != arg.lower():
            errors.append(f"config: key '{key}' is fed by field '{arg}'")


def check_config_fields_wired() -> None:
    """A knob has to be read from the file, written back to the file, and actually consulted.

    The first two are the Dreambound rule. The third is CullTag's: crouch_hides_nametag
    shipped in 1.1.0 parsed, saved, logged and reported by /culltag reload, and its
    implementation piggybacked on a mechanism that could not hide a nametag at close range,
    so the feature did nothing at all. A field nothing outside the config class reads is the
    static shadow of that.
    """
    src = CONFIG.read_text(encoding="utf-8")
    fields = [m.group(2) for m in re.finditer(r"^    public static volatile (boolean|int) (\w+)\s*=", src, re.M)]
    others = "\n".join(
        f.read_text(encoding="utf-8") for f in java_files() if f != CONFIG
    )
    for name in fields:
        if f'"{name}"' not in src and not re.search(rf'"{re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()}"', src):
            errors.append(f"config: field '{name}' is never read from the properties file")
        if f"{name}=%" not in src and not re.search(
                rf"^\s*{re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()}=%", src, re.M):
            errors.append(f"config: field '{name}' is missing from the properties template")
        if not re.search(r"\bCullTagConfig\." + re.escape(name) + r"\b", others):
            errors.append(
                f"config: field '{name}' is never read outside CullTagConfig "
                f"(a knob wired to nothing looks configurable and is not)"
            )


# --------------------------------------------------------------- access widener
def check_access_widener_used() -> None:
    """Every widened member should be one the code actually needs.

    A widener entry is public API surface prised open on somebody else's class; one left
    behind after the code stopped using it is dead surface, and it is also the first thing to
    check when validateAccessWidener starts failing on a Minecraft bump.
    """
    if not ACCESS_WIDENER.exists():
        return
    sources = "\n".join(f.read_text(encoding="utf-8") for f in java_files())
    for raw in ACCESS_WIDENER.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("accessWidener"):
            continue
        parts = line.split()
        if len(parts) < 4 or parts[1] not in ("field", "method", "class"):
            continue
        member = parts[3] if parts[1] != "class" else parts[2].rsplit("/", 1)[-1]
        if not re.search(r"\b" + re.escape(member) + r"\b", sources):
            errors.append(
                f"access widener: '{member}' is widened but never referenced in the source"
            )


# --------------------------------------------------------------- displayed text
# Two real words next to each other. A command name, a key fragment or a separator is not prose.
_PROSE = re.compile(r"[A-Za-z]{2,}\s+[A-Za-z]{2,}")


def _literal_args(src: str):
    """Yields (argument_source, line) for every Component.literal(...) call."""
    for m in re.finditer(r"Component\.literal\(", src):
        i, depth = m.end(), 1
        while i < len(src) and depth:
            if src[i] == '"':  # skip over string literals, including escapes
                i += 1
                while i < len(src) and src[i] != '"':
                    i += 2 if src[i] == "\\" else 1
            elif src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if not depth:
                    break
            i += 1
        yield src[m.end():i], src[: m.start()].count("\n") + 1


def check_hardcoded_display_text() -> None:
    """Component.literal("some English") shipped to a player cannot be translated.

    Only Component.literal is checked, because that is the sink. A bare English string being
    passed around as a String is not yet a bug; it becomes one here. A single word is allowed
    through, which is what keeps the "CullTag" brand token legal.
    """
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for arg, line in _literal_args(src):
            texts = re.findall(r'"((?:\\.|[^"\\])*)"', arg)
            if any(t.startswith("/") for t in texts):
                continue
            prose = [t for t in texts if _PROSE.search(t)]
            # Gluing a literal onto a value bakes English word order in even when neither half
            # is prose on its own.
            glued = "+" in arg and any(t.strip() for t in texts)
            if not prose and not glued:
                continue
            shown = (prose[0] if prose else next(t for t in texts if t.strip()))[:48]
            detail = "concatenated display text" if glued and not prose else "hardcoded display text"
            errors.append(
                f"{detail}: {f.relative_to(ROOT)}:{line} -> "
                f'"{shown}" (use Component.translatableWithFallback with %1$s args)'
            )


def check_bare_translatable() -> None:
    """Component.translatable without a fallback shows a vanilla client the raw key.

    This mod's entire premise is that the player is on a vanilla client that has never heard
    of it, so a bare key does not degrade to English; it renders as culltag.command.enable.
    """
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for m in re.finditer(r"Component\.translatable\(", src):
            line = src[: m.start()].count("\n") + 1
            errors.append(
                f"translatable without fallback: {f.relative_to(ROOT)}:{line} "
                f"(server-side mod: use translatableWithFallback)"
            )


def check_lang_keys_resolve() -> None:
    """Every culltag.* key used in code should exist in en_us.json, and vice versa.

    The fallback means a missing key is invisible in English, so this would otherwise only
    surface as untranslatable text in somebody else's language.
    """
    if not LANG.exists():
        errors.append("lang: assets/culltag/lang/en_us.json is missing")
        return
    try:
        keys = set(json.loads(LANG.read_text(encoding="utf-8")))
    except json.JSONDecodeError as exc:
        errors.append(f"lang: en_us.json does not parse ({exc})")
        return

    used: set[str] = set()
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for key in re.findall(r'"(culltag\.[\w.]+)"', src):
            if key.rsplit(".", 1)[-1] in NOT_KEY_SUFFIXES:
                continue
            used.add(key)

    for key in sorted(used - keys):
        errors.append(f"lang: '{key}' is used in code but missing from en_us.json")
    for key in sorted(keys - used):
        warnings.append(f"lang: '{key}' is defined but never used")


def check_lang_fallbacks_match() -> None:
    """The English in translatableWithFallback must match en_us.json.

    Two copies of the same sentence drift, and then the mod says one thing to a vanilla client
    and another to a client that has the language file.
    """
    if not LANG.exists():
        return
    try:
        lang = json.loads(LANG.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return

    pattern = re.compile(
        r'translatableWithFallback\(\s*"(culltag\.[\w.]+)"\s*,\s*"((?:\\.|[^"\\])*)"', re.S
    )
    for f in java_files():
        src = f.read_text(encoding="utf-8")
        for m in pattern.finditer(src):
            fallback = (
                m.group(2)
                .replace(r"\"", '"')
                .replace(r"\n", "\n")
                .replace(r"\\", "\\")
            )
            key = m.group(1)
            if key in lang and lang[key] != fallback:
                errors.append(
                    f"lang: fallback for '{key}' does not match en_us.json "
                    f"({fallback!r} vs {lang[key]!r})"
                )


def check_text_is_centralised() -> None:
    """Nothing outside CullTagText builds player-facing text.

    One place to hold every shape is what keeps the fallback, the key and the argument order
    from being reinvented per call site.
    """
    for f in java_files():
        if f.name == "CullTagText.java":
            continue
        src = f.read_text(encoding="utf-8")
        if "translatableWithFallback" in src:
            line = src.index("translatableWithFallback")
            warnings.append(
                f"player text built outside CullTagText: {f.relative_to(ROOT)}:"
                f"{src[:line].count(chr(10)) + 1}"
            )


# ------------------------------------------------------------------ metadata sanity
def check_declared_license() -> None:
    """fabric.mod.json's license is what a user sees in the mod list and on Modrinth.

    build.gradle already does `from('LICENSE')`, which silently bundles nothing when the file
    is absent, so the jar can ship claiming MIT with no licence text in it.
    """
    if not FABRIC_JSON.exists():
        errors.append("metadata: fabric.mod.json is missing")
        return
    try:
        meta = json.loads(FABRIC_JSON.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        errors.append(f"metadata: fabric.mod.json does not parse ({exc})")
        return

    declared = meta.get("license", "")
    license_path = ROOT / "LICENSE"
    if declared and not license_path.exists():
        errors.append(
            f"metadata: fabric.mod.json declares '{declared}' but there is no LICENSE file "
            f"(build.gradle bundles one and silently ships nothing)"
        )
        return
    if license_path.exists():
        text = license_path.read_text(encoding="utf-8")
        if "MIT License" in text and declared != "MIT":
            errors.append(f"metadata: LICENSE is MIT but fabric.mod.json declares '{declared}'")


def check_config_keys_documented() -> None:
    """Every config key should be findable in the README, which doubles as the Modrinth page."""
    readme = (ROOT / "README.md").read_text(encoding="utf-8") if (ROOT / "README.md").exists() else ""
    src = CONFIG.read_text(encoding="utf-8")
    for m in re.finditer(r"^\s*([a-z][a-z0-9_]*)=%[sdb]\s*$", src, re.M):
        if m.group(1) not in readme:
            warnings.append(f"config key '{m.group(1)}' is not mentioned in README.md")


# ----------------------------------------------------------- fully-qualified names
def check_inline_qualified_names() -> None:
    """`net.minecraft...` written inline instead of imported. Cosmetic, reads as unfinished."""
    for f in java_files():
        src = strip_comments_and_strings(f.read_text(encoding="utf-8"))
        src = re.sub(r"^import .*$", "", src, flags=re.M)
        hits = re.findall(r"\b(?:com\.culltag|net\.minecraft|java\.util)\.[\w.]+\.[A-Z]\w+", src)
        if not hits:
            continue
        distinct = sorted(set(hits))
        shown = ", ".join(t.rsplit(".", 1)[-1] for t in distinct[:4])
        more = f" +{len(distinct) - 4} more" if len(distinct) > 4 else ""
        warnings.append(
            f"inline qualified names: {f.relative_to(ROOT)} "
            f"({len(hits)} refs: {shown}{more}) - import instead"
        )


# --------------------------------------------------------------------- em dashes
def check_no_em_dashes() -> None:
    """No em dashes anywhere in the repo, including the wiki, the docs and the changelog.

    A house style rule rather than a correctness one, but a mechanical one, so it is checked
    mechanically instead of being remembered. Use a comma for an aside, a colon before an
    explanation, a semicolon between two full clauses, or parentheses. Do not substitute
    blindly: a comma is wrong about a quarter of the time and gives you a comma splice.
    """
    tracked = [
        *java_files(),
        *sorted(ROOT.glob("*.md")),
        *(sorted((ROOT / "wiki").rglob("*.md")) if (ROOT / "wiki").is_dir() else []),
        *(sorted((ROOT / "docs").rglob("*.md")) if (ROOT / "docs").is_dir() else []),
        *(sorted((ROOT / ".claude").rglob("*.md")) if (ROOT / ".claude").is_dir() else []),
        *sorted((ROOT / "scripts").glob("*.py")),
        ROOT / "build.gradle",
        ROOT / "gradle.properties",
        FABRIC_JSON,
    ]
    if LANG.exists():
        tracked.append(LANG)

    for f in tracked:
        if not f.is_file():
            continue
        try:
            src = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for n, line in enumerate(src.splitlines(), 1):
            if EM_DASH in line:
                errors.append(
                    f"em dash: {f.relative_to(ROOT).as_posix()}:{n} "
                    f"(use a comma, colon, semicolon or parentheses)"
                )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quiet", action="store_true", help="errors only")
    args = parser.parse_args()

    for check in (
        check_unused_imports,
        check_unused_private_members,
        check_mixin_unique,
        check_mixins_registered,
        check_mixin_target_names,
        check_mixin_hardcoded_indexes,
        check_no_literal_synched_data_ids,
        check_viewer_state_released,
        check_viewer_state_listed,
        check_tag_files_exist,
        check_occlusion_goes_through_sight_test,
        check_no_client_classes,
        check_config_alignment,
        check_config_fields_wired,
        check_access_widener_used,
        check_hardcoded_display_text,
        check_bare_translatable,
        check_lang_keys_resolve,
        check_lang_fallbacks_match,
        check_text_is_centralised,
        check_declared_license,
        check_config_keys_documented,
        check_inline_qualified_names,
        check_no_em_dashes,
    ):
        check()

    if errors:
        print(f"ERRORS ({len(errors)}) - fix before shipping")
        for e in errors:
            print("  " + e)
    if warnings and not args.quiet:
        print(f"\nWARNINGS ({len(warnings)}) - review, may be intentional")
        for w in warnings:
            print("  " + w)
    if not errors and (args.quiet or not warnings):
        print("clean")
    elif not errors:
        print(f"\nno errors ({len(warnings)} warnings)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
