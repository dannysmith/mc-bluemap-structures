# Chunkbase Structure Data Extraction - Investigation Findings

Investigation performed 2026-03-06. Tested against Chunkbase as of Jan 21, 2026 (MC 1.21).

## Goal

Extract structure/POI positions from Chunkbase for a given Minecraft seed and version, outputting JSON with structure type, X/Z coordinates, and any extra metadata (village biome type, igloo basement, dungeon spawner type, etc.). This data is used to verify the accuracy of independent structure-finding algorithms.

## How Chunkbase Works Internally

Chunkbase is **100% client-side**. All structure position calculations happen in the browser via:

- A **~4 MB WebAssembly module** (compiled from Rust with `wasm-bindgen`), embedded as base64 directly inside a Web Worker JS file (`/_astro/B1WDqatJyKGT.js`, ~5.6 MB total)
- Up to `Math.min(navigator.hardwareConcurrency - 1, 6)` Web Workers compute tile data in parallel
- The main thread renders results on a `<canvas id="map-canvas">` element using Canvas 2D
- **Zero server-side API calls** for seed/structure computation
- The WASM is Chunkbase's own proprietary Rust implementation — **NOT** cubiomes

The architecture: Astro static site → jQuery-based UI → Web Workers → WASM → Canvas rendering

### Key JS Files (all under `/_astro/`)

| File | Size | Purpose |
|------|------|---------|
| `B1WDqatJyKGT.js` | 5.6 MB | Web Worker with embedded WASM binary + structure algorithms |
| `DfMxptLc4JGp.js` | 42 KB | Canvas rendering engine (`$.fn.chunkMap`) |
| `DWXbO7Rugg0o.js` | 24 KB | Main orchestrator (`CB3FinderApp`) — workers, tiles, tooltips |
| `DHuStMMLA3ye.js` | 5.6 KB | Tile management (`SeedMapTiles`) — LRU cache (2048 tiles) |
| `DV6b_SaJTw7V.js` | 17 KB | Structure/POI definitions (33 types with metadata) |
| `qM2vOSXB8SMH.js` | 6 KB | Platform definitions (all Java/Bedrock versions) |
| `Cl0kHWaURmQ_.js` | 18 KB | Biome data (IDs, names, RGB colors, categories) |

Note: These filenames are content-hashed and will change when Chunkbase updates.

## Key Global JavaScript Objects

| Object | Purpose |
|--------|---------|
| `CB3FinderApp` | Main app (jQuery event emitter). Use `.trigger("redrawmap")` to force repaint. Also has events like `poischanged`, `goto`, `zoomchange`, `seedapply`. |
| `CB3TooltipManager` | Manages POI hit-testing and rendering. Has the hookable `onPoiDrawn` callback. Also has `onCanvasClick`, `testCanvasHit`, `getPoiData(id)`. |
| `CB3Router` | URL hash routing. `applyQuery(queryString)` and `applyCurrentUrl()` to update state from hash. `getQueryForCurrentState()` to read current state. |
| `CB3FinderAppConfig` | Config object. `pois` (string "all" or list), `filterPoisByZoomLevel` (bool), `chunkWidths`, `dimensions`. |
| `appHelpers` | Utilities: `toLong(n)` (required for coordinate events), `cleanSeed(s)`, `getSeedFromLong(l)`, `poisToParams(arr)`, `getFullHashForPoi(type, item)`. |
| `ChunkApp` | Core config: `Platform` enum, `Zooming` settings. |
| `CB3SharedTaskManagerMain` | Cross-worker result caching (QuickLRU). |
| `SeedMapTiles` | Tile loading/caching. `calcTileParams()`. |
| `CB3MapUserDataManager` | localStorage-backed "completed POI" tracking (the checkbox in tooltips). |

## Proven Extraction Approach: `onPoiDrawn` Hook + Viewport Scanning

### The `onPoiDrawn` Callback

This callback fires for every POI/structure that gets painted onto the canvas during a render pass.

**Full signature (9 arguments):**
```javascript
onPoiDrawn(type, repr, coords, details, canvasX, canvasY, iconWidth, iconHeight, clipRect)
```

- `type` (string): Structure type ID (e.g., `"village"`, `"dungeon"`, `"igloo"`)
- `repr` (string): Unique key like `"village;-20;-10;0"` — **use this for deduplication**
- `coords` (array): `[blockX, blockY, blockZ]` — Y is `null` for surface-only structures
- `details` (array): `[chunkX, chunkZ, metadata]` — `details[2]` has the structure-specific data
- `canvasX`, `canvasY` (number): Pixel position on the canvas
- `iconWidth`, `iconHeight` (number): Icon dimensions in pixels
- `clipRect` (array): `[x1, y1, x2, y2]` visible bounds for clipping

