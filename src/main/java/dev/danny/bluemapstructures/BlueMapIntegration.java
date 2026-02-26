package dev.danny.bluemapstructures;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BlueMapIntegration {

    public static void register(MinecraftServer server, long worldSeed, ModConfig config) {
        BlueMapAPI.onEnable(api -> {
            BlueMapStructuresMod.LOGGER.info("BlueMap API enabled, creating structure markers...");
            createMarkers(api, server, worldSeed, config);
        });
    }

    private static void createMarkers(BlueMapAPI api, MinecraftServer server, long worldSeed, ModConfig config) {
        Map<StructureType.Dimension, BiomeValidator> validators = new EnumMap<>(StructureType.Dimension.class);
        for (StructureType.Dimension dim : StructureType.Dimension.values()) {
            ServerWorld world = getServerWorld(server, dim);
            if (world != null) {
                validators.put(dim, new BiomeValidator(world));
            }
        }

        int totalMarkers = 0;

        for (StructureType type : StructureType.values()) {
            if (!config.isEnabled(type)) continue;

            BiomeValidator validator = validators.get(type.dimension);
            List<StructureLocator.StructurePos> positions =
                    StructureLocator.findStructures(type, worldSeed, config.radiusBlocks, validator);

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

    private static ServerWorld getServerWorld(MinecraftServer server, StructureType.Dimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> server.getOverworld();
            case NETHER -> server.getWorld(World.NETHER);
            case END -> server.getWorld(World.END);
        };
    }

    private static boolean matchesDimension(String worldId, StructureType.Dimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> worldId.contains("overworld");
            case NETHER -> worldId.contains("the_nether");
            case END -> worldId.contains("the_end");
        };
    }
}
