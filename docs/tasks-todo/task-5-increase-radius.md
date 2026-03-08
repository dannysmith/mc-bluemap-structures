# Increase Structure Radius Without Hurting BlueMap Performance

## Type
Implementation / research task

## Summary
Currently structures are only loaded within a fairly small radius because too many markers cause BlueMap's web UI to become very slow — even with marker sets toggled off. We need a more elegant solution to support a much larger radius (ideally unlimited).

## Research Findings

### Why BlueMap is slow with many markers

The bottleneck is entirely in the web frontend, not the server. Here's exactly what happens:

1. **Every POI marker = 3+ real DOM elements** (wrapper div, container div, img + label div). 8,000 markers = 24,000+ DOM nodes.

2. **Every frame**, BlueMap's `CSS2DRenderer` traverses the *entire* scene tree **twice** — once to project every marker's 3D position to screen coords (matrix multiplication per marker), and again to sort all markers by z-index. This is O(n) on *total* markers, not *visible* markers.

3. **Toggled-off marker sets still cost performance.** `visible = false` sets `display: none` on the DOM element, but the renderer still traverses, projects, and manages every marker every frame.

4. **All markers are a single monolithic JSON blob** (`markers.json`), re-fetched and fully re-parsed every 10 seconds. No pagination, no chunking, no delta updates.

5. **`maxDistance` helps visually but not computationally** — it sets `opacity: 0` on distant markers via the `onBeforeRender` callback, but the DOM elements persist and the per-frame traversal still runs.

### Confirmed performance threshold

