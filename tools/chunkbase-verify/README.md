# Chunkbase Verification Tool

Extracts structure positions from [Chunkbase](https://www.chunkbase.com/apps/seed-map) using Playwright browser automation. Used to verify the accuracy of our own structure-finding algorithms.

See `docs/chunkbase-extraction-findings.md` (in the project root) for the full investigation into how Chunkbase works internally and the `onPoiDrawn` hook technique this tool uses.

## Setup

```bash
bun install
bunx playwright install chromium
```

## Usage

```bash
# Basic — extracts structures within 10k blocks for a given seed
bun run extract.ts --seed=-8408138640175831038

# Smaller radius for quick testing
bun run extract.ts --seed=12345 --radius=3000

# All options
bun run extract.ts \
  --seed=-8408138640175831038 \
  --radius=10000 \
  --version=java_1_21_9 \
  --output=data/my-output.json \
  --headless
```

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--seed` | (required) | Minecraft world seed |
| `--radius` | `10000` | Scan radius in blocks from origin |
| `--version` | `java_1_21_9` | Chunkbase platform string |
| `--output` | `data/chunkbase-seed-{seed}.json` | Output file path |
| `--headless` | `false` | Run browser without UI (headed by default so you can watch) |

## Output format

```json
{
  "seed": "-8408138640175831038",
  "version": "java_1_21_9",
  "radius": 10000,
  "extractedAt": "2026-03-06T12:00:00.000Z",
  "structures": [
    { "type": "village", "x": 128, "y": null, "z": -256, "details": { "type": "plains", "zombie": false } },
    { "type": "dungeon", "x": 50, "y": 30, "z": 100, "details": [50, 30, 100, 0] },
    { "type": "stronghold", "x": 1200, "y": null, "z": -800, "details": null }
  ]
}
```

### Structure metadata (`details` field)

Most structures have `null` details. Notable exceptions:

| Type | Details | Notes |
|------|---------|-------|
| `village` | `{ type, zombie }` | Biome type: plains/desert/savanna/taiga/snowy |
| `dungeon` | `[x, y, z, spawnerType]` | spawnerType: 0=zombie, 1=skeleton, 2=spider |
| `igloo` | `{ hasBasement }` | Whether basement lab exists |
| `oceanRuin` | `{ type, isLarge, clusterSize }` | type: cold/warm |

## How it works

1. Launches Chromium and navigates to Chunkbase's seed map
2. Enables POI checkboxes for the target dimension
3. Hooks `CB3TooltipManager.onPoiDrawn` to intercept every structure drawn on the canvas
4. Pans across a grid of positions (step=3000 blocks) to cover the full radius
5. Collects and deduplicates all intercepted structures
6. Repeats for overworld, nether, and end dimensions
7. Writes merged results to JSON

A 10k radius scan takes roughly 5-7 minutes.

## Extracted structure types

**Overworld:** village, ancientCity, stronghold, woodlandMansion, oceanMonument, pillagerOutpost, ruinedPortalOverworld, jungleTemple, desertTemple, witchHut, buriedTreasure, shipwreck, igloo, oceanRuin, trailRuin, trialChamber, dungeon

**Nether:** netherFortress, bastionRemnant, ruinedPortalNether

**End:** endCity
