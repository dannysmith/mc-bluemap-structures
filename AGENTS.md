# AI Agent Instructions

## Project

Fabric server-side mod (Java 21, MC 1.21.1) adding structure markers to BlueMap. No external structure-finding libs — we implement the seed-based position algorithm directly.

## Source Layout

```
src/main/java/dev/danny/bluemapstructures/
  BlueMapStructuresMod.java    — Fabric entrypoint, hooks SERVER_STARTED
  StructureType.java           — Enum: 19 structures with spacing/salt/dimension/maxDistance/icon
  StructureLocator.java        — Grid-based position algorithm (linear + triangular spread)
  StrongholdLocator.java       — Concentric rings algorithm (strongholds only)
  BiomeValidator.java          — Biome checking via BiomeSource (no chunk loading)
  BlueMapIntegration.java      — Uploads icons, creates MarkerSets + POIMarkers
  ModConfig.java               — JSON config (radius, per-structure toggles)
  TestPositions.java           — Standalone test: prints positions for seed 12345
  TestAgainstMinecraft.java    — Verifies our RNG against MC's ChunkRandom
src/main/resources/
  icons/                       — 22x22 PNG icons per structure type (from Chunkbase sprite)
  fabric.mod.json
```

## Key Architecture Decisions

- **Own algorithm, no libs**: The structure position algorithm is ~50 lines. SeedFinding libs are stale (last updated ~1.18). Our implementation is verified against MC's own `ChunkRandom.setRegionSeed()`.
- **BiomeSource for validation**: Uses `BiomeSource.getBiome()` with `MultiNoiseSampler` instead of `world.getBiome()` to avoid chunk loading. Pure function of coordinates + seed, thread-safe.
- **Nether Fortress/Bastion share a grid**: Same spacing/salt, differentiated by a weight roll (`nextInt(5) < 2` = fortress).
- **BlueMap icons via AssetStorage**: Icons uploaded to each map's asset storage on enable, referenced by URL in POIMarkers.

## Build & Test

```bash
./gradlew build            # JAR at build/libs/bluemap-structures-1.0.0.jar
./gradlew testPositions    # Print positions for seed 12345 (no MC dependency)
./gradlew testVsMC         # Compare RNG vs MC's ChunkRandom (needs MC classpath)
```

## Known Limitations

1. **Pillager Outpost ~5x false positives** — vanilla has a `frequency: 0.2` filter we don't implement
2. **Surface height checks missing** — Desert Pyramids, Jungle Temples, Mansions have height checks in vanilla (~5% false positives)
3. **Dimension matching** uses `.contains()` on BlueMap world ID — works for vanilla, could misfire with custom world configs

## Dependencies

- Fabric API, Fabric Loader 0.16+
- BlueMapAPI 2.7.2 (`compileOnly` — mod works without BlueMap, just does nothing)
- Minecraft 1.21.1, Yarn mappings, Java 21