### Structure-Specific Metadata (details[2])

| Structure Type ID | details[2] | Notes |
|-------------------|-----------|-------|
| `village` | `{type: "plains", zombie: false}` | Biome types: plains, desert, savanna, taiga, snowy. `zombie: true` for zombie villages |
| `igloo` | `{hasBasement: true}` | Whether the basement lab with brewing stand exists |
| `oceanRuin` | `{type: "cold", isLarge: false, clusterSize: 0}` | type: "cold" or "warm". isLarge: large vs small ruin |
| `dungeon` | `[x, y, z, spawnerType]` | **spawnerType**: 0=zombie, 1=skeleton, 2=spider |
| `fossil` | `[x, y, z, "coal"]` | Variant/ore type string |
| `oreVein` | `{min: [x,y,z], max: [x,y,z], reference: [x,y,z], count: N, type: "copper", oreCount: N}` | Full 3D bounding box + ore type |
| `trialChamber` | `[x, y, z]` | 3D start coordinates |
| `trailRuin` | `[x, y, z]` | 3D coordinates |
| `desertWell` | `[x, y, z]` | 3D coordinates |
| `ancientCity` | `null` | Only X/Z from coords; Y is -51 from coords[1] |
| `spawn` | `{x: 0, z: 0}` | World spawn point |
| `stronghold` | `null` | Only X/Z from coords |
| `woodlandMansion` | `null` | Only X/Z from coords |
| `desertTemple` | `null` | Only X/Z from coords |
| `jungleTemple` | `null` | Only X/Z from coords |
| `witchHut` | `null` | Only X/Z from coords |
| `oceanMonument` | `null` | Only X/Z from coords |
| `pillagerOutpost` | `null` | Only X/Z from coords |
| `mineshaft` | `null` | Only X/Z from coords |
| `ruinedPortalOverworld` | `null` | Only X/Z from coords |
| `shipwreck` | `null` | Only X/Z from coords |
| `buriedTreasure` | `null` | Only X/Z from coords |

## Complete Working Playwright Script

This was tested and successfully extracted **26,338 structures** across a 16k×16k area.

### Step-by-Step Approach

```javascript
// === STEP 1: Navigate to Chunkbase ===
// Use a minimal URL — just seed and platform. Do NOT include pois= in the URL.
// If pois= is present (even empty), it overrides the defaults and you get nothing.
await page.goto("https://www.chunkbase.com/apps/seed-map#seed=-8408138640175831038&platform=java_1_21_9");
await page.waitForTimeout(10000); // Wait for WASM to load and initial tiles to compute

// === STEP 2: Dismiss cookie consent dialog ===
// Chunkbase shows a GDPR consent dialog on first visit. Must dismiss it.
// Look for button with text "Accept all" and click it.
const acceptBtn = page.getByRole('button', { name: 'Accept all' });
if (await acceptBtn.isVisible()) await acceptBtn.click();

// === STEP 3: Enable all desired POI checkboxes ===
// POIs are custom checkbox elements with role="checkbox" and aria-checked="true"/"false".
// They are NOT standard HTML <input type="checkbox"> elements.
// You must click unchecked ones to enable them.
await page.evaluate(() => {
  const skip = ["Cave", "Ravine", "Lava Pool", "Geode", "Apple",
                "Highlight biomes", "Grid Lines", "Terrain",
                // Skip other-dimension structures for overworld scan:
                "End City", "End Gateway", "Nether Fortress", "Bastion",
                "Nether Fossil", "Slime Chunk"];
  document.querySelectorAll('[role="checkbox"]').forEach(cb => {
    const text = cb.textContent.trim();
    if (cb.getAttribute('aria-checked') === 'false' &&
        !skip.some(s => text.includes(s))) {
      cb.click();
    }
  });
});
await page.waitForTimeout(10000); // Wait for workers to recompute ALL tiles with new POIs

// === STEP 4: Install persistent collection hook ===
await page.evaluate(() => {
  window._allCollected = new Map();
  window._origOnPoiDrawn = CB3TooltipManager.onPoiDrawn;
  CB3TooltipManager.onPoiDrawn = function(type, repr, coords, details) {
    if (!window._allCollected.has(repr)) {
      window._allCollected.set(repr, {
        type,
        x: coords[0],
        y: coords[1],
        z: coords[2],
        details: details && details[2] ? details[2] : null
      });
    }
    return window._origOnPoiDrawn.apply(this, arguments);
  };
});

// === STEP 5: Scan the area by panning across a grid ===
// At zoom 0.5, the viewport covers roughly ±2000 blocks from center.
// A grid step of 3000 blocks provides good overlap.
// For a 10k block radius, scan from -10000 to +10000.
const radius = 10000;
const step = 3000;
const positions = [];
for (let x = -radius; x <= radius; x += step) {
  for (let z = -radius; z <= radius; z += step) {
    positions.push([x, z]);
  }
}

for (const [px, pz] of positions) {
  // Pan by modifying the URL hash and telling the router to re-apply
  await page.evaluate(([x, z]) => {
    window.location.hash = window.location.hash
      .replace(/x=[^&]*/, 'x=' + x)
      .replace(/z=[^&]*/, 'z=' + z);
    CB3Router.applyCurrentUrl();
  }, [px, pz]);

  await page.waitForTimeout(3000); // Wait for worker tile computation

  // Force redraw to trigger onPoiDrawn for all visible structures
  await page.evaluate(() => {
    CB3FinderApp.trigger("redrawmap");
  });

  await page.waitForTimeout(2000); // Wait for paint cycle
}

// === STEP 6: Collect results ===
const result = await page.evaluate(() => {
  CB3TooltipManager.onPoiDrawn = window._origOnPoiDrawn; // Restore
  const arr = Array.from(window._allCollected.values());
  delete window._allCollected;
  delete window._origOnPoiDrawn;
  return arr;
});

// `result` is now an array of {type, x, y, z, details} objects
// Write to JSON file, filter by radius, etc.
```

