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

### Open questions

- What tile size gives the best balance? (Too small = many files + many fetches. Too large = loading unnecessary markers.)
- How do we hook into the Three.js camera? BlueMap's `MapViewer` exposes the camera, but we need to find it from our injected script's context.
- Should we create actual BlueMap `CSS2DObject` markers from JS, or render our own DOM elements positioned via the camera projection? (The former integrates better with BlueMap's UI; the latter avoids the CSS2DRenderer traversal overhead.)
- How do we handle BlueMap's existing marker polling? We should avoid creating markers via the Java API so they don't appear in `markers.json` — our JS-only markers would be invisible to BlueMap's marker system.

## Discarded Approaches

- **Aggressive maxDistance tiering**: Doesn't reduce marker count in JSON or DOM, only hides them visually. The per-frame traversal cost remains.
- **Regional marker sets**: Still loads all data into the browser. Manual region toggling is poor UX.
- **Reduce marker data**: Marginal gains, doesn't address the O(n) per-frame problem.
- **Wait for BlueMap webapp rewrite**: No timeline, not actionable.
