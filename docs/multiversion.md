# Multi-version builds (Stonecutter)

The mod targets two Minecraft versions from one source tree using [Stonecutter](https://stonecutter.kikugie.dev):

| Target | Java | Mappings | Fabric API | BlueMapAPI |
|---|---|---|---|---|
| **1.21.11** | 21 | Mojang | `0.141.4+1.21.11` | `de.bluecolored.bluemap:BlueMapAPI:2.7.2` |
| **26.2** | 25 | Mojang | `0.152.2+26.2` | `de.bluecolored:bluemap-api:2.8.0` |

## Why this is set up the way it is

Minecraft **26.1 was the first unobfuscated release**, and Fabric **discontinued Yarn** at that point. A single Fabric jar therefore can't span 1.21.11 (Yarn-era) and 26.x (Mojmap-era) the way it could across older versions.

The clean way out — and what `loom-back-compat`'s `loomx.applyMojangMappings()` enables — is to use **Mojang mappings for every target, including the older obfuscated ones**. So:

- The Java source is written **once, in Mojmap names**.
- Both versions compile from the same tree. We currently need **zero** `//? if` conditionals or `replacements`, because the API surface the mod touches is identical between 1.21.11 and 26.2 in Mojmap terms.

Mojang's mappings here are not quite the "classic" Mojmap names you may remember — e.g. `ResourceLocation` is named **`Identifier`** (`net.minecraft.resources.Identifier`). When in doubt, inspect the mapped jar with `javap` (see below) rather than guessing.

## Layout

```
settings.gradle.kts          — declares versions("1.21.11", "26.2"), vcsVersion, plugins
build.gradle.kts             — per-version build (applied to each version subproject):
                               mappings, deps, Java toolchain, processResources, buildAndCollect
stonecutter.gradle.kts       — ROOT build script: active version, spotless, chiseledBuild
stonecutter.properties.toml  — per-version dependency strings + mod metadata
versions/<id>/               — Stonecutter-generated subproject dirs (build output only; gitignored)
```

Spotless lives in `stonecutter.gradle.kts` (the root, whose projectDir is the repo root) — **not** in `build.gradle.kts`. Each version subproject is rooted at `versions/<id>`, and spotless rejects the shared `src/` as "outside project dir" from there.

## Common tasks

```bash
./gradlew chiseledBuild   # build + test every version, collect jars to build/libs/<mod ver>/
./gradlew build           # same, but jars stay under versions/<id>/build/libs/
./gradlew test            # tests for all versions
./gradlew spotlessApply   # format src/ (root task)
```

The **active version** (in `stonecutter.gradle.kts`, default `1.21.11`) is what the IDE
type-checks. Switch via the generated `Set active project to <ver>` Gradle task, and run
`Reset active project` before committing so the working tree matches `vcsVersion`.

## Adding or dropping a version

1. Edit `versions(...)` in `settings.gradle.kts`.
2. Add/remove the `["<ver>"]` table in `stonecutter.properties.toml` (set `deps.fabric_api`, `mod.mc_compat`).
3. If the new version needs a different Java level, extend the `requiredJava` `when` in `build.gradle.kts`.
4. Build. If the Mojmap API differs for that version, the compiler points at the spot — fix it with a
   `//? if <predicate> { ... //?}` block or a buildscript `replacements {}` rule in
   `stonecutter.gradle.kts`, keeping the default branch valid for `vcsVersion`.

## Finding the right Mojmap symbol

When a symbol doesn't resolve, find its real name in the mapped jar instead of guessing:

```bash
JAR=$(find ~/.gradle/caches/fabric-loom/minecraftMaven -name "*.jar" | grep <version> | head -1)
unzip -l "$JAR" | grep -i SomeClassName          # locate the class / its package
javap -classpath "$JAR" net.minecraft.some.Pkg.SomeClass | grep -i methodHint
```

Names confirmed this way during the 26.2 port:
- `ResourceLocation` → **`Identifier`** (`net.minecraft.resources.Identifier`), factory `fromNamespaceAndPath(ns, path)`
- `ChunkGeneratorStructureState` lives in `net.minecraft.world.level.chunk` (not `...levelgen.structure`)
- World spawn: `serverLevel.getLevelData().getRespawnData().pos()` (the old `getSharedSpawnPos()` is gone)
- RNG (tests): `WorldgenRandom` + `LegacyRandomSource`, `setLargeFeatureWithSalt(seed, rx, rz, salt)`, `RandomSpreadType.LINEAR.evaluate(rand, bound)`