### Important Gotchas Discovered During Investigation

1. **Do NOT put `pois=` in the URL hash.** If the URL contains `pois=` (even `pois=village,dungeon,...`), the router's `applyQuery` function parses it and can reset all POIs to empty. The checkboxes must be clicked via DOM manipulation instead.

2. **`CB3FinderApp.trigger("goto", ...)` requires Long objects**, not plain numbers. It calls `appHelpers.toLong()` internally and crashes with `e.toNumber is not a function` if given regular numbers. The hash-based navigation approach avoids this entirely.

3. **The `redrawmap` event only repaints from cached tiles** — it does NOT trigger new worker computations. After panning to a new position, you must wait for workers to compute the new tiles (3s was reliable), THEN trigger redrawmap.

4. **Cookie consent dialog** blocks interaction. Must dismiss on first load.

5. **`initialPois`** (the default visible structures) is only: `ancientCity, bastionRemnant, buriedTreasure, endCity, netherFortress, woodlandMansion, ruinedPortalOverworld, slimeChunk, spawn, trialChamber, village, biomes`. Many structures (dungeon, mineshaft, desert temple, etc.) are OFF by default and must be explicitly enabled.

6. **Zoom 0.5 is sufficient.** Once checkboxes are enabled, ALL structure types render at zoom 0.5. There is no need to zoom in further. The `filterPoisByZoomLevel` config exists but doesn't affect which POIs are computed — it only affects which get drawn, and at 0.5 everything draws.

7. **Nether/End structures require separate scans** in a different dimension. Change the dimension dropdown (combobox "Dimension:") or modify the `dimension=` hash parameter.

8. **The `repr` string is the reliable dedup key.** Format: `"structureType;chunkX;chunkZ;dimensionIndex"`. Using a Map keyed by repr prevents duplicate collection when viewports overlap.

## All Available POI Type IDs

### Overworld
```
spawn, slimeChunk, village, ancientCity, dungeon, stronghold,
woodlandMansion, oceanMonument, pillagerOutpost, mineshaft,
ruinedPortalOverworld, jungleTemple, desertTemple, witchHut,
buriedTreasure, shipwreck, igloo, oceanRuin, fossil,
oreVein, desertWell, trailRuin, trialChamber
```

### Nether
```
netherFortress, bastionRemnant, ruinedPortalNether, fossilNether
```

### End
```
endCity, endGateway
```

### User wants to EXCLUDE
```
lavaPool, cave, ravine, amethystGeode, itemOverworld (Enchanted Golden Apple)
```

## Checkbox-to-POI-ID Mapping

The feature checkboxes use display names, not IDs. Here's the mapping (from the DOM):

