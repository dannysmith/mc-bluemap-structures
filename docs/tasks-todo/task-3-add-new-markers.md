# Add New Structure Markers

## Type
Implementation task

## Summary
Add markers for buried treasure and end ships (end cities with ships). Each uses a different approach from the existing grid locator.

---

## Phase 1: Buried Treasure

### Algorithm (verified against cubiomes + vanilla data pack)

Buried treasure does NOT use the grid-based locator. Every chunk is independently checked with a 1% probability roll using `java.util.Random` (same as existing code).

```java
long seed = (long) chunkX * 341873128712L
          + (long) chunkZ * 132897987541L
          + worldSeed
          + 10387320L;
Random rand = new Random(seed);
if (rand.nextFloat() < 0.01f) {
    // treasure at block position (chunkX * 16 + 9, chunkZ * 16 + 9)
}
```

**Key details:**
- Salt is **10387320** — this comes from `legacy_type_2` frequency reduction (hardcoded), NOT from the structure set's `salt` field (which is 0)
- Block position is `chunkX * 16 + 9` for both X and Z (note: +9 not +8 like regular structures)
- The vanilla structure set has `spacing: 1, separation: 0, salt: 0, frequency: 0.01, frequency_reduction_method: legacy_type_2`
- Biome must be `beach` or `snowy_beach` — use tag `has_structure/buried_treasure`

### Implementation plan

Follow the `StrongholdLocator` pattern (separate class dispatched from `StructureLocator.findStructures()`):

1. **Add `BURIED_TREASURE` to `StructureType` enum**
   - `spacing=1, separation=0, salt=10387320` (bake the legacy_type_2 salt in)
   - `spreadType=LINEAR, dimension=OVERWORLD`
   - `maxDistance=300, defaultHidden=true` (dense like shipwrecks/ocean ruins)
   - `displayName="Buried Treasure"`, `iconFile="treasure.png"`, `markerSetId="structures-buried_treasure"`
   - `biomeTagIds=["has_structure/buried_treasure"]`

2. **Create `BuriedTreasureLocator.java`**
   - Static method: `findBuriedTreasure(long worldSeed, int radiusBlocks, BiomeValidator validator)`
   - Returns `List<StructurePos>`
   - Iterates all chunks in radius, applies RNG check, then biome validation on the ~1% that pass
   - Biome validation is cheap here since only ~1% of chunks need it

3. **Add dispatch in `StructureLocator.findStructures()`**
   - Early return for `BURIED_TREASURE` like Stronghold, but pass through the `BiomeValidator`

4. **Config**: automatically picked up by existing `enabledStructures` map (no changes needed)

### Performance

For the default 5000-block radius:
- `radiusChunks = 312`, total chunks checked = ~390k
- Only ~1% pass RNG (~3,900), and only those get biome-validated
- After biome filtering (beaches are a small fraction), expect a few hundred markers
- The inner loop is just seed computation + `new Random(seed)` + `nextFloat()` — should be fast

### Tests

Add to `StructureLocatorTest.java`:
- `known_buried_treasure_positions()` — regression guard with count + 2-3 known positions for seed 12345, radius 2000 (without biome filtering, i.e. validator=null)
- The existing parameterized `finds_results_within_radius` test will automatically cover `BURIED_TREASURE` once it's added to the enum

To generate known-good positions for tests: run the algorithm in a test with seed 12345, radius 2000, validator=null, and record the count + a few sample positions.

---

## Phase 2: End Ship Detection

- End Cities are already located by the grid algorithm
- Whether a city has a ship is deterministic from `(worldSeed, chunkX, chunkZ)` — no world data needed
- Requires porting the recursive piece-generation algorithm from [cubiomes](https://github.com/Cubitect/cubiomes) (`finders.c` lines ~2335-2600)
- The algorithm seeds RNG with `(nextLong(seed) * chunkX) ^ (nextLong(seed) * chunkZ) ^ worldSeed`, then recursively builds towers/bridges — each bridge has a `1/(10-depth)` chance of spawning a ship
- Collision detection between pieces affects which branches are taken, so bounding boxes need to be tracked
- ~50-60% of end cities have ships
- For our purposes we only need the boolean "has ship" result, but we still need the full piece generation logic to get accurate results
- End cities with ships use `end_ship.png`, without ships continue using `end_city.png`
- Implementation is ~200-300 lines of Java ported from cubiomes C code

---

## Icons needed
- `treasure.png` — exists, ready to use
- `end_ship.png` — exists, ready to use
