# Add Dungeon (Spawner) Markers

## Type
Implementation task

## Summary
Add dungeon (monster spawner) locations as BlueMap markers. Requires reading world data from already-generated chunks — seed-based prediction is not feasible.

## Blocked by
- Likely depends on task-x (use world data) being implemented first, since dungeons cannot be predicted from seed alone

## Research findings
- Dungeons are **features**, not structures — they generate during the decoration phase (step 8 of 12), after caves are carved (step 7)
- Each chunk gets **14 placement attempts** (10 at Y 0-320, 4 at Y -58 to -1)
- Each attempt picks a random position, then validates against actual terrain: solid floor, solid ceiling, and 1-5 wall openings at floor level
- This terrain dependency means **dungeons cannot be reliably predicted from seed alone**
- Confirmed by cubiomes (explicitly excludes dungeons), AMIDST (marked out of scope), and Chunkbase (documented inaccuracies)
- Chunkbase gets partial accuracy by simulating terrain generation, but still has false positives/negatives
- Dungeons generate even with "Generate Structures" off — they are not part of the `StructureSet` system
- They are NOT findable via `/locate`

## Approach
- Read dungeon positions from already-generated chunk NBT data, or use Minecraft's server API to query generated features
- This gives 100% accuracy in explored areas (vs partial accuracy from terrain simulation)
- Ties naturally into the "use world data" task — same infrastructure reads structure starts from chunks
- `dungeon.png` icon already exists in `src/main/resources/icons/`