| Checkbox Text | POI ID |
|--------------|--------|
| Biomes | `biomes` |
| Spawn Point | `spawn` |
| Slime Chunk | `slimeChunk` |
| Village | `village` |
| Ancient City | `ancientCity` |
| Dungeon | `dungeon` |
| Stronghold | `stronghold` |
| Mansion | `woodlandMansion` |
| Monument | `oceanMonument` |
| Outpost | `pillagerOutpost` |
| Mineshaft | `mineshaft` |
| Ruined Portal (overworld) | `ruinedPortalOverworld` |
| Jungle Temple | `jungleTemple` |
| Desert Temple | `desertTemple` |
| Witch Hut | `witchHut` |
| Treasure | `buriedTreasure` |
| Shipwreck | `shipwreck` |
| Igloo | `igloo` |
| Ocean Ruins | `oceanRuin` |
| Fossil | `fossil` |
| Cave | `cave` |
| Ravine | `ravine` |
| Underground Lava Pool | `lavaPool` |
| End City | `endCity` |
| End Gateway | `endGateway` |
| Nether Fortress | `netherFortress` |
| Bastion | `bastionRemnant` |
| Ruined Portal (nether) | `ruinedPortalNether` |
| Geode | `amethystGeode` |
| Apple | `itemOverworld` |
| Ore Veins | `oreVein` |
| Desert Well | `desertWell` |
| Trail Ruins | `trailRuin` |
| Trial Chamber | `trialChamber` |
| Nether Fossil | `fossilNether` |

Note: There are TWO "Ruined Portal" checkboxes — one for overworld, one for nether. They appear in order in the DOM; the first is overworld, the second is nether.

## URL Hash Format

```
#seed=<seed>&platform=<platform>&dimension=<dimension>&x=<x>&z=<z>&zoom=<zoom>
```

- `seed`: The world seed (string, can be negative, e.g., `-8408138640175831038`)
- `platform`: Version identifier (e.g., `java_1_21_9`, `bedrock_1_21_120`)
- `dimension`: `overworld`, `nether`, or `end`
- `x`, `z`: Center coordinates in blocks
- `zoom`: Zoom factor (0.5 = overview, 4 = zoomed in). 0.5 shows ~±2000 blocks from center.

## Worker Message Protocol (for reference)

If you wanted to intercept worker messages directly (alternative to onPoiDrawn):

**Main → Worker (tile computation request):**
```javascript
{
  type: "check",
  params: {
    seed: "string",
    platform: { cb3World: { edition, javaVersion, config } },
    dimension: "overworld" | "nether" | "end",
    pois: ["village", "buriedTreasure", ...],
    showBiomes: true/false,
    tileSize: 8,        // chunks per tile side
    tileScale: 0.25,    // biome resolution scale
    biomeHeight: "depth0" | "caveDepth" | "bottom",
    showHeights: true/false,
    biomeFilter: [biomeId, ...] | null
  },
  tile: { x, z, xL, zL, scale }
}
```

**Worker → Main (tile computation result):**
```javascript
{
  type: "check",
  results: {
    biomes: ArrayBuffer,       // Uint8Array of biome IDs
    biomeScale: number,
    heights: ArrayBuffer,      // Int32Array of surface heights
    biomeFilter: [...],
    poiResults: {              // Structure type → array of [chunkX, chunkZ, metadata]
      "village": [[cx, cz, {type:"plains", zombie:false}], ...],
      "buriedTreasure": [[cx, cz], ...],
      "dungeon": [[cx, cz, [blockX, blockY, blockZ, spawnerType]], ...],
      // etc.
    }
  }
}
```

Note: Intercepting worker messages was NOT used in the successful extraction. The `onPoiDrawn` hook approach is simpler and was the validated method. However, worker interception could theoretically be more efficient since it wouldn't require viewport scanning.

## Structure Finding Algorithm (from JS/WASM source)

Minecraft structures use a deterministic grid-based algorithm. Each structure type has:
- `spacing`: Grid spacing in chunks (region size)
- `separation`: Minimum distance within the grid cell
- `salt`: Per-structure seed salt value

Key configs found in the source:
| Structure | Spacing | Separation | Salt |
|-----------|---------|------------|------|
| Village | 34 | 8 | 10387312 |
| Desert Temple | 32 | 8 | 14357617 |
| Jungle Temple | 32 | 8 | 14357619 |
| Igloo | 32 | 8 | 14357618 |
| Witch Hut | 32 | 8 | 14357620 |
| Shipwreck | 24 | 4 | (varies) |
| End City | 20 | 11 | 10387313 |
| Woodland Mansion | 80 | 20 | 10387319 |
| Pillager Outpost | 32 | 8 | 165745296 |
| Trial Chamber | 24 | 8 | 20083232 |
| Stronghold | special ring algorithm | N/A | N/A |

