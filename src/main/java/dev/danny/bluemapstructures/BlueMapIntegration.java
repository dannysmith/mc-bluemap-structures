package dev.danny.bluemapstructures;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;

import java.util.List;

public class BlueMapIntegration {

    public static void register(long worldSeed, ModConfig config) {
        BlueMapAPI.onEnable(api -> {
            BlueMapStructuresMod.LOGGER.info("BlueMap API enabled, creating structure markers...");
            createMarkers(api, worldSeed, config);
        });
    }

    private static void createMarkers(BlueMapAPI api, long worldSeed, ModConfig config) {
        int totalMarkers = 0;

        for (StructureType type : StructureType.values()) {
            if (!config.isEnabled(type)) continue;

            List<StructureLocator.StructurePos> positions =
                    StructureLocator.findStructures(type, worldSeed, config.radiusBlocks);

            if (positions.isEmpty()) continue;

            MarkerSet markerSet = MarkerSet.builder()
                    .label(type.displayName())
                    .toggleable(true)
                    .defaultHidden(false)
                    .build();

            for (StructureLocator.StructurePos pos : positions) {
                POIMarker marker = POIMarker.builder()
                        .label(type.displayName())
                        .detail(type.displayName() + " (" + pos.blockX() + ", " + pos.blockZ() + ")")
                        .position((double) pos.blockX(), 64.0, (double) pos.blockZ())
                        .maxDistance(10000)
                        .build();
                markerSet.getMarkers().put(
                        type.name().toLowerCase() + "_" + pos.blockX() + "_" + pos.blockZ(),
                        marker
                );
            }

            // Attach to matching dimension maps
            for (BlueMapMap map : api.getMaps()) {
                String worldId = map.getWorld().getId();
                if (matchesDimension(worldId, type.dimension)) {
                    map.getMarkerSets().put(type.markerSetId(), markerSet);
                }
            }

            totalMarkers += positions.size();
            BlueMapStructuresMod.LOGGER.info("Added {} {} markers", positions.size(), type.displayName());
        }

        BlueMapStructuresMod.LOGGER.info("BlueMap structure markers created: {} total", totalMarkers);
    }

    private static boolean matchesDimension(String worldId, StructureType.Dimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> worldId.contains("overworld");
            case NETHER -> worldId.contains("the_nether");
            case END -> worldId.contains("the_end");
        };
    }
}
