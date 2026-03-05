# Use Actual World Data for Explored Areas

## Type
Research / implementation task

## Summary
Currently all structure positions are purely algorithmic — computed from the world seed using RNG and biome noise. For chunks that have already been generated and explored, Minecraft stores the actual generated structure data on disk. We should investigate reading this data to replace our "guesses" with confirmed structure locations in explored areas.

## Current state
- **Zero world data access** — the mod never reads region files, NBT data, chunk storage, or any saved world state
- `StructureLocator.java` and `StrongholdLocator.java` are pure seed+RNG
- `BiomeValidator.java` uses `BiomeSource.getBiome()` which is a pure function of coordinates+seed, not saved chunk data
- The only file I/O is config loading (`ModConfig.java`) and icon uploading (`BlueMapIntegration.java`)

## Benefits of using world data
- **Eliminates false positives** in explored areas (outpost frequency filter, height checks, etc.)
- **Catches false negatives** — any structures our algorithm misses would still show up
- **Could show structure details** — e.g. village type, portal completeness, chest loot status
- **Hybrid approach**: use real data for explored chunks, algorithmic predictions for unexplored areas
- Could visually distinguish confirmed vs predicted markers (different icon styles or colours)

## What Minecraft stores
- **Region files** (`.mca` in `region/`, `DIM-1/region/`, `DIM1/region/`) — chunk-level NBT data
- **Structure starts** — stored in chunk NBT under `Starts` tag within `structures` compound
- **Structure references** — stored under `References` tag, pointing back to the chunk containing the start
- **POI data** (`.mca` in `poi/`) — points of interest like village meeting points, bee nests
- Vanilla also has `data/` directory with things like `raids.dat`, `map_*.dat`

## Possible approaches
1. **Read structure starts from chunk NBT** — scan region files for chunks that have `structures.Starts` entries
2. **Use Minecraft's own API** — access `ServerWorld.getStructureAccessor()` or similar to query generated structures for loaded/saved chunks
3. **Use `ChunkManager`/`ThreadedAnvilChunkStorage`** — query which chunks have been generated and read their structure data
4. **Periodic scan** — scan region files on server start or periodically, cache results

## Considerations
- **Performance**: Reading region files could be slow for large worlds — need caching and incremental updates
- **Thread safety**: Region file access needs care, especially while the server is running
- **API vs direct file access**: Using MC's own classes is safer but may require chunks to be loaded; direct NBT reading is more flexible but more fragile
- **Compatibility**: Region file format and NBT structure names may vary between MC versions
- **Hybrid UX**: How to present the difference between confirmed and predicted structures to the user
