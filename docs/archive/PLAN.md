# BlueMap Structure Markers — Project Reference

> Read this file to understand the project, its architecture, key decisions, and current state.
> RESEARCH.md has the original research notes and reference links.

## What This Is

A Fabric server-side mod for Minecraft 1.21.1 that adds Chunkbase-style toggleable structure markers to BlueMap. Each structure type gets its own toggleable MarkerSet in the BlueMap sidebar. No external dependencies beyond Fabric API and BlueMapAPI.

## Architecture

```
mc-bluemap-structures/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── PLAN.md                 # This file
├── RESEARCH.md             # Original research notes
├── src/main/java/dev/danny/bluemapstructures/
│   ├── BlueMapStructuresMod.java    # Fabric entrypoint — hooks SERVER_STARTED, passes server + seed to integration
│   ├── StructureType.java           # Enum: spacing/separation/salt/dimension + biome tag IDs
│   ├── StructureLocator.java        # Seed-based position algorithm (random_spread + nether complexes)
│   ├── StrongholdLocator.java       # Concentric rings algorithm (strongholds only)
│   ├── BiomeValidator.java          # Biome checking via BiomeSource (no chunk loading)
│   ├── BlueMapIntegration.java      # Creates MarkerSets + POIMarkers, one BiomeValidator per dimension
│   ├── ModConfig.java               # JSON config (radius, per-structure toggles)
│   ├── TestPositions.java           # Standalone: prints structure positions for a seed (no biome check)
│   └── TestAgainstMinecraft.java    # Standalone: compares our RNG vs MC's ChunkRandom class
├── src/main/resources/
│   ├── fabric.mod.json
│   └── assets/bluemapstructures/icon.png
```

## Toolchain Versions (build-tested)

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.115.0+1.21.1 |
| Yarn Mappings | 1.21.1+build.3 |
| Fabric Loom | 1.9-SNAPSHOT |
| BlueMapAPI | 2.7.2 (compileOnly) |
| Java | 21 |

## Data Flow

```
SERVER_STARTED event
  → BlueMapStructuresMod gets MinecraftServer, extracts seed, loads config
  → BlueMapIntegration.register(server, seed, config)
    → BlueMapAPI.onEnable callback fires when BlueMap is ready
      → Create BiomeValidator per dimension (overworld, nether, end)
      → For each enabled StructureType:
        → StructureLocator.findStructures(type, seed, radius, validator)
          → Compute candidate positions from seed math
          → Filter through BiomeValidator.isValidPosition()
        → Build MarkerSet + POIMarkers from surviving positions
        → Attach MarkerSet to matching BlueMap dimension maps
```

## The Core Algorithm

### Position Calculation (seed-based, pure math)

Every structure type uses a grid. The world is divided into regions of `spacing x spacing` chunks. Within each region, an RNG seeded deterministically from `(worldSeed, regionX, regionZ, salt)` picks the structure's chunk position.

```java
long regionSeed = (long)regionX * 341873128712L + (long)regionZ * 132897987541L + worldSeed + (long)salt;
java.util.Random rand = new java.util.Random(regionSeed);
```

**Linear spread** (most structures): `chunkX = regionX * spacing + rand.nextInt(spacing - separation)`

**Triangular spread** (Monument, Mansion, End City): `chunkX = regionX * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2`

This algorithm is verified correct against Minecraft's own `ChunkRandom.setRegionSeed()` — see `TestAgainstMinecraft.java`.

### Special Cases

**Nether Complexes**: Fortress and Bastion share one grid (spacing=27, sep=4, salt=30084232). After computing the position, the same RNG does a weight roll: `rand.nextInt(5) < 2` → Fortress, else Bastion.

**Strongholds**: Use concentric rings algorithm seeded from the world seed. Not grid-based. See `StrongholdLocator.java`.

### Biome Validation

Without biome checking, the position algorithm produces massive false positives (~80-90% for villages, since village grid positions land in oceans, mountains, etc.). Biome validation uses Minecraft's runtime to check whether each candidate position is in a valid biome.

**Approach**: `BiomeValidator` extracts the `BiomeSource` and `MultiNoiseSampler` from the `ServerWorld`'s chunk manager. This lets us query biomes at any coordinate from the seed alone — no chunk loading required, thread-safe.

```java
// In BiomeValidator constructor:
this.biomeSource = world.getChunkManager().getChunkGenerator().getBiomeSource();
this.noiseSampler = world.getChunkManager().getNoiseConfig().getMultiNoiseSampler();

// To check a position:
RegistryEntry<Biome> biome = biomeSource.getBiome(blockX >> 2, 64 >> 2, blockZ >> 2, noiseSampler);
biome.isIn(tagKey); // e.g. #minecraft:has_structure/village_plains
```

Each `StructureType` declares which `has_structure/*` biome tags it can spawn in. Multi-variant structures (villages, ocean ruins, shipwrecks) list all variant tags. Structures that spawn nearly everywhere (ruined portals, strongholds) return an empty list to skip biome checking.

