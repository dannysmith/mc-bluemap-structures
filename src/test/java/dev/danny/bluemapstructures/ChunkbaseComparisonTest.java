package dev.danny.bluemapstructures;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Compares our structure-finding algorithms against Chunkbase's output. The Chunkbase data was
 * extracted using the Playwright tool in tools/chunkbase-verify/.
 *
 * <p>This test loads a JSON file of Chunkbase positions for seed 12345 and compares against our
 * algorithm output for the same seed. It reports matches, false positives (we predict, Chunkbase
 * doesn't), and false negatives (Chunkbase predicts, we don't) per structure type.
 */
class ChunkbaseComparisonTest {

  private static final long SEED = 12345L;
  private static final int RADIUS = 10000;
  private static final String CHUNKBASE_FILE = "/chunkbase-seed-12345.json";

  /** Chunkbase type ID -> our StructureType enum. */
  private static final Map<String, StructureType> TYPE_MAP = new HashMap<>();

  static {
    TYPE_MAP.put("village", StructureType.VILLAGE);
    TYPE_MAP.put("desertTemple", StructureType.DESERT_PYRAMID);
    TYPE_MAP.put("jungleTemple", StructureType.JUNGLE_TEMPLE);
    TYPE_MAP.put("witchHut", StructureType.SWAMP_HUT);
    TYPE_MAP.put("igloo", StructureType.IGLOO);
    TYPE_MAP.put("pillagerOutpost", StructureType.PILLAGER_OUTPOST);
    TYPE_MAP.put("ancientCity", StructureType.ANCIENT_CITY);
    TYPE_MAP.put("trailRuin", StructureType.TRAIL_RUINS);
    TYPE_MAP.put("trialChamber", StructureType.TRIAL_CHAMBERS);
    TYPE_MAP.put("oceanRuin", StructureType.OCEAN_RUIN);
    TYPE_MAP.put("shipwreck", StructureType.SHIPWRECK);
    TYPE_MAP.put("ruinedPortalOverworld", StructureType.RUINED_PORTAL_OW);
    TYPE_MAP.put("oceanMonument", StructureType.MONUMENT);
    TYPE_MAP.put("woodlandMansion", StructureType.MANSION);
    TYPE_MAP.put("netherFortress", StructureType.FORTRESS);
    TYPE_MAP.put("bastionRemnant", StructureType.BASTION);
    TYPE_MAP.put("ruinedPortalNether", StructureType.RUINED_PORTAL_NETHER);
    TYPE_MAP.put("endCity", StructureType.END_CITY);
    TYPE_MAP.put("buriedTreasure", StructureType.BURIED_TREASURE);
    TYPE_MAP.put("stronghold", StructureType.STRONGHOLD);
  }

  /** Chunkbase type IDs we extract but don't yet implement (skip comparison, just log). */
  private static final Set<String> UNIMPLEMENTED_TYPES = Set.of("dungeon", "spawn");

  private record Pos(int x, int z) {}

  private static Map<StructureType, Set<Pos>> chunkbasePositions;