~8,000 POI markers is where BlueMap becomes noticeably laggy, confirmed by multiple users (GitHub issues [#558](https://github.com/BlueMap-Minecraft/BlueMap/issues/558), [#600](https://github.com/BlueMap-Minecraft/BlueMap/issues/600)) and acknowledged by the BlueMap developer:

> "BlueMap's marker system is not made for huge numbers of markers." — TBlueF

The developer has mentioned a "webapp rewrite" that would address marker performance, but there's no timeline or details.

### How BlueMap stores/serves markers

- Server side: markers stored in memory as `ConcurrentHashMap<String, MarkerSet>`, serialized to JSON via Gson
- Written to `<webroot>/maps/<map-id>/live/markers.json` as a single file
- Served via BlueMap's built-in HTTP server at route `live/markers.json` with a 10-second rate-limit cache
- Frontend polls this endpoint every 10 seconds with no-cache headers
- No per-marker or per-region granularity — it's all-or-nothing

### How other mapping tools handle this

The mapping industry (Leaflet, Mapbox GL, OpenLayers) has well-established solutions:

| Technique | Marker capacity | Notes |
|---|---|---|
| DOM markers (what BlueMap does) | ~1,000–3,000 | Our current situation |
| DOM + clustering | 10,000–50,000 | Would need BlueMap-side changes |
| Canvas rendering | 50,000–100,000 | Would require replacing BlueMap's renderer |
| WebGL point rendering | 500,000–5M | Not applicable within BlueMap's architecture |
| Viewport-based loading | Millions+ | Only loads markers for visible area |

Since we can't change BlueMap's renderer, **viewport-based loading is our best option**.

### No existing BlueMap solutions

- No BlueMap addon implements clustering, viewport culling, or lazy loading
- All existing addons (BlueMap-Towny, BlueBridge, etc.) use filtering to keep counts low
- BlueMap has no built-in spatial indexing, frustum culling for markers, or viewport events API

### Our current marker density estimates

At `radiusBlocks = 5000`: ~1,000–4,000 markers across all structure types per dimension (borderline OK).
At `radiusBlocks = 20,000`: could easily hit 10,000–50,000+ markers (well past the breaking point).

### BlueMap's HTTP server and extension points

BlueMap runs its own NIO-based HTTP server (no Netty/Jetty/third-party libs). Key findings from reading the BlueMap source:

- **No public API for custom HTTP routes.** `BlueMapAPI` exposes zero HTTP-related interfaces. There's an undocumented internal cast (`BlueMapAPIImpl.plugin().getWebRequestHandler()`) to access the `RoutingRequestHandler`, but it's fragile across versions.
- **`WebApp.registerScript(url)`** — Public API. Injects a `<script>` tag into the BlueMap webapp. Our JS runs in the same page with full access to Three.js scene, camera, etc.
- **`WebApp.getWebRoot()`** — Public API. We can write arbitrary files to BlueMap's webroot directory and they get served by BlueMap's built-in file handler automatically. Same origin, no CORS issues.
- **`BlueMapMap.getAssetStorage()`** — Public API. Can write files that are served under the map's asset path.

The `RoutingRequestHandler` uses a `ConcurrentLinkedDeque<Route>` with regex matching. Routes added via `register()` go to the front of the deque (highest priority). A catch-all `".*"` route at the back serves static files from the webroot via `FileRequestHandler`. So any file we write to the webroot is automatically served.

### Fallback: JDK HTTP server

If we ever need truly dynamic queries (not just static files), `com.sun.net.httpserver.HttpServer` is built into Java 21 — zero dependencies, ~30 lines of setup, runs on a separate port.

## Chosen Approach: Tile-Based Viewport Loading

Instead of creating all markers at startup via BlueMap's marker API, we pre-compute structures into regional tile files and use injected JavaScript to load only the tiles visible in the current viewport.

### Architecture

**Server side (Java, runs at startup):**
1. Compute structure positions for a large radius (or unlimited) using our existing algorithms
2. Partition results into regional tile files (e.g., 1024x1024 block regions)
3. Write tile files as JSON to BlueMap's webroot: `data/structures/overworld/r.0.0.json`, `data/structures/nether/r.-1.2.json`, etc.
4. Register our client script via `WebApp.registerScript()`
5. Do NOT create any BlueMap `POIMarker` objects — our JS handles all marker rendering

**Client side (JavaScript, injected into BlueMap webapp):**
1. Hook into BlueMap's Three.js camera to read current position and zoom level
2. Determine which region tiles are visible in the current viewport
3. Fetch only those tile JSON files (browser caches them naturally)
4. Create/remove DOM marker elements as the user pans — only markers in/near the viewport exist at any time
5. Manage a rolling window: load tiles entering the viewport, unload tiles leaving it

### Why this works

- **Same origin**: Files in BlueMap's webroot are served by BlueMap's own HTTP server on the same port. No CORS, no separate server.
- **Public API only**: Uses `getWebRoot()` and `registerScript()` — both stable, documented API methods.
- **Browser caching**: Tile files are static JSON, so the browser caches them after first fetch. Revisiting an area is instant.
- **Bounded DOM cost**: Only markers for visible tiles exist in the DOM at any time — maybe 200–500 markers regardless of total world coverage.
- **Deterministic**: Structure positions don't change (same seed = same positions), so pre-computing tiles at startup is valid.

### BlueMap webapp JS globals (from reading source)

BlueMap exposes everything we need as globals. By the time our injected script runs, the app is fully initialised.

**Globals:**
- `window.bluemap` — live `BlueMapApp` instance (camera, maps, markers, controls)
- `window.BlueMap` — barrel re-export of every class (`MarkerSet`, `PoiMarker`, `HtmlMarker`, `Three`, etc.)

**Camera access:**
```js
window.bluemap.mapViewer.controlsManager.position  // Vector3 look-at target (x=east, z=south)
window.bluemap.mapViewer.controlsManager.distance   // zoom distance from look-at point
window.bluemap.mapViewer.camera                     // Three.js PerspectiveCamera subclass
```

**Events (on `window.bluemap.events`, no polling needed):**
- `bluemapCameraMoved` — fires on every camera change, detail: `{controlsManager, camera}`
- `bluemapMapChanged` — fires on dimension switch, detail: `{map}`
- `bluemapRenderFrame` — fires every rendered frame

**Creating markers from JS:**
```js
const { MarkerSet, PoiMarker } = window.BlueMap;
const marker = new PoiMarker("village-1");
marker.updateFromData({ position: {x, y, z}, label, icon, detail, maxDistance, ... });
myMarkerSet.add(marker);
window.bluemap.mapViewer.markers.add(myMarkerSet);
```

These are first-class BlueMap markers — they appear in the sidebar, support toggling, distance fading, click labels, etc. And can be removed just as easily when they leave the viewport.

**Map info:**
```js
window.bluemap.mapViewer.map.data.id  // current map ID, e.g. "world", "world_nether"
window.bluemap.maps                   // all loaded Map instances
```

## Discarded Approaches

- **Aggressive maxDistance tiering**: Doesn't reduce marker count in JSON or DOM, only hides them visually. The per-frame traversal cost remains.
- **Regional marker sets**: Still loads all data into the browser. Manual region toggling is poor UX.
- **Reduce marker data**: Marginal gains, doesn't address the O(n) per-frame problem.
- **Wait for BlueMap webapp rewrite**: No timeline, not actionable.
- **Custom HTTP endpoint on BlueMap's server**: No public API for route registration. Internal cast to `BlueMapAPIImpl` is possible but fragile across versions.
- **Separate HTTP server (JDK/Netty)**: Unnecessary since static tile files served from webroot accomplish the same goal without CORS issues or a second port.

---

## Implementation Plan

### Design Decisions

**Tile size: 1024 blocks.** Each tile covers a 1024x1024 block region. At a comfortable zoom, the viewport shows ~2000x2000 blocks = roughly 4–9 tiles. With structure spacings of 20–80 chunks (320–1280 blocks), each tile contains ~1–10 markers per structure type. With 20 types enabled, that's ~50–200 markers in the viewport — well under the ~3000 threshold.

**Tile coordinates:** Tile `(rx, rz)` covers block range `[rx*1024, (rx+1)*1024)`. Tile `(0,0)` = blocks 0–1023, tile `(-1,0)` = blocks -1024 to -1.

**Spawn marker stays on the Java API.** It's a single marker with no performance impact — no reason to move it to the tile system.

**One MarkerSet per structure type in JS.** This preserves the current UX where each structure type is independently toggleable in BlueMap's sidebar. The MarkerSets persist across tile load/unload; only individual PoiMarkers within them are added/removed as tiles come in and out of view.

**Old code retained alongside new system initially.** We keep the existing `createMarkers()` code (gated behind a config flag or simply not called) until the tile system is confirmed working. Then remove in a follow-up.

### File structure on disk (inside BlueMap webroot)

```
data/bluemap-structures/
  meta.json                           # structure types, tile size, icon URLs, dimension config
  js/bluemap-structures.js            # injected client script
  tiles/overworld/r.0.0.json          # tile files per dimension
  tiles/overworld/r.-1.0.json
  tiles/nether/r.0.0.json
  tiles/end/r.0.0.json
  ...
```

### meta.json format

```json
{
  "tileSize": 1024,
  "dimensions": {
    "overworld": {
      "path": "data/bluemap-structures/tiles/overworld",
      "mapIds": ["world"],
      "tileRange": { "minX": -49, "maxX": 48, "minZ": -49, "maxZ": 48 }
    },
    "nether": { ... },
    "end": { ... }
  },
  "structureTypes": {
    "VILLAGE": {
      "displayName": "Villages",
      "iconUrl": "assets/structures/village.png",
      "maxDistance": 5000,
      "defaultHidden": false
    },
    ...
  }
}
```

`tileRange` tells the JS which tiles actually exist, so it doesn't 404 on empty regions.

### Tile JSON format (e.g. `r.0.0.json`)

```json
[
  { "x": 128, "z": 256, "t": "VILLAGE" },
  { "x": 520, "z": 900, "t": "DESERT_PYRAMID" },
  { "x": 300, "z": 100, "t": "END_CITY", "s": true }
]
```

Kept minimal — just coordinates, type key, and optional `"s": true` for end city ships. The JS looks up display names, icons, maxDistance etc. from `meta.json`. Empty tiles can either be an empty array `[]` or a missing file (JS handles both).

### Phase 1: Java-side tile generation

**New class: `StructureTileWriter`**

Responsibilities:
- Accept a list of `StructurePos` per structure type (from existing `StructureLocator`)
- Partition positions into tile buckets based on `blockX / 1024`, `blockZ / 1024`
- Write each non-empty tile as a JSON file to `<webroot>/data/bluemap-structures/tiles/<dimension>/r.<rx>.<rz>.json`
- Write `meta.json` with structure type metadata, tile ranges per dimension, and icon URLs
- Copy the client JS from mod resources to `<webroot>/data/bluemap-structures/js/bluemap-structures.js`
- Call `api.getWebApp().registerScript("data/bluemap-structures/js/bluemap-structures.js")`

**Changes to `BlueMapIntegration`:**
- Replace `createMarkers()` internals: instead of creating `POIMarker` objects, call `StructureTileWriter.writeTiles()` with the computed positions
- Keep `createSpawnMarker()` unchanged (single marker via Java API)
- Keep `uploadIcons()` unchanged (icons still needed — the JS references them)
- Keep `removeMarkers()` for the spawn marker; add cleanup of tile files on disable

**Changes to `ModConfig`:**
- Increase default `radiusBlocks` to 50000 (or higher — tiles make large radii cheap to serve)

### Phase 2: Client-side viewport loader (JavaScript)

**New file: `bluemap-structures.js`** (in `src/main/resources/`, copied to webroot at startup)

On load:
1. Fetch `meta.json`
2. Determine the current dimension from `bluemap.mapViewer.map.data.id` by matching against `meta.json`'s `mapIds`
3. Create one `MarkerSet` per structure type (using display names and toggleable settings from meta.json), add to `bluemap.mapViewer.markers`

Camera movement handler (`bluemapCameraMoved`):
1. Read `controlsManager.position` (x, z) and `controlsManager.distance` (zoom)
2. Compute a viewport rectangle in block coords (position + buffer zone based on zoom distance)
3. Convert to tile coordinates
4. Diff against currently-loaded tiles — determine which tiles to load, which to unload
5. Fetch new tile JSON files (with browser caching)
6. For each marker in a new tile: create a `PoiMarker`, call `updateFromData()` with position/icon/label/detail/maxDistance from meta.json, add to the appropriate MarkerSet
7. For each marker in an unloaded tile: remove from its MarkerSet and dispose

Map switch handler (`bluemapMapChanged`):
1. Unload all current tiles and markers
2. Re-determine dimension from the new map ID
3. Reload tiles for the new dimension's viewport

Debouncing:
- Don't re-evaluate tiles on every camera event — debounce to e.g. 200ms or use a dirty flag checked on `bluemapRenderFrame`
- Tile fetches are async; don't block rendering while loading

### Phase 3: Polish and UX

- **Toggle state persistence**: Save per-structure-type visibility to `localStorage` (BlueMap does this for its own marker sets, pattern: `bluemap-markerset-{id}-visible`)
- **Error handling**: Missing tile files (404) = empty area, not an error. Log once and cache the empty result.
- **Detail HTML**: Match the current format — label, coordinates, `/tp` command input with the existing styling
- **Loading feedback**: Optional subtle indicator while tiles are being fetched (could skip this initially)

### Phase 4: Cleanup

- Remove old `createMarkers()` POIMarker code from `BlueMapIntegration` (the spawn marker code stays)
- Remove `maxDistance` from `StructureType` enum (now defined in meta.json / JS-side)
- Update `docs/architecture.md` to describe the tile-based system
- Clean up the temp BlueMap clone at `/tmp/bluemap-research/`

### Risk & edge cases

- **Users with external webservers (nginx):** Writing to webroot still works — nginx serves the same files. No issue.
- **BlueMap API changes:** We only use `getWebRoot()`, `registerScript()`, `getAssetStorage()`, and the MarkerSet API for spawn. All stable, public API.
- **Buried treasure density:** Spacing=1 means potentially many markers per tile. But biome filtering limits actual density to beaches. If it's still too dense at large radii, we can use a tighter `maxDistance` in meta.json or smaller tiles for that type specifically. Monitor in testing.
- **Script loading order:** Our script runs after BlueMap is fully loaded (scripts are injected at end of `BlueMapApp.load()`). No race condition.
- **Tile regeneration on config change:** If the user changes `radiusBlocks` or toggles structure types in config, they need to restart the server. Same as current behavior.
