# Add New Structure Markers

## Type
Implementation task

## Summary
Add markers for buried treasure and end ships (end cities with ships). Each uses a different approach from the existing grid locator.

---

## Phase 1: Buried Treasure — DONE

Implemented in commit `2435a9d`. Added `BURIED_TREASURE` enum value, `BuriedTreasureLocator.java` (per-chunk 1% RNG check with legacy_type_2 salt), dispatch in `StructureLocator`, and regression test (619 results for seed 12345, radius 2000).

---

## Phase 2: End Ship Detection

### Concept

Not a new marker type. End Cities are already located by the grid algorithm. This phase adds a `hasShip(worldSeed, chunkX, chunkZ)` check and swaps the icon from `end_city.png` to `end_ship.png` for cities that have a ship. The marker stays in the same "End Cities" marker set and toggle — it's just a visual distinction.

### Algorithm (cross-referenced: cubiomes `finders.c`, KaptainWutax `EndCityGenerator.java`, Minecraft wiki)

End city piece generation is deterministic from `(worldSeed, chunkX, chunkZ)`. It uses recursive placement of template pieces (towers, bridges, houses) with AABB collision detection. A ship spawns with probability `1/(10-depth)` at each bridge endpoint, and only one ship can spawn per city.

#### Step 1: RNG seeding ("carver seed")

```java
Random rand = new Random(worldSeed);
long a = rand.nextLong();
long b = rand.nextLong();
long seed = (long) chunkX * a ^ (long) chunkZ * b ^ worldSeed;
rand = new Random(seed);
```

This is Minecraft's `ChunkRandom.setCarverSeed()` — the same seeding used for caves. Both cubiomes and KaptainWutax confirm this exact formula.

#### Step 2: Initial pieces (no branching)

```java
int rotation = rand.nextInt(4);  // 0=NONE, 1=CW90, 2=CW180, 3=CCW90

// Always placed (deterministic):
place("base_floor",    offset(0, 0, 0),     rotation);
place("second_floor_1", offset(-1, 0, -1),  rotation);
place("third_floor_1",  offset(-1, 4, -1),  rotation);
place("third_roof",     offset(-1, 8, -1),  rotation);

// Then start recursive generation:
generateRecursive(TOWER_GENERATOR, depth=1, thirdRoof, pieces, rand);
```

#### Step 3: Recursive piece generation

Four generator types call each other recursively up to depth 8:

**TOWER_GENERATOR** — Builds a tower stack, then optionally adds bridges or a fat tower:
- Places `tower_base` at offset `(3+nextInt(2), -3, 3+nextInt(2))`
- Places `tower_piece` on top
- `nextInt(3) == 0` → set floor reference (for bridge attachment)
- Stacks `nextInt(3)+1` additional `tower_piece`s, with `nextBoolean()` to update floor reference
- If floor reference exists: try 4 bridge directions, each with `nextBoolean()` gate → calls `TOWER_BRIDGE_GENERATOR`
- If no floor reference and depth != 7: calls `FAT_TOWER_GENERATOR` instead
- Caps with `tower_top`

**TOWER_BRIDGE_GENERATOR** — Builds a bridge, then either spawns a ship or a house+tower:
- `nextInt(4)+1` bridge segments, each consuming `nextBoolean()` (flat vs stairs) and possibly another `nextBoolean()` (steep vs gentle)
- **Ship decision**: `if (!shipCreated && nextInt(10 - depth) == 0)` → ship spawns, set `shipCreated = true`
- Ship placement offset: `(-8+nextInt(8), y, -70+nextInt(10))` — these RNG calls only happen if the ship decision succeeds
- If no ship: calls `HOUSE_TOWER_GENERATOR` recursively

**HOUSE_TOWER_GENERATOR** — Builds a house, optionally topped with another tower:
- Places `base_floor`, then branches on `nextInt(3)`:
  - 0: just `base_roof`
  - 1: `second_floor_2` + `second_roof` + recursive `TOWER_GENERATOR`
  - 2: `second_floor_2` + `third_floor_2` + `third_roof` + recursive `TOWER_GENERATOR`

