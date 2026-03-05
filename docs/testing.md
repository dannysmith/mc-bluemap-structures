# Testing

## Running Tests

```bash
./gradlew test           # run all tests
./gradlew build          # also runs tests (build depends on check)
```

Test output shows each test case with PASSED/FAILED status. Detailed failure output (full stack traces) is enabled via the `testLogging` config in `build.gradle`.

## Test Files

Tests live in `src/test/java/dev/danny/bluemapstructures/`.

### StructureLocatorTest

Tests the position algorithm in `StructureLocator` and `StrongholdLocator`.

- **`finds_results_within_radius`** — Parameterized across all 19 structure types. Verifies each type produces at least one result and all positions fall within the specified radius. Catches radius calculation bugs and missing structure type handling.

- **`fortress_and_bastion_never_share_position`** — Verifies the shared Nether grid correctly assigns each cell to exactly one of fortress or bastion. If the weight roll logic breaks, positions would appear in both lists.

- **`fortress_bastion_counts_match_expected_ratio`** — Sanity check that both fortresses and bastions are found (neither type is accidentally excluded).

- **`known_*_positions`** — Regression guards. Pin exact counts and specific positions for seed 12345, radius 2000. If the algorithm changes in any way — different RNG seeding, wrong constant, off-by-one — these will catch it. Covers village (linear), monument (triangular), fortress (shared grid), and stronghold (concentric rings).

### ChunkRandomVerificationTest

Verifies our RNG seeding matches Minecraft's `ChunkRandom.setRegionSeed()`.

- **`our_rng_matches_minecraft_chunk_random`** — Parameterized across a 5x5 region grid (-2 to +2 in both axes). For each region, computes the village chunk position using both our algorithm and Minecraft's actual `ChunkRandom` + `SpreadType.LINEAR`, then asserts they produce identical results.

This test uses Minecraft classes (`ChunkRandom`, `CheckedRandom`, `SpreadType`) which are available on the test classpath because Fabric Loom automatically includes the deobfuscated Minecraft JAR. No `fabric-loader-junit` or special test engine is needed.

## How the MC Classpath Works in Tests

Fabric Loom puts the deobfuscated, Yarn-mapped Minecraft JAR on `testRuntimeClasspath` automatically. This means tests can freely import and use Minecraft classes like `ChunkRandom`, `CheckedRandom`, and `SpreadType` — as long as those classes don't require Minecraft's bootstrap process or Mixin transformations.

The classes used in our tests (RNG utilities, spread type enum) are plain Java with no runtime dependencies, so they work in a standard JUnit context.

If you ever need to test code that depends on Mixin-transformed classes (e.g., accessor mixins into vanilla classes), you would need `fabric-loader-junit` instead of plain JUnit. That's not currently needed.

## Adding New Tests

### New structure type

If you add a new `StructureType` enum value, the parameterized `finds_results_within_radius` test will automatically include it. Consider also adding a `known_*_positions` regression test for it:

1. Run the algorithm for the new type with seed 12345, radius 2000
2. Note the count and a few representative positions
3. Add a test method pinning those values

### New algorithm feature

If you change the position calculation (e.g., adding the Pillager Outpost frequency filter), the regression tests will fail — that's by design. Update the pinned counts and positions to match the corrected output.

### Verifying against Chunkbase

The regression tests use seed 12345. You can cross-reference positions against [Chunkbase](https://www.chunkbase.com/apps/seed-map) with the same seed to verify correctness visually. Note that Chunkbase shows chunk coordinates, while our positions are block coordinates (chunk * 16 + 8).
