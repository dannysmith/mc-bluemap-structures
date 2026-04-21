# BlueMap Structure Markers

Shows villages, temples, fortresses, and 16 other structure types as markers on your [BlueMap](https://bluemap.bluecolored.de/) web maps. Each structure type gets its own toggleable layer in the markers sidebar and map markers get a suitable icon and a `/tp` tooltip for its coordinates. Wherever possible, structure coords are computed algorithmicly based on the world seed (in a similar way to Chunkbase). Where this is demonstrably inacurrate, your world data on disk is read to help.

Hence this is a Fabric server-side mod rather than a pure Bluemap addon. (Originally built for Minecraft 1.21.11)

<img width="1917" height="951" alt="bm-structures-image" src="https://github.com/user-attachments/assets/09af2086-5005-4bea-b814-6135c4714b6e" />

## Features

- Per-structure toggleable marker sets in the BlueMap sidebar
- Zoom-based visibility (dense structures like shipwrecks only show when zoomed in)
- Copyable `/tp` command in marker popups
- Configurable search radius.

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

Structure positions are calculated from the world seed using the same grid-based algorithm as Minecraft. Each candidate position is validated against the server's biome data via `BiomeSource` (ie. chunks do not need to have been loaded for structure marker generation). Valid structure positions become POI markers on the appropriate BlueMap dimension map.