**FAT_TOWER_GENERATOR** — Wider tower variant with bridges:
- Places `fat_tower_base` + `fat_tower_middle`
- Up to 2 extra `fat_tower_middle` sections (loop while `nextInt(3) != 0`)
- Each middle section: 4 bridge directions with `nextBoolean()` gate → `TOWER_BRIDGE_GENERATOR`
- Caps with `fat_tower_top`

#### Step 4: Collision detection

Each `generateRecursive` call:
1. Runs the generator's `generate()` method, which produces a list of candidate pieces
2. Calls `rand.nextInt()` (no bound — 32-bit) to assign a `genDepth` tag
3. Checks each candidate piece's bounding box against all existing pieces
4. If any collision with a piece from a *different* genDepth → entire group rejected, `generateRecursive` returns `false`
5. Pieces with the same genDepth are allowed to overlap

This collision check is necessary for correctness because:
- Failed branches change the RNG state (the `rand.nextInt()` for genDepth still happens)
- Failed branches prevent reaching later ship decisions
- The first bridge piece in TOWER_BRIDGE_GENERATOR has `genDepth = -1` (immune to collision)

#### Step 5: Piece bounding boxes

Each piece has a fixed size. Bounding boxes are computed from the piece's origin position + rotation + template size. The rotation transform (with pivot at origin) is:

| Rotation | (x, y, z) → |
|----------|-------------|
| NONE (0) | (x, y, z) |
| CW_90 (1) | (-z, y, x) |
| CW_180 (2) | (-x, y, -z) |
| CCW_90 (3) | (z, y, -x) |

Piece sizes (width, height, depth — used as exclusive bounds, i.e. box spans [origin, origin+size-1]):

| Piece | Size |
|-------|------|
| base_floor | 10, 4, 10 |
| base_roof | 12, 2, 12 |
| bridge_end | 5, 6, 2 |
| bridge_gentle_stairs | 5, 7, 8 |
| bridge_piece | 5, 6, 4 |
| bridge_steep_stairs | 5, 7, 4 |
| fat_tower_base | 13, 4, 13 |
| fat_tower_middle | 13, 8, 13 |
| fat_tower_top | 17, 6, 17 |
| second_floor_1 | 12, 8, 12 |
| second_floor_2 | 12, 8, 12 |
| second_roof | 14, 2, 14 |
| ship | 13, 24, 29 |
| third_floor_1 | 14, 8, 14 |
| third_floor_2 | 14, 8, 14 |
| third_roof | 16, 2, 16 |
| tower_base | 7, 7, 7 |
| tower_piece | 7, 4, 7 |
| tower_top | 9, 5, 9 |

(tower_floor exists in vanilla but is never used by the generation code)

Bridge direction offsets for towers:
- NONE: (1, -1, 0)
- CW_90: (6, -1, 1)
- CCW_90: (0, -1, 5)
- CW_180: (5, -1, 6)

Bridge direction offsets for fat towers:
- NONE: (4, -1, 0)
- CW_90: (12, -1, 4)
- CCW_90: (0, -1, 8)
- CW_180: (8, -1, 12)

### Implementation plan

#### 1. Create `EndCityShipDetector.java`

Single public method:

```java
public static boolean hasShip(long worldSeed, int chunkX, int chunkZ)
```

Internal structure:
- **Piece record**: `(int minX, minY, minZ, maxX, maxY, maxZ, int genDepth)` — just an AABB + tag
- **Pieces list**: `List<int[]>` or similar lightweight structure (max ~420 pieces per city, but typically <50)
- **`shipFound` flag**: set `true` on first ship spawn, prevents further ships
- **Four generator methods** mirroring the vanilla logic, each taking `(depth, parentPiece, pieces, rand)` and returning `boolean`
- **Helper**: `addPiece(pieces, parent, offset, pieceName, rotation)` — computes child bounding box from parent position + rotated offset + piece size
- **Helper**: `collides(piece, pieces, excludeGenDepth)` — AABB intersection check
- **Helper**: `rotate(x, y, z, rotation)` — applies rotation transform

