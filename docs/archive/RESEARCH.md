# BlueMap Structure Markers — Research & Requirements

## Project Goal

Build a Fabric mod for Minecraft 1.21.11 that adds Chunkbase-style toggleable structure markers to BlueMap. Each structure type (Village, Stronghold, Desert Temple, etc.) should appear as its own toggleable MarkerSet in the BlueMap sidebar.

## Server Setup

- **Minecraft**: Java Edition 1.21.11
- **Mod Loader**: Fabric
- **Map**: BlueMap with 4 maps (Overworld, Nether, End, Nether Roof)
- **Hosting**: WiseHosting
- **World Seed**: Known (available server-side via `server.getOverworld().getSeed()`)

## Approach: Hybrid (Seed-Based + World Data)

### Seed-Based Calculation (Primary)

Predict structure positions mathematically from the world seed, giving full Chunkbase-like coverage of unexplored areas.

**Algorithm**: Minecraft divides the world into a grid per structure type. Each cell is `spacing x spacing` chunks. Within each cell, an RNG seeded with `(worldSeed, regionX, regionZ, salt)` picks a position. A biome check then validates whether the structure can actually spawn.

```
for each grid region (regionX, regionZ):
    rng = seed_rng(worldSeed, regionX, regionZ, structureSalt)
    chunkX = regionX * spacing + rng.nextInt(spacing - separation)
    chunkZ = regionZ * spacing + rng.nextInt(spacing - separation)
    if biome_is_valid(chunkX, chunkZ, structureType):
        structure spawns at (chunkX * 16, chunkZ * 16)
```

**Known limitation (post-1.18)**: Desert Pyramids, Jungle Temples, and Woodland Mansions have surface height checks that seed-based tools can't verify without full terrain simulation. These may produce false positives (~5% of cases).

### World Data Validation (Secondary)

Cross-reference seed predictions against actually-generated chunks to mark structures as "confirmed". Read `structure_starts` and `structure_references` from chunk NBT data in region (.mca) files.

---

## Key Libraries & Dependencies

### For Structure Finding

| Library | Language | License | Notes |
|---------|----------|---------|-------|
| **SeedFinding mc_feature_java** | Pure Java | MIT | Structure/feature placement. Best JVM fit. |
| **SeedFinding mc_biome_java** | Pure Java | MIT | Biome generation for validation step. |
| **SeedFinding mc_core_java** | Pure Java | MIT | Core RNG (`ChunkRand`, `setRegionSeed`). |
| **Cubiomes** | C | MIT | Gold standard but needs JNI. Not ideal. |

**Status concern**: SeedFinding libs last updated ~1.18-1.19 era. May need extension for 1.21 structures (Trial Chambers). The core algorithm hasn't changed — just need new spacing/separation/salt values.

**Alternative**: Implement the structure position algorithm directly. It's ~50 lines of code for step (a) (candidate positions). Biome validation (step b) is the complex part.

### For BlueMap Integration

- **BlueMapAPI** v2.7.x — `compileOnly` dependency
- Marker types: `POIMarker` (icon at point), `MarkerSet` (toggleable group)
- Markers are NOT persistent — must recreate in `BlueMapAPI.onEnable()` callback
- `MarkerGson` for JSON serialization/persistence

### For Reading World Data (MCA files)

- **BlueNBT** v1.3.0 — Platform-agnostic NBT parser from BlueMap team
- Can read `.mca` region files directly with `RandomAccessFile`
- BlueMapStructures (Paper plugin) already has working MCA parsing code that is platform-agnostic

---

## Structure Set Parameters (Vanilla 1.21)