## Alternative Approaches

### 1. MC-SeedLocator (npm: `mc-seedlocator`)
- **GitHub**: https://github.com/acheong08/MC-SeedLocator
- **What**: Extracted Chunkbase's `chunky.wasm` binary + JS wrappers, packaged as npm module
- **API**: `getAreaResult(seed, [x,z], poiTypes, {edition, javaVersion, dimension, searchWidth, tileSize, tileScale})`
- **Pro**: No browser needed, exact Chunkbase parity, simple API
- **Con**: Appears stuck at MC 1.20.4, may be unmaintained
- **Verdict**: Would be ideal if it supported 1.21.x. Worth checking for updates.

### 2. cubiomes (C library)
- **GitHub**: https://github.com/Cubitect/cubiomes
- **What**: Independent C reimplementation of MC Java Edition world gen
- **Pro**: Actively maintained, supports latest MC versions, no browser needed, compiles to WASM (proven by mcseedmap.net)
- **Con**: Independent implementation — may differ from Chunkbase on edge cases, which defeats the purpose of comparing against Chunkbase
- **Verdict**: Best long-term approach for your OWN structure finding, but doesn't serve the "compare against Chunkbase" use case

### 3. Direct WASM Extraction
- **What**: Extract the base64 WASM from the worker JS file, instantiate it directly in Node.js
- **Pro**: Exact same engine as Chunkbase, no rendering overhead, fast
- **Con**: Complex reverse-engineering of the wasm-bindgen interface, fragile across updates
- **Verdict**: High effort but could be very powerful. The WASM exports include: `biomeprovider`, `dungeonfinder`, `cavefinder`, `oreveinfinder`, `xoroshirorandom`, `world`, etc.

### 4. Browser Automation with Playwright (Recommended)
- **What**: Control headless Chromium, navigate Chunkbase, hook JS to collect data
- **Pro**: Always matches current Chunkbase version, gets all metadata, **proven to work** (26k structures extracted in testing)
- **Con**: Slower (~5-7 min for 20k×20k scan), needs Chromium
- **Verdict**: **Best fit for the stated goal of comparing against Chunkbase specifically**

### 5. Jacob Hummer's CSV Export Technique
- **Blog post**: https://jcbhmr.com/2024/12/05/chunkbase-csv/
- **What**: Similar onPoiDrawn hook approach, exports to CSV
- **Limitation**: Only captures the current viewport, doesn't scan multiple positions
- **Note**: This blog post confirmed the approach was viable and informed our investigation

## Test Results Summary

| Metric | Value |
|--------|-------|
| Seed tested | `-8408138640175831038` |
| MC Version | Java 1.21.9 |
| Area scanned | ~16k × 16k blocks (grid: -8000 to +8000, step 3000) |
| Total structures extracted | 26,338 |
| Scan grid positions | 36 (6×6) |
| Wait per position | ~5s (3s compute + 2s render) |
| Total scan time | ~3 minutes |

### Structures found by type:
| Type | Count |
|------|-------|
| dungeon | 16,871 |
| mineshaft | 3,206 |
| oreVein | 2,524 |
| trialChamber | 667 |
| oceanRuin | 707 |
| fossil | 563 |
| shipwreck | 536 |
| ruinedPortalOverworld | 510 |
| buriedTreasure | 240 |
| village | 146 |
| oceanMonument | 90 |
| trailRuin | 78 |
| igloo | 73 |
| ancientCity | 52 |
| pillagerOutpost | 24 |
| jungleTemple | 16 |
| stronghold | 15 |
| desertTemple | 9 |
| desertWell | 9 |
| woodlandMansion | 1 |
| spawn | 1 |

Note: witchHut had 0 because Witch Hut checkbox wasn't enabled when the scan ran (it was in the skip list by mistake during that particular run — it should NOT be skipped).

## Biome Data Gap

The `onPoiDrawn` callback does not include the biome at each structure's location (except for villages which embed their biome type). To get biome info for other structures, options include:

1. **Read the biome tile data**: Workers return a `biomes: ArrayBuffer` (Uint8Array of biome IDs) per tile. This could be intercepted from worker messages and used to look up biome at each structure's chunk coordinates.
2. **Use Chunkbase's biome color lookup**: The file `Cl0kHWaURmQ_.js` contains all biome definitions with IDs, names, and RGB colors.
3. **Click each structure on the map**: The tooltip popup includes biome info for some structures (e.g., "Plains Village"). But this would be extremely slow for thousands of structures.

Option 1 (worker biome data interception) is likely the most practical for bulk extraction.
