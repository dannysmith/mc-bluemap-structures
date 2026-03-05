package dev.danny.bluemapstructures;

import java.util.Comparator;
import java.util.List;

/**
 * Standalone test — prints structure positions for a known seed. Compare output against Chunkbase
 * to verify the algorithm.
 *
 * <p>Run: ./gradlew run
 */
public class TestPositions {
  public static void main(String[] args) {
    long seed = 12345L;
    int radius = 2000;

    System.out.println("Seed: " + seed);
    System.out.println("Radius: " + radius + " blocks");
    System.out.println();

    // Test a few representative types:
    // - Village (linear, common)
    // - Monument (triangular)
    // - Mansion (triangular, large spacing)
    // - Fortress + Bastion (shared grid, weight roll)
    // - Stronghold (concentric rings)
    StructureType[] testTypes = {
      StructureType.VILLAGE,
      StructureType.DESERT_PYRAMID,
      StructureType.MONUMENT,
      StructureType.FORTRESS,
      StructureType.BASTION,
      StructureType.STRONGHOLD,
    };

    for (StructureType type : testTypes) {
      List<StructureLocator.StructurePos> positions =
          StructureLocator.findStructures(type, seed, radius, null);

      // Sort by distance from origin for easy comparison
      positions.sort(
          Comparator.comparingDouble(
              p -> Math.sqrt((double) p.blockX() * p.blockX() + (double) p.blockZ() * p.blockZ())));

      System.out.println("=== " + type.displayName() + " (" + type.name() + ") ===");
      System.out.println("  Count: " + positions.size());
      System.out.printf("  %-8s %-8s %-8s%n", "BlockX", "BlockZ", "ChunkX/Z");
      for (StructureLocator.StructurePos pos : positions) {
        int cx = (pos.blockX() - 8) / 16;
        int cz = (pos.blockZ() - 8) / 16;
        System.out.printf("  %-8d %-8d (%d, %d)%n", pos.blockX(), pos.blockZ(), cx, cz);
      }
      System.out.println();
    }
  }
}
