# Increase Structure Radius Without Hurting BlueMap Performance

## Type
Implementation / research task

## Summary
Currently structures are only loaded within a fairly small radius because too many markers cause BlueMap's web UI to become very slow — even with marker sets toggled off. We need a more elegant solution to support a much larger radius.

## Context
- Radius is configured in `ModConfig.java`
- Markers are created as `POIMarker` instances in `BlueMapIntegration.java` and added to `MarkerSet`s
- The performance issue is in the BlueMap web frontend, not server-side
- Even toggled-off marker sets still impact performance (likely because the data is still loaded/parsed by the browser)

## Possible approaches
- **Clustering**: Group nearby markers at low zoom levels, expand at higher zoom
- **Lazy loading / chunked markers**: Only send markers for the visible viewport area
- **Multiple marker sets by region**: Split markers into regional sets so only relevant ones load
- **BlueMap API limitations**: Check if BlueMap supports any of the above natively, or if we'd need to work around it
- **Reduce marker data**: Minimise the data per marker (simpler HTML, smaller icons)
- **LOD approach**: Show fewer structure types at low zoom, all at high zoom

## Questions
- What does BlueMap's marker API actually support for large marker counts?
- Is there a practical upper limit we should target (e.g. 10k markers, 50k)?
- Can we leverage BlueMap's tile system or viewport events?
