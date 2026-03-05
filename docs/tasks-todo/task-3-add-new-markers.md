# Add New Structure Markers

## Type
Implementation task

## Summary
Add markers for buried treasure and end ships (end cities with ships). Each uses a different approach from the existing grid locator.

---

## Phase 1: Buried Treasure

- Seed-deterministic but uses a **different algorithm** from the grid locator in `StructureLocator.java`
- Every chunk is checked individually (spacing=1, separation=0) with a 1% probability roll
- RNG seeding: `chunkX * 341873128712L + chunkZ * 132897987541L + worldSeed + 10387320L`
- If `nextFloat() < 0.01`, treasure generates at fixed block position `(chunkX*16 + 9, y, chunkZ*16 + 9)`
- Biome must be Beach or Snowy Beach
- The per-chunk iteration makes this more expensive at large radii than grid-based structures — may need a sensible default radius
- Will need a new locator method (not `StructureLocator.getStructureChunk()`) or a separate locator class
- Icon: `treasure.png`

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
