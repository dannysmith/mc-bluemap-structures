# Verify Algorithms Against Real Server

## Type
Manual testing / debugging task

## Summary
Visit a large number of predicted structure locations on a real server to verify our algorithms are correct. For any errors, investigate why we're wrong and tweak the algorithm.

## Approach
1. Generate a list of predicted structure positions for the server's seed
2. Visit each location in-game and confirm whether the structure actually exists
3. Log results: correct predictions, false positives, and any missed structures
4. For errors, determine root cause and fix

## Known sources of inaccuracy
From AGENTS.md:
- **Pillager Outpost**: ~5x false positives — vanilla has `frequency: 0.2` filter we don't implement
- **Desert Pyramids, Jungle Temples, Mansions**: ~5% false positives due to missing surface height checks
- **Biome validation**: Uses `BiomeSource.getBiome()` which should be accurate but hasn't been extensively verified
- **Nether Fortress/Bastion grid**: Shared grid with weight roll (`nextInt(5) < 2` = fortress) — verify the split is correct

## Structures to verify
All types in `StructureType.java` enum, prioritising those with known inaccuracy concerns.


## Notes

- Strongold
  - Our Location: -40, -1848
  - Actual Location: 68 -1548
- Stronghold
  - Our Location: -1368, 840
  - Actual Location: -1676, 916
