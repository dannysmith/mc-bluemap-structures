# BlueMap Structure Markers — Implementation Plan

> This file contains everything needed to build the mod from scratch in a fresh session.
> Run `/prime-context` or just read this file + RESEARCH.md to get started.

## What We're Building

A Fabric mod for Minecraft 1.21.11 that adds Chunkbase-style toggleable structure markers to BlueMap. Each structure type gets its own toggleable MarkerSet in the BlueMap sidebar. No external dependencies beyond Fabric API and BlueMapAPI.

## Architecture

```
mc-bluemap-structures/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/main/java/dev/danny/bluemapstructures/
│   ├── BlueMapStructuresMod.java    # Fabric ModInitializer entrypoint
│   ├── StructureType.java           # Enum: all structures with spacing/separation/salt/dimension
│   ├── StructureLocator.java        # Seed-based position algorithm (random_spread + nether complexes)
│   ├── StrongholdLocator.java       # Concentric rings algorithm (strongholds only)
│   ├── BlueMapIntegration.java      # Creates MarkerSets + POIMarkers per structure type
│   └── ModConfig.java               # JSON config (radius, enabled structures)
├── src/main/resources/
│   ├── fabric.mod.json
│   └── assets/bluemapstructures/icon.png
└── RESEARCH.md
```

## Toolchain Versions

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.18.1 |
| Fabric API | 0.141.3+1.21.11 |
| Yarn Mappings | 1.21.11+build.1 |
| Fabric Loom | 1.14 |
| BlueMapAPI | 2.7.2 (compileOnly) |
| Java | 21 |

> **Note**: These version numbers were researched but not build-tested. If Gradle can't resolve a dependency, check:
> - Fabric API versions at https://modrinth.com/mod/fabric-api/versions
> - Yarn mappings at https://maven.fabricmc.net/net/fabricmc/yarn/
> - BlueMapAPI at https://repo.bluecolored.de/releases/de/bluecolored/bluemap-api/

## The Core Algorithm

### Region Seed Formula (same across all MC versions)
```java
long regionSeed = (long)regionX * 341873128712L + (long)regionZ * 132897987541L + worldSeed + (long)salt;
java.util.Random rand = new java.util.Random(regionSeed);
```

### Linear Spread (most structures)
```java
int offset = spacing - separation;
int chunkX = regionX * spacing + rand.nextInt(offset);
int chunkZ = regionZ * spacing + rand.nextInt(offset);
```

### Triangular Spread (Monument, Mansion, End City)
```java
int range = spacing - separation;
int chunkX = regionX * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2;
int chunkZ = regionZ * spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2;
```

### Region Coordinate (handles negative)
```java
int regionX = Math.floorDiv(chunkX, spacing);
```

### Nether Complexes (Fortress/Bastion)
They share spacing=27, separation=4, salt=30084232. After computing position (which consumes 2 or 4 nextInt calls depending on spread type), use the SAME Random to pick:
```java
int roll = rand.nextInt(5); // total weight = 2 (fortress) + 3 (bastion)
boolean isFortress = roll < 2;
```
**Important**: Test this against Chunkbase for the actual seed. The exact number of nextInt calls before the weight roll matters. If positions are right but fortress/bastion are swapped, the weight roll offset is wrong.

### Stronghold Concentric Rings
Vanilla params: count=128, distance=32, spread=3. Algorithm:
```java
Random rand = new Random(seed); // world seed
int ringCount = spread; // 3
double ringDistance = 6.0 * distance; // 192 chunks
double angle = rand.nextDouble() * Math.PI * 2;
int placed = 0;
int ring = 0;
for (int i = 0; i < count; i++) {
    double dist = (4.0 * ringDistance + ringDistance * ring * 6.0) + (rand.nextDouble() - 0.5) * ringDistance * 2.5;
    int chunkX = (int)Math.round(Math.cos(angle) * dist);
    int chunkZ = (int)Math.round(Math.sin(angle) * dist);
    // blockX = chunkX * 16 + 8, blockZ = chunkZ * 16 + 8
    angle += Math.PI * 2.0 / ringCount;
    placed++;
    if (placed == ringCount) {
        ring++;
        placed = 0;
        ringCount += 2 * ringCount / (ring + 1);
        if (ringCount > count - i - 1) ringCount = count - i - 1;
        angle += rand.nextDouble() * Math.PI * 2.0;
    }
}
```
**Note**: This pseudocode is approximate. Verify against Chunkbase output.

### Pillager Outpost Frequency (0.2)
After computing the candidate position, apply frequency reduction:
```java
// The frequency reduction method "default" seeds from the candidate position
long freqSeed = chunkX * 341873128712L + chunkZ * 132897987541L + worldSeed + salt;
// ... exact seeding TBD, test against Chunkbase
```
If this is too complex to get right initially, just skip the frequency check and note that outposts will have 5x false positives. Can be fixed later.

## Structure Set Parameters (1.21.x)

