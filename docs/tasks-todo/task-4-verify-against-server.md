# Verify Algorithms Against Chunkbase + Real Server

## Type
Tooling + testing + debugging task

## Summary
Build a Playwright-based tool to extract structure positions from Chunkbase, then systematically compare against our own algorithm output. Fix any discrepancies found. Also use the server seed data for manual in-game spot-checking.

## Reference
See `docs/chunkbase-extraction-findings.md` for the full Chunkbase investigation: internal architecture, `onPoiDrawn` hook API, working Playwright script, gotchas, and metadata format.

## Seeds
- **Test seed**: `12345` (used in existing JUnit tests)
- **Server seed**: `-8408138640175831038` (for manual in-game verification)

---

## Phase 1: Playwright extraction tool — DONE

Built `tools/chunkbase-verify/` with `extract.ts` CLI script.

```bash
bun run extract.ts --seed=12345 --radius=10000 --headless
```

Key discovery during implementation: Chunkbase's `filterPoisByZoomLevel` prevents dungeons from being drawn (and thus captured by the `onPoiDrawn` hook) at zoom 0.5. Fixed by setting `CB3FinderAppConfig.filterPoisByZoomLevel = false` before scanning.

### Structure types extracted
**Overworld**: village, ancientCity, stronghold, woodlandMansion, oceanMonument, pillagerOutpost, ruinedPortalOverworld, jungleTemple, desertTemple, witchHut, buriedTreasure, shipwreck, igloo, oceanRuin, trailRuin, trialChamber, dungeon
**Nether**: netherFortress, bastionRemnant, ruinedPortalNether
**End**: endCity

### Types we extract but don't implement yet
dungeon (saved for future use)

### Types we DON'T extract
fossil, oreVein, desertWell, spawn, mineshaft, slimeChunk, cave, ravine, lavaPool, amethystGeode, itemOverworld, endGateway, fossilNether

## Phase 2: Generate comparison datasets — DONE

Extracted for both seeds at 10k radius:
- `tools/chunkbase-verify/data/chunkbase-seed-12345.json` — 31,705 structures
- `tools/chunkbase-verify/data/chunkbase-seed--8408138640175831038.json` — 32,289 structures
- Test fixture: `src/test/resources/chunkbase-seed-12345.json`

## Phase 3: Automated comparison tests — DONE

`ChunkbaseComparisonTest.java` loads the seed 12345 Chunkbase JSON and compares against our algorithm output (without biome validation, so false positives are expected). Comparison uses 16-block tolerance for position matching.

### Comparison results (seed 12345, radius 10000, no biome validator)

The "Coverage" column is the important one — what % of Chunkbase positions do we also find. False positives are expected without biome validation and aren't a concern here.

| Structure | Our Count | CB Count | Matches | FP | FN | Coverage |
|-----------|-----------|----------|---------|-----|-----|----------|
| Swamp Huts | 1,529 | 9 | 9 | 1,520 | 0 | **100%** |
| Ocean Monuments | 1,532 | 115 | 106 | 1,426 | 9 | **92%** |
| Buried Treasure | 15,659 | 341 | 307 | 15,352 | 43 | **90%** |
| Ocean Ruins | 3,890 | 898 | 791 | 3,099 | 107 | **88%** |
| Shipwrecks | 2,711 | 649 | 570 | 2,141 | 79 | **88%** |
| Ruined Portals OW | 988 | 732 | 625 | 363 | 107 | **85%** |
| End Cities | 3,932 | 285 | 240 | 3,692 | 45 | **84%** |
| Igloos | 1,517 | 59 | 50 | 1,467 | 9 | **85%** |
| Villages | 1,337 | 230 | 190 | 1,147 | 40 | **83%** |
| Pillager Outposts | 1,524 | 40 | 33 | 1,491 | 7 | **83%** |
| Trail Ruins | 1,345 | 111 | 91 | 1,254 | 20 | **82%** |
| Ancient Cities | 2,710 | 72 | 58 | 2,652 | 14 | **81%** |
| Mansions | 251 | 5 | 4 | 247 | 1 | **80%** |
| Jungle Temples | 1,524 | 29 | 18 | 1,506 | 11 | **62%** |
| Bastions | 1,332 | 820 | 439 | 893 | 381 | **54%** |
| Desert Pyramids | 1,533 | 7 | 3 | 1,530 | 4 | **43%** (tiny sample) |
| Nether Fortresses | 807 | 836 | 274 | 533 | 562 | **33%** |
| Trial Chambers | 1,344 | 1,001 | 210 | 1,134 | 791 | **21%** |
| Strongholds | 25 | 20 | 1 | 24 | 19 | **5%** |
| Ruined Portals Nether | 2,500 | 732 | 9 | 2,491 | 723 | **1%** |

### Analysis of false negatives

