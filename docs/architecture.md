# Architecture

## Overview

This is a Fabric server-side mod that adds structure markers (villages, temples, fortresses, etc.) to BlueMap. It predicts structure positions mathematically from the world seed rather than reading generated chunk data, giving coverage of unexplored areas like Chunkbase does.

## Source Layout

```
src/main/java/dev/danny/bluemapstructures/
  BlueMapStructuresMod.java    — Fabric entrypoint
  StructureType.java           — Enum: 19 structures with grid parameters
  StructureLocator.java        — Seed-based position algorithm
  StrongholdLocator.java       — Concentric rings algorithm (strongholds only)
  BiomeValidator.java          — Biome checking via BiomeSource
  BlueMapIntegration.java      — Creates MarkerSets + POIMarkers
  ModConfig.java               — JSON config (radius, per-structure toggles)

src/test/java/dev/danny/bluemapstructures/
  StructureLocatorTest.java         — Algorithm correctness + regression guards
  ChunkRandomVerificationTest.java  — Our RNG vs Minecraft's ChunkRandom
```

## Data Flow

```
SERVER_STARTED event
  → BlueMapStructuresMod extracts world seed, loads config
  → BlueMapIntegration.register(server, seed, config)
    → BlueMapAPI.onEnable callback fires when BlueMap is ready
      → Create BiomeValidator per dimension (overworld, nether, end)
      → For each enabled StructureType:
        → StructureLocator.findStructures(type, seed, radius, validator)
          → Compute candidate positions from seed math
          → Filter through BiomeValidator.isValidPosition()
        → Build MarkerSet + POIMarkers from surviving positions
        → Attach MarkerSet to matching BlueMap maps
```

## Key Design Decisions

### Own algorithm instead of SeedFinding libs

The structure position algorithm is ~50 lines. The SeedFinding Java libraries were last updated around 1.18 and would need extending for newer structures. Since the core algorithm hasn't changed between MC versions (just the spacing/salt parameters per structure), implementing it ourselves is simpler and more maintainable than taking on a stale dependency.

The algorithm is verified against Minecraft's own `ChunkRandom.setRegionSeed()` in `ChunkRandomVerificationTest`.

### BiomeSource for biome validation

Without biome checking, the position algorithm produces massive false positives (~80-90% for villages, since grid positions land in oceans, mountains, etc.). The mod uses `BiomeSource.getBiome()` with `MultiNoiseSampler` instead of `world.getBiome()` to validate positions.

Why this matters: `world.getBiome()` forces chunk loading/generation for unloaded positions. `BiomeSource` is a pure function of coordinates and noise parameters — it works for any position regardless of whether the chunk exists, and is thread-safe.

The biome is sampled at Y=64 (sea level), which is a reasonable approximation for all current structure types.

### Dimension matching

BlueMap maps are matched to Minecraft dimensions by checking if the map's world ID string contains `"overworld"`, `"the_nether"`, or `"the_end"`. This works for vanilla setups but could misfire with custom world configurations that use non-standard naming.

### BlueMapAPI as compileOnly

BlueMapAPI is a `compileOnly` dependency. The mod loads and works even if BlueMap isn't installed — it just does nothing (the `BlueMapAPI.onEnable` callback never fires). No runtime dependency on BlueMap.

## Known Limitations

1. **Pillager Outpost false positives** — Vanilla has a `frequency: 0.2` filter that rejects 80% of grid positions. We don't implement this, so we show ~5x too many outposts.
2. **Surface height checks missing** — Desert Pyramids, Jungle Temples, and Mansions have height validation in vanilla (~5% false positives remain even with biome checking).
3. **Dimension matching fragility** — The `.contains()` check on BlueMap world IDs works for vanilla but could break with custom world configs.
