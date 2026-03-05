# BlueMap Structure Markers — Code Review

## 1. High-Level Approach

**Verdict: Sound.** The overall architecture — a Fabric server-side mod that calculates structure positions from the world seed and pushes them to BlueMap as toggleable MarkerSets — is the right approach. It's the same strategy Chunkbase uses (seed math), wrapped in a server-side mod that can also validate against Minecraft's biome data at runtime.

The code is well-structured: pure-Java seed math (`StructureLocator`, `StrongholdLocator`), a clean Minecraft-dependent layer (`BiomeValidator`), and a BlueMap integration layer (`BlueMapIntegration`). The separation means the core algorithm is testable standalone, which you've already leveraged with `TestPositions` and `TestAgainstMinecraft`.

The decision to skip external libraries (SeedFinding, Cubiomes) in favour of implementing the ~50-line algorithm directly was correct. Those libs are stale and the algorithm is simple enough to own.

**One scope note:** The original RESEARCH.md described a "Hybrid (Seed-Based + World Data)" approach — reading `.mca` files to confirm generated structures. The implementation is purely seed-based. This is fine for v1 and noted in PLAN.md, but worth keeping in mind.

---

## 2. Structure Location Finding

### 2a. Seed-Based Position Algorithm (StructureLocator)

**Verdict: Correct for all standard structures.**

The region seed formula matches Minecraft's `ChunkRandom.setRegionSeed()`:
```java
long regionSeed = (long)regionX * 341873128712L + (long)regionZ * 132897987541L + worldSeed + (long)salt;
```

Using `java.util.Random` is valid — Minecraft's `CheckedRandom` uses the same 48-bit LCG with identical multiplier and seed scrambling (`seed ^ 0x5DEECE66DL`). There's a known Minecraft bug (MC-239059) where `CheckedRandom` deviates slightly from `java.util.Random`, but it's documented as having negligible impact and your `TestAgainstMinecraft.java` directly compares the two implementations. If they matched in testing, you're fine.

LINEAR and TRIANGULAR spread types are implemented correctly. The `spacing`, `separation`, and `salt` values match the vanilla 1.21.1 data pack.

### 2b. Nether Fortress / Bastion Complex

**Verdict: Correct.**

The shared-grid approach is right: one position per region, then a weight roll on the same RNG instance. The vanilla `nether_complexes` structure set lists fortress first (weight 2), bastion second (weight 3). The code's `rand.nextInt(5) < 2 → fortress` matches the vanilla weighted selection exactly. The RNG state after 2 `nextInt(range)` calls (X and Z position) is correct for the weight roll being the 3rd call.

### 2c. Stronghold Algorithm (StrongholdLocator)

**Verdict: BUG — strongholds are placed at ~6x the correct distance.**

The distance formula has an error. The code computes:

```java
double ringDistance = 6.0 * DISTANCE;  // = 6.0 * 32 = 192
double dist = (4.0 * ringDistance + ringDistance * ring * 6.0)
        + (rand.nextDouble() - 0.5) * ringDistance * 2.5;
```

This expands to: `dist = 768 + 1152 * ring + random * 480` (in chunks)