| Structure | Spacing | Separation | Salt | Spread | Dimension | Frequency | Notes |
|-----------|---------|------------|------|--------|-----------|-----------|-------|
| Village | 34 | 8 | 10387312 | linear | overworld | 1.0 | |
| Desert Pyramid | 32 | 8 | 14357617 | linear | overworld | 1.0 | |
| Jungle Temple | 32 | 8 | 14357619 | linear | overworld | 1.0 | |
| Swamp Hut | 32 | 8 | 14357620 | linear | overworld | 1.0 | |
| Igloo | 32 | 8 | 14357618 | linear | overworld | 1.0 | |
| Pillager Outpost | 32 | 8 | 165745296 | linear | overworld | 0.2 | exclusion_zone near villages |
| Ocean Monument | 32 | 5 | 10387313 | triangular | overworld | 1.0 | |
| Woodland Mansion | 80 | 20 | 10387319 | triangular | overworld | 1.0 | |
| Ancient City | 24 | 8 | 20083232 | linear | overworld | 1.0 | |
| Trail Ruins | 34 | 8 | 83469867 | linear | overworld | 1.0 | |
| Trial Chambers | 34 | 12 | 94251327 | linear | overworld | 1.0 | |
| Ocean Ruin | 20 | 8 | 14357621 | linear | overworld | 1.0 | |
| Shipwreck | 24 | 4 | 165745295 | linear | overworld | 1.0 | |
| Ruined Portal (OW) | 40 | 15 | 34222645 | linear | overworld | 1.0 | |
| Nether Fortress | 27 | 4 | 30084232 | linear | nether | 1.0 | Shares grid with Bastion (weight 2) |
| Bastion Remnant | 27 | 4 | 30084232 | linear | nether | 1.0 | Shares grid with Fortress (weight 3) |
| Ruined Portal (Nether) | 25 | 10 | 34222645 | linear | nether | 1.0 | |
| End City | 20 | 11 | 10387313 | triangular | end | 1.0 | |
| Stronghold | — | — | — | concentric_rings | overworld | — | count=128, distance=32, spread=3 |

**Excluded (v1)**: Buried Treasure (spacing=1, freq 0.01), Mineshaft (spacing=1, freq 0.004), Nether Fossils (spacing=2), Desert Well.

## BlueMap Integration

### API Pattern
```java
BlueMapAPI.onEnable(api -> {
    // Create one MarkerSet per structure type
    MarkerSet set = MarkerSet.builder()
        .label("Villages").toggleable(true).defaultHidden(false).build();

    // Add POI markers
    POIMarker marker = POIMarker.builder()
        .label("Village").position(x, 64, z).maxDistance(10000).build();
    set.getMarkers().put("village_x_z", marker);

    // Attach to the right maps
    for (BlueMapMap map : api.getMaps()) {
        String worldId = map.getWorld().getId();
        if (worldId.contains("overworld")) {
            map.getMarkerSets().put("structures-village", set);
        }
    }
});
```

### Dimension Matching
- `worldId.contains("overworld")` → Overworld structures
- `worldId.contains("the_nether")` → Nether structures (includes Nether Roof map)
- `worldId.contains("the_end")` → End structures

### Lifecycle
- Register `BlueMapAPI.onEnable()` inside `ServerLifecycleEvents.SERVER_STARTED`
- Markers are NOT persistent — recreate on every BlueMap enable
- `BlueMapAPI.onDisable()` for cleanup (markers auto-cleared)

## Fabric Mod Setup

### Key APIs
- `ServerLifecycleEvents.SERVER_STARTED` → get MinecraftServer reference
- `server.getOverworld().getSeed()` → world seed
- `server.getSavePath(WorldSavePath.ROOT)` → world save dir (for future MCA reading)

### fabric.mod.json
```json
{
    "schemaVersion": 1,
    "id": "bluemap-structures",
    "version": "1.0.0",
    "name": "BlueMap Structure Markers",
    "description": "Adds Chunkbase-style toggleable structure markers to BlueMap",
    "environment": "server",
    "entrypoints": {
        "main": ["dev.danny.bluemapstructures.BlueMapStructuresMod"]
    },
    "depends": {
        "fabricloader": ">=0.18.0",
        "fabric-api": "*",
        "minecraft": "~1.21.11"
    },
    "suggests": {
        "bluemap": ">=5.15"
    }
}
```

## Config File (config/bluemap-structures.json)
```json
{
    "radiusBlocks": 10000,
    "enabledStructures": {
        "VILLAGE": true,
        "DESERT_PYRAMID": true,
        "JUNGLE_TEMPLE": true,
        "SWAMP_HUT": true,
        "IGLOO": true,
        "PILLAGER_OUTPOST": true,
        "MONUMENT": true,
        "MANSION": true,
        "ANCIENT_CITY": true,
        "TRAIL_RUINS": true,
        "TRIAL_CHAMBERS": true,
        "OCEAN_RUIN": true,
        "SHIPWRECK": true,
        "RUINED_PORTAL_OW": true,
        "FORTRESS": true,
        "BASTION": true,
        "RUINED_PORTAL_NETHER": true,
        "END_CITY": true,
        "STRONGHOLD": true
    }
}
```

## Known Limitations (v1)

1. **No biome validation**: ~5% false positives for Desert Pyramids, Jungle Temples, Woodland Mansions
2. **Default POI icons**: All markers use the same icon. Custom icons are a future enhancement.
3. **Pilager Outpost frequency**: May need to skip the 0.2 frequency check initially (5x false positives for outposts)
4. **Nether Complexes weight roll**: Exact fortress/bastion determination needs testing against Chunkbase

## Verification

1. `./gradlew build` → produces JAR in `build/libs/`
2. Compare positions with Chunkbase for the world seed
3. Deploy to server alongside BlueMap, verify markers + toggles work
4. Test all three dimensions + Nether Roof map
