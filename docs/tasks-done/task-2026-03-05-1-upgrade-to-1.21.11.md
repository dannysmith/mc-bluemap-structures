# Upgrade Mod to Minecraft 1.21.11

## Why

The mod is compiled for MC 1.21.1 but the server runs 1.21.11. This causes runtime `NoSuchMethodError` crashes — the spawn marker feature is completely broken because `getSpawnPos()` was renamed in 1.21.11.

## Target Versions

| Component         | Current             | Target              |
|-------------------|---------------------|---------------------|
| Minecraft         | 1.21.1              | 1.21.11             |
| Yarn Mappings     | 1.21.1+build.3      | 1.21.11+build.4     |
| Fabric Loader     | 0.16.14             | 0.18.4              |
| Fabric API        | 0.115.0+1.21.1      | 0.141.3+1.21.11     |
| Fabric Loom       | 1.9-SNAPSHOT        | 1.15.4              |
| Gradle            | 8.12                | 9.2 (minimum for Loom 1.14+) |
| BlueMap API       | 2.7.2               | 2.7.2 (unchanged)   |
| JUnit Jupiter     | 5.10.3              | 5.10.3 (unchanged)  |
| Spotless           | 8.3.0               | 8.3.0 (unchanged)   |

## Research Findings

### Gradle/Loom compatibility (resolved)

The original task doc noted Gradle 9.2 wasn't available — this is no longer the case. Gradle 9.4.0 is the latest stable release. Loom 1.14+ requires Gradle 9.2 minimum. **Use Gradle 9.2** (the minimum that works) to avoid surprises from newer Gradle breaking changes.

### MC API changes (verified against Yarn 1.21.11+build.4 javadocs)

Only **one** breaking change affects our code:

**Spawn position API** (`BlueMapIntegration.java:166`):
- Old: `WorldProperties.getSpawnPos()` returns `BlockPos`
- New: `WorldProperties.getSpawnPoint()` returns `WorldProperties.SpawnPoint` record
- The `SpawnPoint` record has a `getPos()` method returning `BlockPos`
- Fix: `getSpawnPos()` → `getSpawnPoint().getPos()`

All other APIs we use are **confirmed unchanged** in Yarn 1.21.11+build.4:

| Method | Status |
|--------|--------|
| `MinecraftServer.getOverworld()` | Unchanged |
| `World.getLevelProperties()` | Unchanged |
| `MinecraftServer.getWorld(key)` | Unchanged |
| `ServerWorld.getChunkManager()` | Unchanged |
| `ChunkGenerator.getBiomeSource()` | Unchanged |
| `ServerChunkManager.getNoiseConfig()` | Unchanged |
| `NoiseConfig.getMultiNoiseSampler()` | Unchanged |
| `TagKey.of()` | Unchanged |
| `RegistryEntry.isIn()` | Unchanged |
| `BiomeSource.getBiome()` | Unchanged |
| `ServerWorld.getSeed()` | Unchanged |
| `World.OVERWORLD/NETHER/END` | Unchanged |
| `RegistryKeys.BIOME` | Unchanged |
| `Identifier.of()` | Unchanged |

### Note on Yarn's future

Yarn and Intermediary will **stop being updated after 1.21.11**. MC 26.1+ ships unobfuscated, making Yarn unnecessary. This doesn't affect us now but is worth knowing for any future upgrade.

## Files to Change

### Build configuration (Phase 1)
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.12 → 9.2
- `build.gradle` — Loom `1.9-SNAPSHOT` → `1.15`
- `gradle.properties` — MC version, yarn, loader, fabric API versions
- `src/main/resources/fabric.mod.json` — minecraft dep `~1.21.1` → `~1.21.11`

### Source code (Phase 2)
- `BlueMapIntegration.java:166` — `getSpawnPos()` → `getSpawnPoint().getPos()`

## Approach

### Phase 1: Upgrade Gradle wrapper

Update `gradle/wrapper/gradle-wrapper.properties` to Gradle 9.2:
```
distributionUrl=https\://services.gradle.org/distributions/gradle-9.2-bin.zip
```

Verify: `./gradlew --version` should show Gradle 9.2.

### Phase 2: Update build configuration

1. `build.gradle` — Change Loom version from `1.9-SNAPSHOT` to `1.15`
2. `gradle.properties` — Update all version properties:
   ```properties
   minecraft_version=1.21.11
   yarn_mappings=1.21.11+build.4
   loader_version=0.18.4
   fabric_version=0.141.3+1.21.11
   ```
3. `fabric.mod.json` — Update minecraft dependency:
   ```json
   "minecraft": "~1.21.11"
   ```
   And bump fabricloader minimum:
   ```json
   "fabricloader": ">=0.18.0"
   ```

### Phase 3: Fix source code

Single change in `BlueMapIntegration.java:166`:
```java
// Before:
BlockPos spawn = server.getOverworld().getLevelProperties().getSpawnPos();

// After:
BlockPos spawn = server.getOverworld().getLevelProperties().getSpawnPoint().getPos();
```

May also need to add an import for `WorldProperties.SpawnPoint` or adjust the import for `WorldProperties` — verify at compile time.

### Phase 4: Build and test

1. `./gradlew compileJava` — Verify everything compiles with new mappings. If any other API names changed that the research missed, fix them here.
2. `./gradlew test` — Tests don't use MC runtime APIs directly, should pass.
3. `./gradlew spotlessApply` — Fix any formatting issues from the code change.
4. `./gradlew build` — Full build including format check + JAR.

### Phase 5: Deploy and verify

1. Copy JAR to server
2. Verify in server logs: no `NoSuchMethodError`, spawn marker loads, all structure markers appear on BlueMap
