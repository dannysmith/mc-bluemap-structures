See @docs/tasks.md for task management

# AI Agent Instructions

## Project

Fabric server-side mod (Java 21, MC 1.21.11) adding structure markers to BlueMap. No external structure-finding libs — we implement the seed-based position algorithm directly.

## Documentation

Detailed docs are in `docs/`. Read the relevant doc before working on that area:

- `docs/architecture.md` — Mod structure, data flow, design decisions
- `docs/structure-algorithm.md` — Position algorithm, region seeds, spread types, parameter table
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
./gradlew build            # compile + format check + tests + JAR
./gradlew check            # compile + format check + tests (no JAR)
./gradlew test             # JUnit tests only
./gradlew spotlessApply    # auto-fix formatting
```

## Known Limitations

1. **Pillager Outpost ~5x false positives** — vanilla has a `frequency: 0.2` filter we don't implement
2. **Surface height checks missing** — Desert Pyramids, Jungle Temples, Mansions have height checks in vanilla (~5% false positives)
3. **Dimension matching** uses `.contains()` on BlueMap world ID — works for vanilla, could misfire with custom world configs

## Dependencies

- Fabric API, Fabric Loader 0.18+
- BlueMapAPI 2.7.2 (`compileOnly` — mod works without BlueMap, just does nothing)
- Minecraft 1.21.11, Yarn mappings, Java 21
- JUnit Jupiter 5.10.3 (test only)
