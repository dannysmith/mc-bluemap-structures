# BlueMap Structure Markers

Shows villages, temples, fortresses, and 16 other structure types as markers on your [BlueMap](https://bluemap.bluecolored.de/) web map. Each structure type gets its own toggleable layer in the sidebar with a custom icon, and markers include a `/tp` command you can copy. Positions are computed directly from the world seed -- same algorithm Minecraft uses -- so it doesn't need to load any chunks.

Fabric server-side mod for Minecraft 1.21.11.

<img width="1917" height="951" alt="bm-structures-image" src="https://github.com/user-attachments/assets/09af2086-5005-4bea-b814-6135c4714b6e" />

## Features

- Per-structure toggleable marker sets in the BlueMap sidebar
- Zoom-based visibility (dense structures like shipwrecks only show when zoomed in)
- Copyable `/tp` command in marker popups
- Configurable search radius and per-structure enable/disable

## Setup

Requires Fabric, Fabric API, and BlueMap 5.0+.

```bash
./gradlew build
# Copy build/libs/bluemap-structures-1.0.0.jar to your server's mods/ folder
```

## Config

`config/bluemap-structures.json` is created on first run:

- `radiusBlocks` -- how far from origin to search (default: 10000)
- `enabledStructures` -- toggle each structure type on/off

## How It Works

Structure positions are calculated from the world seed using the same grid-based algorithm as Minecraft. Each candidate position is validated against the server's biome data via `BiomeSource` -- no chunk loading required. Valid positions become POI markers on the appropriate BlueMap dimension map.