Each structure type has known spacing, separation, and salt values defined in the vanilla data pack. Full data at [misode/mcmeta](https://github.com/misode/mcmeta).

Sample values:

| Structure | Spacing | Separation | Salt | Placement |
|-----------|---------|------------|------|-----------|
| Village | 34 | 8 | 10387312 | random_spread |
| Desert Pyramid | 32 | 8 | 14357617 | random_spread |
| Jungle Temple | 32 | 8 | 14357619 | random_spread |
| Swamp Hut | 32 | 8 | 14357620 | random_spread |
| Igloo | 32 | 8 | 14357618 | random_spread |
| Monument | 32 | 5 | 10387313 | random_spread |
| Mansion | 80 | 20 | 10387319 | random_spread |
| Pillager Outpost | 32 | 8 | 165745296 | random_spread |
| Ancient City | 24 | 8 | 20083232 | random_spread |
| Trail Ruins | 34 | 8 | 83469867 | random_spread |
| Trial Chambers | 32 | 8 | 94251327 | random_spread |
| Ocean Ruin | 20 | 8 | 14357621 | random_spread |
| Shipwreck | 24 | 4 | 165745295 | random_spread |
| Buried Treasure | 1 | 0 | 0 | random_spread (special) |
| Ruined Portal (OW) | 40 | 15 | 34222645 | random_spread |
| Nether Fortress | 27 | 4 | 30084232 | random_spread |
| Bastion Remnant | 27 | 4 | 30084232 | random_spread |
| Ruined Portal (Nether) | 25 | 10 | 34222645 | random_spread |
| End City | 20 | 11 | 10387313 | random_spread |
| Stronghold | — | — | — | concentric_rings (special) |

**Note**: These values MUST be verified against the actual 1.21.1 vanilla data pack. Some may have changed between versions.

---

## BlueMap Marker Integration

### MarkerSet per Structure Type

Each structure type gets its own `MarkerSet` with `toggleable: true`:

```java
BlueMapAPI.onEnable(api -> {
    MarkerSet villages = MarkerSet.builder()
        .label("Villages")
        .toggleable(true)
        .defaultHidden(false)
        .build();

    for (StructurePos pos : findVillages(seed, radius)) {
        POIMarker marker = POIMarker.builder()
            .label("Village")
            .position(pos.x, 64, pos.z)
            .icon("assets/bluemap-structures/village.png", 16, 16)
            .build();
        villages.getMarkers().put("village-" + pos.x + "-" + pos.z, marker);
    }

    for (BlueMapMap map : api.getMaps()) {
        // Only add to maps for the correct dimension
        map.getMarkerSets().put("villages", villages);
    }
});
```

### Marker Persistence

Since markers are wiped on BlueMap reload, the mod should:
1. Calculate structure positions on first load
2. Cache results to a JSON file using `MarkerGson`
3. On subsequent loads, read from cache (recalculate only if seed/radius changes)

### Dimension Mapping

Need to map BlueMap maps to Minecraft dimensions to add the right structures to the right maps. BlueMap map configs specify which world/dimension they render.

---

## Minecraft Server-Side Data Access (Fabric)

### Getting the World Seed
```java
long seed = server.getOverworld().getSeed();
```

### Accessing Generated Structures
```java
// Per-chunk structure data
Chunk chunk = world.getChunk(chunkX, chunkZ);
Map<Structure, StructureStart> starts = chunk.getStructureStarts();

// Structure registry
Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
```

### World Save Path
```java
// For reading .mca files directly
Path savePath = server.getSavePath(WorldSavePath.ROOT);
// Region files at: savePath/region/r.X.Z.mca
// Nether: savePath/DIM-1/region/r.X.Z.mca
// End: savePath/DIM1/region/r.X.Z.mca
```

---

## Reference Implementations

1. **BlueMapStructures** (TechnicJelle) — Paper plugin, reads .mca files. Core parsing code is platform-agnostic. [GitHub](https://github.com/TechnicJelle/BlueMapStructures)
2. **BlueMap-World-POI-Markers** (TheMcSebi) — Python script, cubiomes-viewer CSV to BlueMap markers. [GitHub](https://github.com/TheMcSebi/BlueMap-World-POI-Markers)
3. **SeedFinding mc_feature_java** — Pure Java structure finding. [GitHub](https://github.com/SeedFinding/mc_feature_java)
4. **Cubiomes** — C library, gold standard for accuracy. [GitHub](https://github.com/Cubitect/cubiomes)

---

## Open Questions Before Implementation

1. **SeedFinding libs viability for 1.21**: Do they work out of the box, or do we need to add Trial Chambers / update structure parameters? If they're too stale, we may want to implement the position algorithm ourselves (it's simple) and skip biome validation initially (accepting some false positives).

2. **Biome validation necessity**: How important is it to eliminate false positives? We could ship v1 with position-only calculation (step a) and add biome validation later. Most structures will be correct; only desert pyramids, jungle temples, and mansions have meaningful false-positive rates.

3. **Radius configuration**: How far out should we calculate? Options:
   - Fixed radius (e.g., 10,000 blocks) — simple
   - Match BlueMap's rendered area — adaptive
   - Configurable in mod config — flexible

4. **Performance**: Even with biome validation, calculating all structures within 10k blocks takes <1 second. Not a concern.

5. **Icons**: Need small PNG icons for each structure type. Could extract from Chunkbase, use Minecraft textures, or create custom ones.

6. **Confirmed vs Predicted markers**: Should we visually distinguish structures confirmed by world data vs seed-predicted? (e.g., different opacity, border, or icon style)