  @BeforeAll
  static void loadChunkbaseData() {
    var stream = ChunkbaseComparisonTest.class.getResourceAsStream(CHUNKBASE_FILE);
    assertNotNull(stream, "Chunkbase data file not found: " + CHUNKBASE_FILE);

    JsonObject root = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
    JsonArray structures = root.getAsJsonArray("structures");

    chunkbasePositions = new EnumMap<>(StructureType.class);
    int skipped = 0;

    for (JsonElement el : structures) {
      JsonObject s = el.getAsJsonObject();
      String cbType = s.get("type").getAsString();

      if (UNIMPLEMENTED_TYPES.contains(cbType)) {
        skipped++;
        continue;
      }

      StructureType type = TYPE_MAP.get(cbType);
      if (type == null) {
        // Unknown type — skip silently
        skipped++;
        continue;
      }

      int x = s.get("x").getAsInt();
      int z = s.get("z").getAsInt();
      chunkbasePositions.computeIfAbsent(type, k -> new HashSet<>()).add(new Pos(x, z));
    }

    System.out.println(
        "Loaded Chunkbase data: "
            + structures.size()
            + " total, "
            + skipped
            + " skipped (unimplemented/unknown)");
    for (var entry :
        chunkbasePositions.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().name()))
            .toList()) {
      System.out.println("  " + entry.getKey().displayName() + ": " + entry.getValue().size());
    }
  }

  @Test
  void compare_all_structure_types() {
    System.out.println("\n=== Chunkbase Comparison (seed " + SEED + ", radius " + RADIUS + ") ===");

    int totalMatches = 0;
    int totalFalsePos = 0;
    int totalFalseNeg = 0;
    int typesCompared = 0;

    for (StructureType type : StructureType.values()) {
      Set<Pos> cbSet = chunkbasePositions.getOrDefault(type, Set.of());
      if (cbSet.isEmpty()) {
        System.out.println("\n" + type.displayName() + ": no Chunkbase data, skipping");
        continue;
      }

      List<StructureLocator.StructurePos> ours =
          StructureLocator.findStructures(type, SEED, RADIUS, null);
      Set<Pos> ourSet = new HashSet<>();
      for (var pos : ours) {
        ourSet.add(new Pos(pos.blockX(), pos.blockZ()));
      }

      // Chunk-level matching: positions are "same" if within tolerance blocks.
      // Trial chambers need 17 because Chunkbase reports entry positions with
      // block offsets of 7/9 (not chunk center 8), causing 1-block overshoot
      // when our chunk coordinate differs by 1 from Chunkbase's.
      int tolerance = type == StructureType.TRIAL_CHAMBERS ? 17 : 16;
      Set<Pos> matches = new HashSet<>();
      Set<Pos> falsePositives = new HashSet<>(ourSet);
      Set<Pos> falseNegatives = new HashSet<>(cbSet);

      for (Pos ourPos : ourSet) {
        Pos matched = findNearby(cbSet, ourPos, tolerance);
        if (matched != null) {
          matches.add(ourPos);
          falsePositives.remove(ourPos);
          falseNegatives.remove(matched);
        }
      }

      int matchCount = matches.size();
      int fpCount = falsePositives.size();
      int fnCount = falseNegatives.size();

      totalMatches += matchCount;
      totalFalsePos += fpCount;
      totalFalseNeg += fnCount;
      typesCompared++;

      double matchRate =
          cbSet.isEmpty() ? 0 : (double) matchCount / Math.max(ourSet.size(), cbSet.size()) * 100;

      System.out.printf(
          "\n%s: ours=%d, chunkbase=%d, matches=%d, FP=%d, FN=%d (%.1f%% match)\n",
          type.displayName(), ourSet.size(), cbSet.size(), matchCount, fpCount, fnCount, matchRate);

      // Log some example false positives/negatives for debugging
      if (fpCount > 0 && fpCount <= 10) {
        System.out.println("  False positives (we predict, CB doesn't): " + falsePositives);
      } else if (fpCount > 10) {
        System.out.println(
            "  False positives (first 10): " + falsePositives.stream().limit(10).toList() + " ...");
      }

      if (fnCount > 0 && fnCount <= 10) {
        System.out.println("  False negatives (CB predicts, we don't): " + falseNegatives);
      } else if (fnCount > 10) {
        System.out.println(
            "  False negatives (first 10): " + falseNegatives.stream().limit(10).toList() + " ...");
      }
    }

    System.out.printf(
        "\n=== TOTALS: %d types compared, %d matches, %d FP, %d FN ===\n",
        typesCompared, totalMatches, totalFalsePos, totalFalseNeg);

    // Assertion: we should match at least some structures overall
    assertTrue(totalMatches > 0, "Should have at least some matches against Chunkbase");
  }

  /**
   * Finds a position in the target set that is within `tolerance` blocks of the given position.
   * Returns the matched position, or null if none found.
   */
  private static Pos findNearby(Set<Pos> targets, Pos pos, int tolerance) {
    // Exact match first (fast path)
    if (targets.contains(pos)) return pos;

    // Nearby search
    for (Pos target : targets) {
      if (Math.abs(target.x - pos.x) <= tolerance && Math.abs(target.z - pos.z) <= tolerance) {
        return target;
      }
    }
    return null;
  }
}
