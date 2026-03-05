# Investigate Adding Dungeons (Spawners)

## Type
Research / implementation task

## Summary
Investigate whether dungeon (monster spawner) locations can be predicted from seed and added as BlueMap markers.

## Context
- Dungeons are NOT jigsaw structures or located via the region-grid algorithm used for other structures in `StructureLocator.java`
- They are generated during chunk decoration as random underground rooms with a spawner block
- Their placement depends on terrain/cave generation, making seed-based prediction significantly harder than grid-based structures
- May require a different algorithmic approach entirely
- `dungeon.png` icon already exists in `src/main/resources/icons/`

## Questions to answer
- How does vanilla MC place dungeons during world gen?
- Is there a known algorithm or approach for predicting dungeon positions from seed alone?
- What accuracy can we realistically achieve without loading chunks?
- Are there existing implementations (SeedFinding libs, Chunkbase source) we can reference?
