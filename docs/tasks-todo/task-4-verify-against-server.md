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

## Phase 1: Playwright extraction tool

Build a standalone Playwright script that extracts structure positions from Chunkbase for a configurable seed.

### Deliverables
- `tools/chunkbase-verify/` directory with a Playwright project
- Script accepts seed, radius, and MC version as parameters
- Scans all three dimensions (overworld, nether, end)
- Outputs JSON: `{ structures: [{ type, x, z, details }] }`
- Deduplicates using `repr` key (see findings doc)

### Key implementation notes (from findings doc)
- Do NOT put `pois=` in the URL hash (breaks POI loading)
- Must dismiss cookie consent dialog
- Enable POI checkboxes via DOM clicks, not URL params
- Use hash-based navigation (`x=`/`z=` params + `CB3Router.applyCurrentUrl()`)
- Zoom 0.5 is sufficient; 3s compute wait + `redrawmap` trigger + 2s paint wait per position
- Nether/End require separate scans with `dimension=` hash param
- Use `onPoiDrawn` hook for collection (proven approach, 26k structures extracted in testing)

### Structure types to extract
**Overworld**: village, ancientCity, stronghold, woodlandMansion, oceanMonument, pillagerOutpost, ruinedPortalOverworld, jungleTemple, desertTemple, witchHut, buriedTreasure, shipwreck, igloo, oceanRuin, trailRuin, trialChamber, dungeon
**Nether**: netherFortress, bastionRemnant, ruinedPortalNether
**End**: endCity

### Types we DON'T extract (not in our mod)
fossil, oreVein, desertWell, spawn, mineshaft, slimeChunk, cave, ravine, lavaPool, amethystGeode, itemOverworld, endGateway, fossilNether

## Phase 2: Generate comparison datasets

Run the extraction tool for both seeds and save the output.

### Deliverables
- `tools/chunkbase-verify/data/chunkbase-seed-12345.json`
- `tools/chunkbase-verify/data/chunkbase-seed-server.json`
- Use a 10k block radius for both (matches the tested 16k x 16k scan area)

## Phase 3: Automated comparison tests

Write a JUnit test that loads Chunkbase JSON and compares against our algorithm.

### Deliverables
- Test fixture JSON files in `src/test/resources/`
- `ChunkbaseComparisonTest.java` that:
  - Runs our algorithm for seed 12345 at matching radius
  - Loads Chunkbase JSON
  - Per structure type, reports:
    - **Matches**: positions in both (within some tolerance, e.g. same chunk)
    - **False positives**: we predict, Chunkbase doesn't
    - **False negatives**: Chunkbase predicts, we don't
  - Asserts match rates per structure type (initially lenient, tighten as we fix bugs)

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

## Phase 4: Fix discrepancies

Investigate and fix issues revealed by the comparison. Known suspects:

### Stronghold algorithm (HIGH priority)
Our positions are significantly off from real positions:
- Our: (-40, -1848) vs Actual: (68, -1548) — ~300 blocks off
- Our: (-1368, 840) vs Actual: (-1676, 916) — ~316 blocks off

Likely a bug in `StrongholdLocator.java`. Chunkbase comparison will confirm.

### Pillager Outpost false positives (MEDIUM priority)
Vanilla has `frequency: 0.2` that rejects 80% of grid positions. We don't implement this. Comparison will quantify the false positive rate precisely.

### Surface height checks (LOW priority)
Desert Pyramids, Jungle Temples, Mansions have height validation in vanilla. ~5% false positive rate. May not be worth implementing since we can't easily do height checks without chunk generation.

### Biome validation accuracy (UNKNOWN priority)
BiomeSource.getBiome() should be accurate but hasn't been verified at scale. Comparison will reveal any systematic biome-related mismatches.

## Phase 5: Manual in-game verification

Use the server seed extraction data for spot-checking in-game, focusing on:
- Any structure types where Chunkbase and our algorithm agree but might both be wrong
- Edge cases near biome boundaries
- Structures in the stronghold notes above

## Notes from initial manual testing

- Stronghold
  - Our Location: -40, -1848
  - Actual Location: 68, -1548
- Stronghold
  - Our Location: -1368, 840
  - Actual Location: -1676, 916