The vanilla formula (confirmed against Cubiomes and Minecraft's `ConcentricRingsStructurePlacement`) uses `DISTANCE` directly, not `6 * DISTANCE`:

```java
double dist = (4.0 * DISTANCE + DISTANCE * ring * 6.0)
        + (rand.nextDouble() - 0.5) * DISTANCE * 2.5;
```

Which gives: `dist = 128 + 192 * ring + random * 80` (in chunks)

**Impact:** The first ring of strongholds would appear at ~12,288 blocks from origin instead of the correct ~2,048 blocks. All strongholds are 6x too far out. With a default radius of 10,000 blocks, most strongholds in the first ring would actually fall outside the search radius and not appear at all. The `ringDistance` variable is the culprit — it pre-multiplies by 6, and then the formula multiplies by 6 again.

**Fix:** Replace the distance calculation:
```java
double dist = (4.0 * DISTANCE + (double) DISTANCE * ring * 6.0)
        + (rand.nextDouble() - 0.5) * (double) DISTANCE * 2.5;
```
The `ringDistance` variable can be removed entirely.

### 2d. Biome Validation (BiomeValidator)

**Verdict: Approach is correct, should work at runtime.**

Using `BiomeSource.getBiome()` with the `MultiNoiseSampler` is the right way to query biomes without loading chunks. It's a pure mathematical computation (noise evaluation) that works for any coordinate and is thread-safe.

The fixed Y=64 for biome queries is reasonable — vanilla structure placement also checks at a fixed Y for most structures. Underground structures (Ancient City, Trial Chambers) might have slightly different biome at their actual Y level, but this matches how most seed-finding tools work.

The biome tag approach (`has_structure/village_plains` etc.) is clean and correct for 1.21.1. `Identifier.of("minecraft", "has_structure/village_plains")` is valid — forward slashes are allowed in identifier paths.

---

## 3. The Mod Itself (Fabric Integration)

**Verdict: Will run. Two concerns below.**

The Fabric entrypoint is minimal and correct: hook `SERVER_STARTED`, grab the seed, load config, hand off to `BlueMapIntegration`. The `ModConfig` is straightforward with sensible auto-migration for new structure types.

**Concern — Listener accumulation:** `BlueMapAPI.onEnable()` adds a listener to a list — it doesn't replace. The mod registers a new listener every time `SERVER_STARTED` fires. On a dedicated server (one start per JVM lifetime), this is fine. But if the JVM persists across server restarts (some hosting panels, integrated server), listeners accumulate and markers get duplicated.

**Concern — No `onDisable` handler:** When BlueMap reloads (`/bluemap reload`), `onDisable` fires, markers are wiped, then `onEnable` fires and the callback recreates them. This flow works. But without an explicit `onDisable` handler, there's no cleanup of references. More importantly, if you later want to persist markers or track state, you'd need this. For v1 it's not a problem since markers are stateless and recreated from scratch.

---

## 4. BlueMap Extension

**Verdict: API usage is correct. One uncertainty around dimension matching.**

The `MarkerSet.builder()` API with `.label()`, `.toggleable()`, `.defaultHidden()` is correct for BlueMapAPI 2.7.x. `POIMarker.builder()` with `.position(double, double, double)`, `.label()`, `.detail()`, `.maxDistance()` is correct and uses the non-deprecated overload.

The pattern of creating MarkerSets, adding POIMarkers, and attaching to maps via `map.getMarkerSets().put()` is the standard approach used by other BlueMap addons.

**Dimension matching uncertainty:** The code does:
```java
String worldId = map.getWorld().getId();
if (worldId.contains("overworld")) { ... }
```

`BlueMapWorld.getId()` returns a string identifier, but its exact format depends on BlueMap's internal representation. For vanilla servers, it typically contains the dimension path (e.g., `minecraft:overworld` or a path-based ID). The `.contains()` approach should work for standard setups since "overworld", "the_nether", and "the_end" will appear as substrings. But for non-standard BlueMap configurations (custom world names, modded dimensions), this could misfire.

**Recommendation:** Log the world IDs at startup so you can verify what BlueMap actually returns on your server. If they don't match, adjust the matching logic.

---

## 5. Minor / Housekeeping

- **`TestPositions.java` and `TestAgainstMinecraft.java`**: Useful for development but won't run as Gradle tasks on a server (they need MC classes on the classpath). The custom Gradle tasks (`testPositions`, `testVsMC`) use `sourceSets.main.runtimeClasspath`, which should work in a dev environment with Loom. Fine to keep.

- **Missing icon file**: PLAN.md's architecture diagram lists `assets/bluemapstructures/icon.png` but it doesn't exist. The code doesn't reference any custom icons — POIMarkers use BlueMap's default icon. No issue, just a stale reference in the plan.

- **Pillager Outpost false positives**: Acknowledged in PLAN.md. Outposts have a `frequency: 0.2` filter that isn't implemented, so the mod shows ~5x too many outposts. This is a known limitation, not a bug.

- **`fabric.mod.json`**: Declares `"environment": "server"` which is correct. The `"suggests": { "bluemap": ">=5.0" }` is a soft dependency — the mod won't crash without BlueMap, it just won't do anything (the `onEnable` callback never fires).

- **No extraneous files or dead code**: The codebase is lean. Every file has a purpose.

---

## Summary of Issues by Severity

| Severity | Issue | Location |
|----------|-------|----------|
| **BUG** | Stronghold distance formula produces positions ~6x too far from origin | `StrongholdLocator.java:26-27` |
| **Moderate** | No `BlueMapAPI.onDisable()` cleanup — could cause duplicate markers on BlueMap reload in edge cases | `BlueMapIntegration.java` |
| **Moderate** | `onEnable` listener registered per server start — accumulates if JVM persists across restarts | `BlueMapIntegration.java:18` |
| **Low** | Dimension matching via `.contains()` on world ID — works for vanilla, could misfire with custom configs | `BlueMapIntegration.java:66` |
| **Known** | Pillager Outpost ~5x false positives (frequency filter not implemented) | `StructureLocator.java` |
| **Known** | All markers use default BlueMap POI icon (no custom structure icons) | `BlueMapIntegration.java:51-56` |

**Bottom line:** Fix the stronghold formula before deploying. Everything else should work on a standard Fabric + BlueMap server. The seed-based position algorithm for all non-stronghold structures is verified correct.
