See @docs/tasks.md for task management

# AI Agent Instructions

## Project

Fabric server-side mod adding structure markers to BlueMap. No external structure-finding libs — we implement the seed-based position algorithm directly.

Multi-version via [Stonecutter](https://stonecutter.kikugie.dev): one source tree builds two targets — **MC 1.21.11** (Java 21) and **MC 26.2** (Java 25). Both compile against **Mojang mappings** (Yarn was discontinued from 26.1, the first unobfuscated release), so the source is written once in Mojmap names and `loomx.applyMojangMappings()` covers every version. See `docs/multiversion.md`.

## Documentation

Detailed docs are in `docs/`. Read the relevant doc before working on that area:

- `docs/architecture.md` — Mod structure, data flow, design decisions
- `docs/structure-algorithm.md` — Position algorithm, region seeds, spread types, parameter table
- `docs/multiversion.md` — Stonecutter setup, Mojmap, adding/dropping MC versions
- `docs/testing.md` — How to run/add tests, MC classpath in tests
- `docs/dev-setup.md` — Build commands, formatting, project structure

## Source Layout

```
src/main/java/dev/danny/bluemapstructures/
  BlueMapStructuresMod.java    — Fabric entrypoint, hooks SERVER_STARTED
  StructureType.java           — Enum: 19 structures with spacing/salt/dimension/maxDistance/icon
  StructureLocator.java        — Grid-based position algorithm (linear + triangular spread)
  StrongholdLocator.java       — Reads vanilla positions at runtime, geometric fallback
  BuriedTreasureLocator.java   — Buried treasure algorithm (region-map based)
  EndCityShipDetector.java     — Detects whether an end city has a ship
  BiomeValidator.java          — Biome checking via BiomeSource (no chunk loading)
  BlueMapIntegration.java      — Uploads icons, creates MarkerSets + POIMarkers
  ModConfig.java               — JSON config (radius, per-structure toggles)

src/test/java/dev/danny/bluemapstructures/
  StructureLocatorTest.java         — Algorithm correctness + regression guards
  ChunkRandomVerificationTest.java  — Our RNG vs Minecraft's ChunkRandom (village + trial chambers)
  ChunkbaseComparisonTest.java      — Compare against Chunkbase extraction data
  EndCityShipDetectorTest.java      — Ship detection regression tests

src/main/resources/
  icons/                       — 22x22 PNG icons per structure type
  fabric.mod.json
```

## Build & Test

```bash
./gradlew chiseledBuild    # build + test ALL versions, collect jars to build/libs/<ver>/
./gradlew build            # build + test all versions (jars stay under versions/<id>/build)
./gradlew check            # compile + tests, all versions (no JAR)
./gradlew test             # JUnit tests only, all versions
./gradlew spotlessApply    # auto-fix formatting (runs at repo root over src/)
./gradlew spotlessCheck    # verify formatting
```

The "active" version (set in `stonecutter.gradle.kts`, default `1.21.11`) is what the IDE
type-checks. Switch with the generated `Set active project to <ver>` Gradle task. Run the
`Reset active project` task before committing. Per-version dependency strings live in
`stonecutter.properties.toml`.

## Known Limitations

1. **Pillager Outpost ~5x false positives** — vanilla has a `frequency: 0.2` filter we don't implement
2. **Surface height checks missing** — Desert Pyramids, Jungle Temples, Mansions have height checks in vanilla (~5% false positives)
3. **Dimension matching** uses `.contains()` on BlueMap world ID — works for vanilla, could misfire with custom world configs

## Dependencies

Per-version strings are in `stonecutter.properties.toml`; shared tooling in `build.gradle.kts` / `settings.gradle.kts`.

- Stonecutter 0.9.6 + `loom-back-compat` 0.3 (Fabric Loom 1.17), Gradle 9.5.1
- Fabric Loader 0.19.3; Fabric API `0.141.4+1.21.11` / `0.152.2+26.2`
- BlueMapAPI `compileOnly` — coordinates differ by line: `de.bluecolored.bluemap:BlueMapAPI:2.7.2` (1.21.11) vs `de.bluecolored:bluemap-api:2.8.0` (26.2). Mod works without BlueMap, just does nothing.
- Minecraft 1.21.11 (Java 21) and 26.2 (Java 25), **Mojang mappings** for both
- JUnit Jupiter 5.10.3 (test only)
