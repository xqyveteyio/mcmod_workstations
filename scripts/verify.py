#!/usr/bin/env python3
"""Static checks for the things a Gradle build cannot fail on.

`gradlew build` compiles Java and zips resources. It does not read a single one of
those resources, so a malformed recipe, a model pointing at a texture that was
never drawn, or a translation key nobody translated all build perfectly and then
misbehave in game. Worse, most of them misbehave *quietly*: a missing lang key
shows the raw key on screen, and a missing texture shows as black-and-magenta,
neither of which raises anything in a log.

Run this after every change to `src/main/resources` or to any string literal that
looks like a translation key:

    python3 scripts/verify.py

Exits 0 when everything passes and 1 otherwise, so it can gate a commit.
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
JAVA = ROOT / "src/main/java"

failures = []
notes = []


def fail(check, detail):
    failures.append((check, detail))


def read_java():
    return "\n".join(p.read_text(encoding="utf-8") for p in JAVA.rglob("*.java"))


# --------------------------------------------------------------------------
# 1. Every JSON resource parses.
#    A trailing comma is the single most common way to break a resource pack,
#    and the game answers it by dropping the file with a log line nobody reads.
# --------------------------------------------------------------------------
def check_json_parses():
    bad = []

    for path in RES.rglob("*.json"):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            bad.append(f"{path.relative_to(ROOT)}: {error}")

    if bad:
        fail("json parses", "\n".join(bad))

    return len(list(RES.rglob("*.json")))


# --------------------------------------------------------------------------
# 2. The mod id agrees with itself in all four places it is written down.
#    These are independent strings, so a rename that misses one leaves a mod
#    whose assets are simply never found under the id it registers things with.
# --------------------------------------------------------------------------
def check_mod_id():
    manifest = json.loads((RES / "fabric.mod.json").read_text(encoding="utf-8"))
    declared = manifest["id"]

    source = (JAVA / "dev/keyboard/workstations/WorkstationsMod.java").read_text(encoding="utf-8")
    match = re.search(r'MOD_ID\s*=\s*"([a-z0-9_\-]+)"', source)
    constant = match.group(1) if match else "<not found>"

    asset_dirs = sorted(p.name for p in (RES / "assets").iterdir() if p.is_dir())
    data_dirs = sorted(p.name for p in (RES / "data").iterdir() if p.is_dir() and p.name != "minecraft")

    if declared != constant:
        fail("mod id", f"fabric.mod.json says {declared!r} but MOD_ID is {constant!r}")

    if asset_dirs != [declared]:
        fail("mod id", f"assets/ holds {asset_dirs} but the id is {declared!r}")

    if data_dirs != [declared]:
        fail("mod id", f"data/ holds {data_dirs} (minecraft excluded) but the id is {declared!r}")

    return declared


# --------------------------------------------------------------------------
# 3. Both languages carry the same keys.
#    Adding a string to en_us and forgetting zh_cn leaves Chinese players
#    reading English, which is invisible to anyone testing in English.
# --------------------------------------------------------------------------
def check_lang_symmetry(mod_id):
    langs = {}

    for path in sorted((RES / f"assets/{mod_id}/lang").glob("*.json")):
        langs[path.stem] = json.loads(path.read_text(encoding="utf-8"))

    if len(langs) < 2:
        fail("lang symmetry", f"expected at least two language files, found {sorted(langs)}")
        return langs

    reference = "en_us"

    for name, table in langs.items():
        if name == reference:
            continue

        missing = sorted(set(langs[reference]) - set(table))
        extra = sorted(set(table) - set(langs[reference]))

        if missing:
            fail("lang symmetry", f"{name}.json is missing: {', '.join(missing)}")

        if extra:
            fail("lang symmetry", f"{name}.json has keys {reference} lacks: {', '.join(extra)}")

    return langs


# --------------------------------------------------------------------------
# 4. Every key the code asks for has been translated.
#    Keys reach the lang file three ways: written out in full, assembled from a
#    SettingOption's name, or derived by vanilla from a registry id. All three
#    fail the same silent way, so all three are checked.
# --------------------------------------------------------------------------
def check_keys_exist(mod_id, langs):
    source = read_java()
    known = set(langs.get("en_us", {}))

    prefixes = "block|item|entity|itemGroup|config|container|message|key|category"
    literal = {
        key.rstrip(".")
        for key in re.findall(rf'"((?:{prefixes})\.{mod_id}[A-Za-z0-9_.]*)"', source)
    }
    # The bare prefix used to build keys at runtime is not itself a key.
    literal.discard(f"config.{mod_id}")

    # SettingOption.flag(CATEGORY, "enable_breeding", ...) -> config.<id>.enable_breeding
    # and a matching .tooltip beside it.
    options = set(re.findall(r'SettingOption\.(?:flag|range|choice)\(\s*\w+\s*,\s*"([a-z0-9_]+)"', source))
    built = {f"config.{mod_id}.{name}" for name in options}
    built |= {f"config.{mod_id}.{name}.tooltip" for name in options}

    # Category tabs: "category.breeding" -> config.<id>.category.breeding
    categories = set(re.findall(r'=\s*"(category\.[a-z_]+)"\s*;', source))
    built |= {f"config.{mod_id}.{name}" for name in categories}

    missing = sorted((literal | built) - known)

    if missing:
        fail("keys translated", "\n".join(f"  no translation for {key}" for key in missing))

    notes.append(f"translation keys referenced: {len(literal)} literal, {len(built)} assembled")
    return literal | built


# --------------------------------------------------------------------------
# 5. Every asset a model or blockstate points at is really there.
#    A texture that does not exist renders as the black-and-magenta placeholder
#    with nothing in the log, which is easy to mistake for a UV mistake.
# --------------------------------------------------------------------------
def check_asset_refs(mod_id):
    assets = RES / "assets" / mod_id
    missing = []
    checked = 0

    def resolve(ref):
        """Where a namespaced reference could legitimately live."""
        return [
            assets / f"models/{ref}.json",
            assets / f"textures/{ref}.png",
            assets / f"{ref}.png",
        ]

    for path in sorted(assets.rglob("*.json")):
        if path.parent.name == "lang":
            continue

        document = json.loads(path.read_text(encoding="utf-8"))
        text = json.dumps(document)

        for ref in re.findall(rf'"{mod_id}:([a-z0-9_/]+)"', text):
            checked += 1

            if not any(candidate.exists() for candidate in resolve(ref)):
                missing.append(f"  {path.relative_to(RES)} -> {mod_id}:{ref}")

    if missing:
        fail("asset references", "\n".join(missing))

    notes.append(f"namespaced asset references resolved: {checked}")


# --------------------------------------------------------------------------
# 6. Registry names used in data files were actually registered.
#    Recipes, loot tables and tags name blocks and items rather than files, so
#    a typo there is not a missing file but a silently dropped recipe.
# --------------------------------------------------------------------------
def check_registry_refs(mod_id):
    mod_source = (JAVA / "dev/keyboard/workstations/WorkstationsMod.java").read_text(encoding="utf-8")
    registered = set(re.findall(r'\w+_ID\s*=\s*id\("([a-z0-9_]+)"\)', mod_source))
    unknown = []

    for path in sorted((RES / "data").rglob("*.json")):
        text = path.read_text(encoding="utf-8")

        for ref in re.findall(rf'"{mod_id}:([a-z0-9_/]+)"', text):
            if ref not in registered:
                unknown.append(f"  {path.relative_to(RES)} -> {mod_id}:{ref} is never registered")

    if unknown:
        fail("registry references", "\n".join(unknown))

    notes.append(f"registered ids: {', '.join(sorted(registered))}")


# --------------------------------------------------------------------------
# 7. Imports left behind by deleted code.
#    javac says nothing about these, so they accumulate and go stale.
# --------------------------------------------------------------------------
def check_unused_imports():
    stale = []

    for path in sorted(JAVA.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        body = "\n".join(line for line in text.splitlines() if not line.startswith("import "))

        for name in re.findall(r"^import (?:static )?[\w.]*?(\w+);", text, re.MULTILINE):
            if not re.search(rf"\b{name}\b", body):
                stale.append(f"  {path.relative_to(ROOT)}: {name}")

    if stale:
        fail("unused imports", "\n".join(stale))


def main():
    print("Static checks that `gradlew build` cannot perform\n")

    total_json = check_json_parses()
    mod_id = check_mod_id()
    langs = check_lang_symmetry(mod_id)
    check_keys_exist(mod_id, langs)
    check_asset_refs(mod_id)
    check_registry_refs(mod_id)
    check_unused_imports()

    print(f"mod id       : {mod_id}")
    print(f"json files   : {total_json}")
    print(f"languages    : {', '.join(sorted(langs))}")

    for note in notes:
        print(f"note         : {note}")

    if not failures:
        print("\nAll checks passed.")
        return 0

    print(f"\n{len(failures)} check(s) FAILED\n")

    for check, detail in failures:
        print(f"[{check}]")
        print(detail if detail.startswith("  ") else f"  {detail}")
        print()

    return 1


if __name__ == "__main__":
    sys.exit(main())
