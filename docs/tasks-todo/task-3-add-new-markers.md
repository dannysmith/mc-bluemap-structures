# Add New Structure Markers

## Type
Implementation task

## Summary
Add markers for spawn point, buried treasure, and end ships (end cities with ships). Each phase uses a different approach. Icons for all four exist in `src/main/resources/icons/` but ancient city and end ship icons still need to be created/sourced (see Phase 1 notes).

---

## Phase 1: Spawn Point + Ancient City Icon

### Spawn
- Read spawn position directly from the server API — no algorithm needed
- `server.getOverworld().getSpawnPos()` returns the exact spawn point
- Available by the time `SERVER_STARTED` fires (which we already hook)
- Create a single POI marker, not a full MarkerSet — there's only one spawn per world
- Icon: `spawn.png`

### Ancient City Icon
- Ancient Cities are already fully implemented in `StructureType.java` (spacing=24, separation=8, salt=20083232, LINEAR spread) — confirmed correct against vanilla data
- Just need to source/create the `ancient_city.png` icon — the current one needs replacing

---

## Phase 2: Buried Treasure

- Seed-deterministic but uses a **different algorithm** from the grid locator in `StructureLocator.java`
- Every chunk is checked individually (spacing=1, separation=0) with a 1% probability roll
- RNG seeding: `chunkX * 341873128712L + chunkZ * 132897987541L + worldSeed + 10387320L`
- If `nextFloat() < 0.01`, treasure generates at fixed block position `(chunkX*16 + 9, y, chunkZ*16 + 9)`
- Biome must be Beach or Snowy Beach
- The per-chunk iteration makes this more expensive at large radii than grid-based structures — may need a sensible default radius
- Will need a new locator method (not `StructureLocator.getStructureChunk()`) or a separate locator class
- Icon: `treasure.png`

---

## Phase 3: End Ship Detection

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
- `spawn.png` — exists, ready to use
- `ancient_city.png` — exists but needs replacing with correct icon
- `treasure.png` — exists, ready to use
- `end_ship.png` — exists, ready to use