We don't need y-position for the `hasShip` result but we DO need it internally because collision detection is 3D. Use a fixed `y=0` for the initial base_floor position (the actual y doesn't matter as long as it's consistent — all relative offsets are the same).

Port from KaptainWutax `EndCityGenerator.java` as the primary reference (already Java, same RNG semantics). Cross-check against cubiomes for any discrepancies.

#### 2. Modify `StructureLocator.StructurePos` record

Add a boolean field for the ship flag:

```java
public record StructurePos(int blockX, int blockZ, StructureType type, boolean hasShip) {}
```

Most callers pass `false`. For `END_CITY`, the grid locator runs `EndCityShipDetector.hasShip()` on each position after biome validation passes.

Update existing call sites to pass `hasShip = false` — this is a backward-compatible addition (just add `false` to each `new StructurePos(...)` call in `StructureLocator`, `StrongholdLocator`, and `BuriedTreasureLocator`).

#### 3. Modify `BlueMapIntegration.createMarkers()`

Currently the icon URL is resolved once per `StructureType`. For `END_CITY`, we need both `end_city.png` and `end_ship.png` available:

- Upload `end_ship.png` alongside the regular icons (it's already in the resources)
- In the marker loop, when `type == END_CITY && pos.hasShip()`:
  - Use `end_ship.png` icon URL instead of `end_city.png`
  - Change the label/detail to "End City (Ship)" to distinguish on hover

The simplest approach: upload `end_ship.png` as a separate icon asset, store its URL, and branch in the per-marker loop. No new marker sets, toggles, or config changes needed.

#### 4. Wiring in `StructureLocator`

In the main `findStructures()` method, after the grid locator produces `END_CITY` positions (and they pass radius + biome checks), call `EndCityShipDetector.hasShip()`:

```java
boolean hasShip = (type == StructureType.END_CITY)
    ? EndCityShipDetector.hasShip(worldSeed, blockX / 16, blockZ / 16)
    : false;
results.add(new StructurePos(blockX, blockZ, type, hasShip));
```

Note: `chunkX = blockX / 16` works because `blockX = chunkX * 16 + 8`, so integer division recovers the chunk coordinate.

### Performance

- End city ship detection runs once per end city position (~30-80 cities in a typical 5000-block radius)
- Each detection does recursive piece generation with ~20-100 RNG calls and AABB checks
- Total: negligible compared to the grid search itself

### Tests

Add to `StructureLocatorTest.java`:
- `end_city_ship_detection()` — for seed 12345, radius 2000, check that some end cities have `hasShip=true` and some have `hasShip=false`, assert specific counts and a few known positions with known ship status

Add `EndCityShipDetectorTest.java`:
- `known_ship_positions()` — test `hasShip()` directly with several known (worldSeed, chunkX, chunkZ) inputs and expected results
- Cross-validate against cubiomes or Chunkbase output for seed 12345

To generate known-good values: use Chunkbase's end city finder with seed 12345, or run the ported algorithm and manually verify a few results against an actual world.

### Reference implementations
- **Primary**: [KaptainWutax/FeatureUtils EndCityGenerator.java](https://github.com/KaptainWutax/FeatureUtils/blob/master/src/main/java/kaptainwutax/featureutils/structure/generator/structure/EndCityGenerator.java) — Java, same RNG
- **Cross-reference**: [cubiomes finders.c](https://github.com/Cubitect/cubiomes/blob/master/finders.c) — C, `getEndCityPieces()` function

---

## Icons needed
- `treasure.png` — exists, used by Phase 1
- `end_ship.png` — exists, ready to use for Phase 2
