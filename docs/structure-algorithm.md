# Structure Position Algorithm

Reference for `StructureLocator.java`, `StrongholdLocator.java`, and `StructureType.java`.

## The Region Grid

Minecraft divides the world into a grid per structure type. Each grid cell is `spacing x spacing` chunks. Within each cell, an RNG seeded deterministically from the world seed, region coordinates, and a per-structure salt value picks exactly one candidate position.

Whether a structure actually spawns there depends on biome validation (handled separately by `BiomeValidator`).

## Region Seed Calculation

The seed for each grid cell is computed as:

```
regionSeed = regionX * 341873128712 + regionZ * 132897987541 + worldSeed + salt
```

The two large multipliers (`REGION_X_MULTIPLIER` and `REGION_Z_MULTIPLIER` in the code) are hardcoded constants from Minecraft's `ChunkRandom.setRegionSeed()`. They mix the region coordinates into the seed to produce a unique, deterministic value per grid cell.

This seeded `java.util.Random` then produces the chunk offset within the region.

## Spread Types

### Linear (most structures)

The chunk position within a region is a uniform random offset:

```
range = spacing - separation
chunkX = regionX * spacing + rand.nextInt(range)
chunkZ = regionZ * spacing + rand.nextInt(range)
```

`separation` creates a minimum gap between adjacent regions' structures by reducing the range of valid positions within each cell.

### Triangular (Monument, Mansion, End City)

Uses the average of two random values, creating a distribution biased toward the centre of the region:

```
chunkX = regionX * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2
chunkZ = regionZ * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2
```

This is the same technique used to approximate a triangular distribution — structures cluster more toward the centre of each grid cell rather than being uniformly distributed.

## Special Cases

### Nether Fortress and Bastion (Shared Grid)

Fortress and Bastion share identical grid parameters (spacing=27, separation=4, salt=30084232). Every grid cell produces exactly one structure, with a weight roll determining which type.

Since MC 1.18, the weight roll uses a **fresh RNG** seeded from the output chunk position (carver seed), not the same RNG used for position calculation:

```
// Position RNG (region seed)
regionSeed = regionX * 341873128712 + regionZ * 132897987541 + worldSeed + salt
chunkX/Z = computed from regionSeed RNG

// Weight RNG (carver seed) — separate from position RNG
Random(worldSeed) → a = nextLong(), b = nextLong()
carverSeed = (a * chunkX) ^ (b * chunkZ) ^ worldSeed
roll = new Random(carverSeed).nextInt(5)
if roll < 2 → Nether Fortress (40%)
else        → Bastion Remnant (60%)
```

This is handled by `StructureLocator.findNetherComplex()`, which iterates the shared grid once and filters by the requested type.

### Strongholds (Concentric Rings)

Strongholds don't use the region grid at all. They use a concentric rings algorithm seeded directly from the world seed. See `StrongholdLocator.java`.

**At runtime**, the mod reads pre-computed stronghold positions directly from vanilla's `StructurePlacementCalculator.getPlacementPositions()`. These positions include biome snapping (vanilla shifts geometric positions to valid biomes within a 112-block radius), so they exactly match in-game locations.

**Geometric fallback** (used in tests where no `ServerWorld` is available): places 128 strongholds in expanding rings around the origin:
- Starts with 3 strongholds in the first ring at ~2048 blocks from origin
- Each subsequent ring has more strongholds at greater distance
- Within each ring, strongholds are evenly spaced with a random angular offset
- Distance from origin varies randomly within each ring

The geometric fallback is ~300 blocks off from actual positions because it skips biome snapping.

## Structure Parameters

Each structure type in `StructureType.java` defines these values, sourced from the vanilla 1.21.1 data pack:

| Structure | Spacing | Separation | Salt | Spread |
|-----------|---------|------------|------|--------|
| Village | 34 | 8 | 10387312 | linear |
| Desert Pyramid | 32 | 8 | 14357617 | linear |
| Jungle Temple | 32 | 8 | 14357619 | linear |
| Swamp Hut | 32 | 8 | 14357620 | linear |
| Igloo | 32 | 8 | 14357618 | linear |
| Pillager Outpost | 32 | 8 | 165745296 | linear |
| Ancient City | 24 | 8 | 20083232 | linear |
| Trail Ruins | 34 | 8 | 83469867 | linear |
| Trial Chambers | 34 | 12 | 94251327 | linear |
| Ocean Ruin | 20 | 8 | 14357621 | linear |
| Shipwreck | 24 | 4 | 165745295 | linear |
| Ruined Portal (OW) | 40 | 15 | 34222645 | linear |
| Ocean Monument | 32 | 5 | 10387313 | triangular |
| Woodland Mansion | 80 | 20 | 10387319 | triangular |
| Nether Fortress | 27 | 4 | 30084232 | linear |
| Bastion Remnant | 27 | 4 | 30084232 | linear |
| Ruined Portal (Nether) | 40 | 15 | 34222645 | linear |
| End City | 20 | 11 | 10387313 | triangular |
| Stronghold | n/a | n/a | n/a | concentric_rings |

These values must match the vanilla data pack for the targeted MC version (1.21.11). If updating to a new MC version, verify against `data/minecraft/worldgen/structure_set/` in the vanilla data pack (available at [misode/mcmeta](https://github.com/misode/mcmeta) — use the version tag, not the `data` branch which tracks snapshots).

Note: Ruined Portal uses the same parameters for both Overworld and Nether — vanilla has a single `ruined_portals` structure set.

## Biome Validation

Each structure type declares which `has_structure/*` biome tags it can spawn in (defined in `StructureType.biomeTagIds()`). `BiomeValidator` converts these to `TagKey<Biome>` objects and checks the biome at each candidate position using the world's `BiomeSource`.

Some structures skip biome validation:
- **Ruined Portals** spawn in nearly all biomes (7+ variant tags)
- **Strongholds** use concentric rings placement, not biome-based

Biome coordinates are downsampled from block coordinates by shifting right 2 bits (4x4 block grid), matching Minecraft's internal biome resolution.
