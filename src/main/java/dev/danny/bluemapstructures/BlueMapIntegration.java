package dev.danny.bluemapstructures;

import com.flowpowered.math.vector.Vector2i;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlueMapIntegration {

  private static final Vector2i ICON_ANCHOR = new Vector2i(11, 11); // center of 22x22 icon

  private static Consumer<BlueMapAPI> enableListener;
  private static Consumer<BlueMapAPI> disableListener;
  private static final List<String> registeredMarkerSetIds = new ArrayList<>();

  public static void register(MinecraftServer server, long worldSeed, ModConfig config) {
    unregister();

    enableListener =
        api -> {
          BlueMapStructuresMod.LOGGER.info("BlueMap API enabled, creating structure markers...");
          createMarkers(api, server, worldSeed, config);
        };

    disableListener =
        api -> {
          BlueMapStructuresMod.LOGGER.info("BlueMap API disabling, removing structure markers...");
          removeMarkers(api);
        };

    BlueMapAPI.onEnable(enableListener);
    BlueMapAPI.onDisable(disableListener);
  }

  public static void unregister() {
    if (enableListener != null) {
      BlueMapAPI.unregisterListener(enableListener);
      enableListener = null;
    }
    if (disableListener != null) {
      BlueMapAPI.unregisterListener(disableListener);
      disableListener = null;
    }
    registeredMarkerSetIds.clear();
  }

  private static void removeMarkers(BlueMapAPI api) {
    for (BlueMapMap map : api.getMaps()) {
      for (String id : registeredMarkerSetIds) {
        map.getMarkerSets().remove(id);
      }
    }
    registeredMarkerSetIds.clear();
  }

  private static Map<StructureType, String> uploadIcons(BlueMapAPI api) {
    Map<StructureType, String> iconUrls = new EnumMap<>(StructureType.class);

    for (StructureType type : StructureType.values()) {
      String url = uploadIcon(api, "/icons/" + type.iconFile(), "structures/" + type.iconFile());
      if (url != null) {
        iconUrls.put(type, url);
      }
    }

    BlueMapStructuresMod.LOGGER.info("Uploaded {} structure icons", iconUrls.size());
    return iconUrls;
  }

  private static void createMarkers(
      BlueMapAPI api, MinecraftServer server, long worldSeed, ModConfig config) {
    Map<StructureType.Dimension, BiomeValidator> validators =
        new EnumMap<>(StructureType.Dimension.class);
    for (StructureType.Dimension dim : StructureType.Dimension.values()) {
      ServerWorld world = getServerWorld(server, dim);
      if (world != null) {
        validators.put(dim, new BiomeValidator(world));
      }
    }

    for (BlueMapMap map : api.getMaps()) {
      BlueMapStructuresMod.LOGGER.info(
          "BlueMap map '{}' has world ID '{}'", map.getId(), map.getWorld().getId());
    }

    Map<StructureType, String> iconUrls = uploadIcons(api);
    String endShipIconUrl = uploadIcon(api, "/icons/end_ship.png", "structures/end_ship.png");

    int totalMarkers = 0;

    for (StructureType type : StructureType.values()) {
      if (!config.isEnabled(type)) continue;

      BiomeValidator validator = validators.get(type.dimension);
      ServerWorld world = getServerWorld(server, type.dimension);
      int effectiveRadius = config.radiusBlocks;
      List<StructureLocator.StructurePos> positions =
          StructureLocator.findStructures(type, worldSeed, effectiveRadius, validator, world);

      if (positions.isEmpty()) continue;

      MarkerSet markerSet =
          MarkerSet.builder()
              .label(type.displayName())
              .toggleable(true)
              .defaultHidden(type.defaultHidden)
              .build();

      String iconUrl = iconUrls.get(type);

      for (StructureLocator.StructurePos pos : positions) {
        boolean isShip = pos.hasShip() && type == StructureType.END_CITY;
        String label = isShip ? "End City (Ship)" : type.displayName();
        POIMarker.Builder builder =
            POIMarker.builder()
                .label(label)
                .detail(buildDetail(label, pos.blockX(), pos.blockZ()))
                .position((double) pos.blockX(), 64.0, (double) pos.blockZ())
                .maxDistance(type.maxDistance);

        String markerIconUrl = (isShip && endShipIconUrl != null) ? endShipIconUrl : iconUrl;
        if (markerIconUrl != null) {
          builder.icon(markerIconUrl, ICON_ANCHOR);
        }

        markerSet
            .getMarkers()
            .put(
                type.name().toLowerCase() + "_" + pos.blockX() + "_" + pos.blockZ(),
                builder.build());
      }

      String markerSetId = type.markerSetId();
      registeredMarkerSetIds.add(markerSetId);

      for (BlueMapMap map : api.getMaps()) {
        String worldId = map.getWorld().getId();
        if (matchesDimension(worldId, type.dimension)) {
          map.getMarkerSets().put(markerSetId, markerSet);
        }
      }

      totalMarkers += positions.size();
      BlueMapStructuresMod.LOGGER.info("Added {} {} markers", positions.size(), type.displayName());
    }

    if (config.enableSpawn) {
      createSpawnMarker(api, server);
      totalMarkers++;
    }

    BlueMapStructuresMod.LOGGER.info("BlueMap structure markers created: {} total", totalMarkers);
  }

  private static void createSpawnMarker(BlueMapAPI api, MinecraftServer server) {
    BlockPos spawn = server.getOverworld().getLevelProperties().getSpawnPoint().getPos();

    String iconUrl = uploadIcon(api, "/icons/spawn.png", "structures/spawn.png");

    POIMarker.Builder builder =
        POIMarker.builder()
            .label("World Spawn")
            .detail("World Spawn (" + spawn.getX() + ", " + spawn.getZ() + ")")
            .position((double) spawn.getX(), (double) spawn.getY(), (double) spawn.getZ())
            .maxDistance(10000);

    if (iconUrl != null) {
      builder.icon(iconUrl, ICON_ANCHOR);
    }

    MarkerSet markerSet =
        MarkerSet.builder().label("World Spawn").toggleable(true).defaultHidden(false).build();

    markerSet.getMarkers().put("spawn", builder.build());

    String markerSetId = "structures-spawn";
    registeredMarkerSetIds.add(markerSetId);

    for (BlueMapMap map : api.getMaps()) {
      String worldId = map.getWorld().getId();
      if (matchesDimension(worldId, StructureType.Dimension.OVERWORLD)) {
        map.getMarkerSets().put(markerSetId, markerSet);
      }
    }

    BlueMapStructuresMod.LOGGER.info("Added spawn marker at {}, {}", spawn.getX(), spawn.getZ());
  }

  private static String uploadIcon(BlueMapAPI api, String resourcePath, String assetName) {
    String iconUrl = null;
    for (BlueMapMap map : api.getMaps()) {
      try (InputStream in = BlueMapIntegration.class.getResourceAsStream(resourcePath)) {
        if (in == null) {
          BlueMapStructuresMod.LOGGER.warn("Icon resource not found: {}", resourcePath);
          break;
        }
        try (OutputStream out = map.getAssetStorage().writeAsset(assetName)) {
          in.transferTo(out);
        }
        if (iconUrl == null) {
          iconUrl = map.getAssetStorage().getAssetUrl(assetName);
        }
      } catch (IOException e) {
        BlueMapStructuresMod.LOGGER.warn(
            "Failed to upload icon {}: {}", resourcePath, e.getMessage());
      }
    }
    return iconUrl;
  }

  private static ServerWorld getServerWorld(
      MinecraftServer server, StructureType.Dimension dimension) {
    return switch (dimension) {
      case OVERWORLD -> server.getOverworld();
      case NETHER -> server.getWorld(World.NETHER);
      case END -> server.getWorld(World.END);
    };
  }

  private static String buildDetail(String label, int blockX, int blockZ) {
    String tpCommand = "/tp @p " + blockX + " ~ " + blockZ;
    return label
        + " ("
        + blockX
        + ", "
        + blockZ
        + ")"
        + "<br>"
        + "<input type=\"text\" value=\""
        + tpCommand
        + "\" readonly onfocus=\"this.select()\""
        + " style=\"pointer-events:auto;user-select:text;"
        + "width:100%;font-family:monospace;font-size:12px;"
        + "background:#1a1a2e;color:#eee;border:1px solid #444;"
        + "padding:3px 5px;margin-top:4px;cursor:text;box-sizing:border-box\">";
  }

  private static boolean matchesDimension(String worldId, StructureType.Dimension dimension) {
    return switch (dimension) {
      case OVERWORLD -> worldId.contains("overworld");
      case NETHER -> worldId.contains("the_nether");
      case END -> worldId.contains("the_end");
    };
  }
}
