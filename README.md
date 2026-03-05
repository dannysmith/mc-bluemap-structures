# BlueMap Structure Markers

Fabric server-side mod for Minecraft 1.21.1 that adds Chunkbase-style toggleable structure markers to BlueMap. Predicts structure positions from the world seed using the same algorithm as Minecraft, with biome validation to filter false positives.

## Features

- 19 structure types across Overworld, Nether, and End
- Per-structure toggleable marker sets in the BlueMap sidebar
- Zoom-based visibility (dense structures like shipwrecks only show when zoomed in)
- Custom icons per structure type
- Configurable search radius and per-structure enable/disable

## Setup

Requires Fabric, Fabric API, and BlueMap 5.0+.

```bash
./gradlew build
# Copy build/libs/bluemap-structures-1.0.0.jar to your server's mods/ folder
```

## Config

`config/bluemap-structures.json` is created on first run:

- `radiusBlocks` — how far from origin to search (default: 10000)
- `enabledStructures` — toggle each structure type on/off

## How It Works

Structure positions are calculated mathematically from the world seed (same grid-based algorithm Minecraft uses). Each candidate position is then validated against the server's biome data using `BiomeSource` — no chunk loading required. Valid positions become POI markers on the appropriate BlueMap dimension maps.
