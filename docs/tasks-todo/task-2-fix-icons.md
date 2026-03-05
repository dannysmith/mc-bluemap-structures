# Fix Structure Icons

## Type
Manual task (Danny)

## Summary
Go through all icons in `src/main/resources/icons/` and ensure each filename matches the actual image content. Many icons currently show the wrong structure for their filename. Fix by swapping filenames to match the correct images.

## Icons to check
- ancient_city.png
- apple.png
- bastion.png
- cave.png
- desert_temple.png
- desert_well.png
- dungeon.png
- end_city.png
- end_gateway.png
- fossil.png
- geode.png
- igloo.png
- jungle_temple.png
- lava_pool.png
- mansion.png
- mineshaft.png
- monument.png
- nether_fortress.png
- nether_fossil.png
- ocean_ruins.png
- ore_veins.png
- outpost.png
- ravine.png
- ruined_portal_nether.png
- ruined_portal_ow.png
- shipwreck.png
- slime_chunk.png
- stronghold.png
- trail_ruins.png
- treasure.png
- trial_chamber.png
- village.png
- witch_hut.png

## Context
- Icons are 22x22 PNGs extracted from the Chunkbase sprite sheet
- Referenced by `StructureType.java` enum which maps each structure to its icon filename
- Icons are uploaded to BlueMap's asset storage via `BlueMapIntegration.java`