**Biome tag names** (verified against [Minecraft Wiki](https://minecraft.wiki/w/Biome_tag_(Java_Edition))):
- Villages: `village_plains`, `village_desert`, `village_savanna`, `village_snowy`, `village_taiga`
- `desert_pyramid`, `jungle_temple`, `swamp_hut`, `igloo`, `pillager_outpost`
- `ancient_city`, `trail_ruins`, `trial_chambers`
- `ocean_ruin_cold`, `ocean_ruin_warm`, `shipwreck`, `shipwreck_beached`
- `ocean_monument`, `woodland_mansion`
- `nether_fortress`, `bastion_remnant`, `end_city`

## Structure Set Parameters (1.21.1)

| Structure | Spacing | Separation | Salt | Spread | Dimension |
|-----------|---------|------------|------|--------|-----------|
| Village | 34 | 8 | 10387312 | linear | overworld |
| Desert Pyramid | 32 | 8 | 14357617 | linear | overworld |
| Jungle Temple | 32 | 8 | 14357619 | linear | overworld |
| Swamp Hut | 32 | 8 | 14357620 | linear | overworld |
| Igloo | 32 | 8 | 14357618 | linear | overworld |
| Pillager Outpost | 32 | 8 | 165745296 | linear | overworld |
| Ocean Monument | 32 | 5 | 10387313 | triangular | overworld |
| Woodland Mansion | 80 | 20 | 10387319 | triangular | overworld |
| Ancient City | 24 | 8 | 20083232 | linear | overworld |
| Trail Ruins | 34 | 8 | 83469867 | linear | overworld |
| Trial Chambers | 34 | 12 | 94251327 | linear | overworld |
| Ocean Ruin | 20 | 8 | 14357621 | linear | overworld |
| Shipwreck | 24 | 4 | 165745295 | linear | overworld |
| Ruined Portal (OW) | 40 | 15 | 34222645 | linear | overworld |
| Nether Fortress | 27 | 4 | 30084232 | linear | nether |
| Bastion Remnant | 27 | 4 | 30084232 | linear | nether |
| Ruined Portal (Nether) | 25 | 10 | 34222645 | linear | nether |
| End City | 20 | 11 | 10387313 | triangular | end |
| Stronghold | — | — | — | concentric_rings | overworld |

**Excluded**: Buried Treasure (spacing=1, freq 0.01), Mineshaft (spacing=1, freq 0.004), Nether Fossils (spacing=2), Desert Well.

## BlueMap Integration

- One `MarkerSet` per structure type, toggleable in the BlueMap sidebar
- `POIMarker` at each position with default icons
- Dimension matching via `map.getWorld().getId()` string matching (`overworld`, `the_nether`, `the_end`)
- Markers are recreated on every `BlueMapAPI.onEnable()` (not persistent)

## Config (config/bluemap-structures.json)

- `radiusBlocks`: how far from origin to search (default 10000)
- `enabledStructures`: per-structure-type toggle (all true by default)
- Auto-migrates: new structure types added to existing configs

## Key Decisions

1. **Implemented structure algorithm ourselves** rather than using SeedFinding libs (stale, last updated ~1.18 era). The algorithm is ~50 lines and verified against MC's own classes.

2. **BiomeSource approach for biome validation** rather than `world.getBiome()`. The latter would force chunk loading/generation for unloaded positions. BiomeSource is a pure function of coordinates and noise — thread-safe and works for any position.

3. **Biome tags from StructureType enum** as simple string lists. Keeps the enum free of MC imports (pure Java) while BiomeValidator handles the MC-side TagKey conversion.

4. **Ruined portals and strongholds skip biome checking**. Ruined portals have 7 variant tags covering almost all biomes. Strongholds use concentric rings that don't do traditional biome-based placement.

5. **No Pillager Outpost frequency filter** yet. Outposts have `frequency: 0.2`, meaning only 20% of grid positions actually spawn one. Without this filter, we show ~5x too many outposts. Implementing the exact frequency check is non-trivial.

## Build & Test

```bash
./gradlew build                  # JAR at build/libs/bluemap-structures-1.0.0.jar
./gradlew testPositions          # Print structure positions for seed 12345 (no biome check)
./gradlew testVsMC               # Compare our RNG vs MC's ChunkRandom
```

## Current Status

- Position algorithm: verified correct for all structure types
- Biome validation: implemented, needs server testing
- BlueMap markers: working (tested on server prior to biome validation)
- Config system: working

## Known Limitations

1. **Pillager Outpost frequency**: ~5x false positives (frequency filter not implemented)
2. **Default POI icons**: All markers use the same icon
3. **Surface height checks**: Desert Pyramids, Jungle Temples, Woodland Mansions have height checks in vanilla that we don't replicate (~5% remaining false positives even with biome validation)
4. **BiomeValidator untested on server**: The `BiomeSource`/`NoiseConfig` API usage compiles but hasn't been runtime-tested yet. If `getNoiseConfig()` isn't accessible, fallback is `world.getBiome(BlockPos)`.
