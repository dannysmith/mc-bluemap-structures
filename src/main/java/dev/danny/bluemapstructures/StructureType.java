package dev.danny.bluemapstructures;

import java.util.List;

public enum StructureType {
  // Overworld - Linear spread
  VILLAGE(34, 8, 10387312, SpreadType.LINEAR, Dimension.OVERWORLD, 1000, false),
  DESERT_PYRAMID(32, 8, 14357617, SpreadType.LINEAR, Dimension.OVERWORLD, 1000, false),
  JUNGLE_TEMPLE(32, 8, 14357619, SpreadType.LINEAR, Dimension.OVERWORLD, 1000, false),
  SWAMP_HUT(32, 8, 14357620, SpreadType.LINEAR, Dimension.OVERWORLD, 1000, false),
  IGLOO(32, 8, 14357618, SpreadType.LINEAR, Dimension.OVERWORLD, 1000, false),
  PILLAGER_OUTPOST(32, 8, 165745296, SpreadType.LINEAR, Dimension.OVERWORLD, 1000, false),
  ANCIENT_CITY(24, 8, 20083232, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
  TRAIL_RUINS(34, 8, 83469867, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
  TRIAL_CHAMBERS(34, 12, 94251327, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
  OCEAN_RUIN(20, 8, 14357621, SpreadType.LINEAR, Dimension.OVERWORLD, 300, true),
  SHIPWRECK(24, 4, 165745295, SpreadType.LINEAR, Dimension.OVERWORLD, 300, true),
  RUINED_PORTAL_OW(40, 15, 34222645, SpreadType.LINEAR, Dimension.OVERWORLD, 800, true),

  // Overworld - Triangular spread
  MONUMENT(32, 5, 10387313, SpreadType.TRIANGULAR, Dimension.OVERWORLD, 1000, false),
  MANSION(80, 20, 10387319, SpreadType.TRIANGULAR, Dimension.OVERWORLD, 5000, false),

  // Nether
  FORTRESS(27, 4, 30084232, SpreadType.LINEAR, Dimension.NETHER, 1000, false),
  BASTION(27, 4, 30084232, SpreadType.LINEAR, Dimension.NETHER, 1000, false),
  RUINED_PORTAL_NETHER(25, 10, 34222645, SpreadType.LINEAR, Dimension.NETHER, 500, true),

  // End
  END_CITY(20, 11, 10387313, SpreadType.TRIANGULAR, Dimension.END, 5000, false),

  // Special
  BURIED_TREASURE(1, 0, 10387320, SpreadType.LINEAR, Dimension.OVERWORLD, 300, true),
  STRONGHOLD(0, 0, 0, SpreadType.CONCENTRIC_RINGS, Dimension.OVERWORLD, 5000, false);

  public final int spacing;
  public final int separation;
  public final int salt;
  public final SpreadType spreadType;
  public final Dimension dimension;
  public final int maxDistance;
  public final boolean defaultHidden;

  StructureType(
      int spacing,
      int separation,
      int salt,
      SpreadType spreadType,
      Dimension dimension,
      int maxDistance,
      boolean defaultHidden) {
    this.spacing = spacing;
    this.separation = separation;
    this.salt = salt;
    this.spreadType = spreadType;
    this.dimension = dimension;
    this.maxDistance = maxDistance;
    this.defaultHidden = defaultHidden;
  }

  public String displayName() {
    return switch (this) {
      case VILLAGE -> "Villages";
      case DESERT_PYRAMID -> "Desert Pyramids";
      case JUNGLE_TEMPLE -> "Jungle Temples";
      case SWAMP_HUT -> "Swamp Huts";
      case IGLOO -> "Igloos";
      case PILLAGER_OUTPOST -> "Pillager Outposts";
      case MONUMENT -> "Ocean Monuments";
      case MANSION -> "Woodland Mansions";
      case ANCIENT_CITY -> "Ancient Cities";
      case TRAIL_RUINS -> "Trail Ruins";
      case TRIAL_CHAMBERS -> "Trial Chambers";
      case OCEAN_RUIN -> "Ocean Ruins";
      case SHIPWRECK -> "Shipwrecks";
      case RUINED_PORTAL_OW -> "Ruined Portals (OW)";
      case FORTRESS -> "Nether Fortresses";
      case BASTION -> "Bastion Remnants";
      case RUINED_PORTAL_NETHER -> "Ruined Portals (Nether)";
      case END_CITY -> "End Cities";
      case BURIED_TREASURE -> "Buried Treasure";
      case STRONGHOLD -> "Strongholds";
    };
  }

  /**
   * Returns biome tag paths (e.g. "has_structure/village_plains") that this structure can spawn in.
   * Empty list means skip biome checking (structure spawns in many biomes).
   */
  public List<String> biomeTagIds() {
    return switch (this) {
      case VILLAGE ->
          List.of(
              "has_structure/village_plains",
              "has_structure/village_desert",
              "has_structure/village_savanna",
              "has_structure/village_snowy",
              "has_structure/village_taiga");
      case DESERT_PYRAMID -> List.of("has_structure/desert_pyramid");
      case JUNGLE_TEMPLE -> List.of("has_structure/jungle_temple");
      case SWAMP_HUT -> List.of("has_structure/swamp_hut");
      case IGLOO -> List.of("has_structure/igloo");
      case PILLAGER_OUTPOST -> List.of("has_structure/pillager_outpost");
      case ANCIENT_CITY -> List.of("has_structure/ancient_city");
      case TRAIL_RUINS -> List.of("has_structure/trail_ruins");
      case TRIAL_CHAMBERS -> List.of("has_structure/trial_chambers");
      case OCEAN_RUIN -> List.of("has_structure/ocean_ruin_cold", "has_structure/ocean_ruin_warm");
      case SHIPWRECK -> List.of("has_structure/shipwreck", "has_structure/shipwreck_beached");
      case MONUMENT -> List.of("has_structure/ocean_monument");
      case MANSION -> List.of("has_structure/woodland_mansion");
      case FORTRESS -> List.of("has_structure/nether_fortress");
      case BASTION -> List.of("has_structure/bastion_remnant");
      case END_CITY -> List.of("has_structure/end_city");
      case BURIED_TREASURE -> List.of("has_structure/buried_treasure");
      // Skip biome checking — spawns in many biomes or uses special placement
      case RUINED_PORTAL_OW, RUINED_PORTAL_NETHER, STRONGHOLD -> List.of();
    };
  }

  public String markerSetId() {
    return "structures-" + name().toLowerCase();
  }

  public String iconFile() {
    return switch (this) {
      case VILLAGE -> "village.png";
      case DESERT_PYRAMID -> "desert_temple.png";
      case JUNGLE_TEMPLE -> "jungle_temple.png";
      case SWAMP_HUT -> "witch_hut.png";
      case IGLOO -> "igloo.png";
      case PILLAGER_OUTPOST -> "outpost.png";
      case ANCIENT_CITY -> "ancient_city.png";
      case TRAIL_RUINS -> "trail_ruins.png";
      case TRIAL_CHAMBERS -> "trial_chamber.png";
      case OCEAN_RUIN -> "ocean_ruins.png";
      case SHIPWRECK -> "shipwreck.png";
      case RUINED_PORTAL_OW -> "ruined_portal_ow.png";
      case MONUMENT -> "monument.png";
      case MANSION -> "mansion.png";
      case FORTRESS -> "nether_fortress.png";
      case BASTION -> "bastion.png";
      case RUINED_PORTAL_NETHER -> "ruined_portal_nether.png";
      case END_CITY -> "end_city.png";
      case BURIED_TREASURE -> "treasure.png";
      case STRONGHOLD -> "stronghold.png";
    };
  }

  public enum SpreadType {
    LINEAR,
    TRIANGULAR,
    CONCENTRIC_RINGS
  }

  public enum Dimension {
    OVERWORLD,
    NETHER,
    END
  }
}