Most structures with 80-92% coverage have their false negatives at the edges of the scan radius (coordinates near ±10000-11000), suggesting Chunkbase's effective scan area is slightly larger than ours. These are not algorithmic issues.

The truly broken structures have fundamental mismatches that need investigation.

## Phase 4: Fix discrepancies

Prioritised by severity based on comparison results.

### BROKEN: Ruined Portals Nether — 1% coverage

Only 9 matches out of 732. Almost certainly wrong parameters in `StructureType.java`. Current: `RUINED_PORTAL_NETHER(25, 10, 34222645, LINEAR, NETHER)`. Needs investigation — parameters may not match vanilla 1.21.

### BROKEN: Strongholds — 5% coverage

Only 1 match out of 20. Algorithm in `StrongholdLocator.java` is fundamentally wrong. Manual testing also confirmed positions are 300+ blocks off:
- Our: (-40, -1848) vs Actual: (68, -1548)
- Our: (-1368, 840) vs Actual: (-1676, 916)

### BROKEN: Trial Chambers — 21% coverage

210 matches out of 1001. Near-misses spotted in data: our `(8168, 8936)` vs Chunkbase `(8169, 8919)` — just barely outside 16-block tolerance. Likely a coordinate precision issue (Chunkbase reports block-level, we report chunk-center) combined with possible parameter differences. Current: `TRIAL_CHAMBERS(34, 12, 94251327, LINEAR)`.

### BROKEN: Nether Fortresses — 33% coverage

274 matches out of 836. Something is off with the shared nether grid algorithm in `StructureLocator.findNetherComplex()`. Could be parameters, weight roll logic, or the grid calculation itself.

### BROKEN: Bastions — 54% coverage

439 matches out of 820. Shares grid with Fortresses, so likely the same root cause. The fortress/bastion split (`nextInt(5) < 2`) may not match vanilla exactly.

### SUSPECT: Jungle Temples — 62% coverage

18 matches out of 29. Could be parameter issues or the small sample size makes it look worse than it is. Worth investigating alongside desert pyramids.

### OK but noisy: Pillager Outposts — 83% coverage

33 matches out of 40 Chunkbase positions. The 1,491 false positives confirm the known issue: vanilla has `frequency: 0.2` filter that rejects 80% of grid positions. We show ~38x too many (1524 vs 40).

### LOW PRIORITY: Surface height checks

Desert Pyramids, Jungle Temples, Mansions have height validation in vanilla. This causes some false positives but doesn't affect coverage of real positions. Not worth implementing since we can't do height checks without chunk generation.

## Phase 5: Manual in-game verification

Use the server seed extraction data (`data/chunkbase-seed--8408138640175831038.json`) for spot-checking in-game, focusing on:
- Any structure types where Chunkbase and our algorithm agree but might both be wrong
- Edge cases near biome boundaries
- Validating fixes from Phase 4

## Notes from initial manual testing

- Stronghold
  - Our Location: -40, -1848
  - Actual Location: 68, -1548
- Stronghold
  - Our Location: -1368, 840
  - Actual Location: -1676, 916

## Key files

| File | Purpose |
|------|---------|
| `tools/chunkbase-verify/extract.ts` | Chunkbase extraction script |
| `tools/chunkbase-verify/data/` | Extracted JSON datasets (gitignored) |
| `src/test/resources/chunkbase-seed-12345.json` | Test fixture for comparison |
| `src/test/java/.../ChunkbaseComparisonTest.java` | Automated comparison test |
| `src/main/java/.../StructureLocator.java` | Main grid algorithm |
| `src/main/java/.../StrongholdLocator.java` | Stronghold ring algorithm |
| `src/main/java/.../StructureType.java` | Structure parameters (spacing/salt/etc) |
| `build.gradle` | `showStandardStreams = true` added to see test output |

### Mapping Chunkbase type IDs to our StructureType enum
| Chunkbase ID | Our StructureType |
|---|---|
| village | VILLAGE |
| desertTemple | DESERT_PYRAMID |
| jungleTemple | JUNGLE_TEMPLE |
| witchHut | SWAMP_HUT |
| igloo | IGLOO |
| pillagerOutpost | PILLAGER_OUTPOST |
| ancientCity | ANCIENT_CITY |
| trailRuin | TRAIL_RUINS |
| trialChamber | TRIAL_CHAMBERS |
| oceanRuin | OCEAN_RUIN |
| shipwreck | SHIPWRECK |
| ruinedPortalOverworld | RUINED_PORTAL_OW |
| oceanMonument | MONUMENT |
| woodlandMansion | MANSION |
| netherFortress | FORTRESS |
| bastionRemnant | BASTION |
| ruinedPortalNether | RUINED_PORTAL_NETHER |
| endCity | END_CITY |
| buriedTreasure | BURIED_TREASURE |
| stronghold | STRONGHOLD |
