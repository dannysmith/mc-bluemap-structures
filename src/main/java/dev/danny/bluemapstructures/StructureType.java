package dev.danny.bluemapstructures;

import java.util.List;

public enum StructureType {
    // Overworld - Linear spread
    VILLAGE(34, 8, 10387312, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
    DESERT_PYRAMID(32, 8, 14357617, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
    JUNGLE_TEMPLE(32, 8, 14357619, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
    SWAMP_HUT(32, 8, 14357620, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
    IGLOO(32, 8, 14357618, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
    PILLAGER_OUTPOST(32, 8, 165745296, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, false),
    ANCIENT_CITY(24, 8, 20083232, SpreadType.LINEAR, Dimension.OVERWORLD, 3000, false),
    TRAIL_RUINS(34, 8, 83469867, SpreadType.LINEAR, Dimension.OVERWORLD, 3000, false),
    TRIAL_CHAMBERS(34, 12, 94251327, SpreadType.LINEAR, Dimension.OVERWORLD, 3000, false),
    OCEAN_RUIN(20, 8, 14357621, SpreadType.LINEAR, Dimension.OVERWORLD, 500, true),
    SHIPWRECK(24, 4, 165745295, SpreadType.LINEAR, Dimension.OVERWORLD, 500, true),
    RUINED_PORTAL_OW(40, 15, 34222645, SpreadType.LINEAR, Dimension.OVERWORLD, 1500, true),

    // Overworld - Triangular spread
    MONUMENT(32, 5, 10387313, SpreadType.TRIANGULAR, Dimension.OVERWORLD, 1500, false),
    MANSION(80, 20, 10387319, SpreadType.TRIANGULAR, Dimension.OVERWORLD, 10000, false),

    // Nether
    FORTRESS(27, 4, 30084232, SpreadType.LINEAR, Dimension.NETHER, 1500, false),
    BASTION(27, 4, 30084232, SpreadType.LINEAR, Dimension.NETHER, 1500, false),
    RUINED_PORTAL_NETHER(25, 10, 34222645, SpreadType.LINEAR, Dimension.NETHER, 1500, true),

    // End
    END_CITY(20, 11, 10387313, SpreadType.TRIANGULAR, Dimension.END, 10000, false),

    // Special
    STRONGHOLD(0, 0, 0, SpreadType.CONCENTRIC_RINGS, Dimension.OVERWORLD, 10000, false);

    public final int spacing;
    public final int separation;
    public final int salt;
    public final SpreadType spreadType;
    public final Dimension dimension;
    public final int maxDistance;
    public final boolean defaultHidden;

    StructureType(int spacing, int separation, int salt, SpreadType spreadType, Dimension dimension,
                  int maxDistance, boolean defaultHidden) {
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
            case STRONGHOLD -> "Strongholds";
        };
    }

    /**
     * Returns biome tag paths (e.g. "has_structure/village_plains") that this structure
     * can spawn in. Empty list means skip biome checking (structure spawns in many biomes).
     */
    public List<String> biomeTagIds() {
        return switch (this) {
            case VILLAGE -> List.of(
                    "has_structure/village_plains",
                    "has_structure/village_desert",
                    "has_structure/village_savanna",
                    "has_structure/village_snowy",
                    "has_structure/village_taiga"
            );
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
            // Skip biome checking — spawns in many biomes or uses special placement
            case RUINED_PORTAL_OW, RUINED_PORTAL_NETHER, STRONGHOLD -> List.of();
        };
    }

    public String markerSetId() {
        return "structures-" + name().toLowerCase();
    }

    public enum SpreadType {
        LINEAR, TRIANGULAR, CONCENTRIC_RINGS
    }

    public enum Dimension {
        OVERWORLD, NETHER, END
    }
}
