# Upgrade Mod to Minecraft 1.21.11

## Why

The mod is compiled for MC 1.21.1 but the server runs 1.21.11. This causes runtime `NoSuchMethodError` crashes — the spawn marker feature is completely broken because `getSpawnPos()` was removed in 1.21.11.

## Target Versions

| Component         | Current             | Target              |
|-------------------|---------------------|---------------------|
| Minecraft         | 1.21.1              | 1.21.11             |
| Yarn Mappings     | 1.21.1+build.3      | 1.21.11+build.4     |
| Fabric Loader     | 0.16.14             | 0.18.4              |
| Fabric API        | 0.115.0+1.21.1      | 0.141.3+1.21.11     |
| Fabric Loom       | 1.9-SNAPSHOT        | 1.15.4              |
| Gradle            | 8.12                | 9.2+ (required by Loom 1.15.4) |
| BlueMap API       | 2.7.2               | 2.7.2 (unchanged)   |

## Gradle/Loom Upgrade Chicken-and-Egg Problem

Loom 1.15.4 requires Gradle with `plugin.api-version` of `9.2.0`. Gradle 9.0 exists but is not new enough. Gradle 9.1 and 9.2 didn't resolve from services.gradle.org during testing. Options:

1. Check if a newer Gradle 9.x is available now (9.2 may have released since)
2. Find a Loom version compatible with Gradle 9.0 (e.g. try 1.14.x or 1.13.x)
3. Manually edit `gradle-wrapper.properties` to point at the right Gradle dist URL if the wrapper task can't validate it

The recommended approach: start by figuring out which Loom + Gradle combo works, update the wrapper first (temporarily reverting MC version if needed), then update everything else.

## Files to Update

- `gradle.properties` — MC version, yarn, loader, fabric API versions
- `build.gradle` — Loom plugin version
- `gradle/wrapper/gradle-wrapper.properties` — Gradle distribution version
- `src/main/resources/fabric.mod.json` — minecraft dependency constraint (`~1.21.1` -> `~1.21.11`)

## MC API Changes (1.21.1 -> 1.21.11)

1.21.11 uses Mojang mappings by default, so many Yarn names changed. With Yarn 1.21.11+build.4, the names may differ from what's listed below — verify after Loom resolves the mappings. The intermediary method names are stable, so the key thing is whether methods still exist.

### Confirmed broken: Spawn position

- `World.getSpawnPos()` / `WorldProperties.getSpawnPos()` — **removed**
- Replaced by `LevelData.getRespawnData()` which returns a `RespawnData` record with a `.pos()` method
- Affected file: `BlueMapIntegration.java:166`

### Likely renames (verify after build compiles)

These were identified by comparing 1.21.1 Yarn mappings to 1.21.11 layered mappings. The Yarn names in 1.21.11 may differ:

| Used in code as (1.21.1 Yarn) | File | Notes |
|-------------------------------|------|-------|
| `getOverworld()` | BlueMapStructuresMod, BlueMapIntegration | May rename |
| `getLevelProperties()` | BlueMapIntegration | May rename |
| `getWorld(key)` | BlueMapIntegration | May rename |
| `getChunkManager()` | BiomeValidator | May rename |
| `getChunkGenerator()` | BiomeValidator | May rename |
| `getNoiseConfig()` | BiomeValidator | May rename |
| `getMultiNoiseSampler()` | BiomeValidator | May rename |
| `TagKey.of()` | BiomeValidator | May rename to `TagKey.create()` |
| `RegistryEntry.isIn()` | BiomeValidator | May rename to `is()` |
| `BiomeSource.getBiome()` | BiomeValidator | May rename to `getNoiseBiome()` |

### Likely unchanged

- `ServerWorld.getSeed()`, `BlockPos` methods, `World.OVERWORLD/NETHER/END` constants, `RegistryKeys.BIOME`, `Identifier.of()`

## Approach

1. Get Gradle + Loom versions sorted first (this is the main blocker)
2. Update `gradle.properties` and `fabric.mod.json`
3. Run `./gradlew compileJava` and fix compile errors one by one
4. Run tests (`./gradlew test`) — the tests don't use MC runtime APIs so they should mostly pass
5. Run `./gradlew build` for full build + format check
6. Deploy to bmdev and verify spawn marker + all structure markers load without errors
