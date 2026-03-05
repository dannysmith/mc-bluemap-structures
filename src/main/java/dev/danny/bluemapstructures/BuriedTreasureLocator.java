package dev.danny.bluemapstructures;

import dev.danny.bluemapstructures.StructureLocator.StructurePos;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BuriedTreasureLocator {

  private static final long REGION_X_MULTIPLIER = 341873128712L;
  private static final long REGION_Z_MULTIPLIER = 132897987541L;
  private static final long SALT = 10387320L;
  private static final float FREQUENCY = 0.01f;

  public static List<StructurePos> findBuriedTreasure(
      long worldSeed, int radiusBlocks, BiomeValidator validator) {
    List<StructurePos> results = new ArrayList<>();
    int radiusChunks = radiusBlocks / 16;

    for (int chunkX = -radiusChunks; chunkX <= radiusChunks; chunkX++) {
      for (int chunkZ = -radiusChunks; chunkZ <= radiusChunks; chunkZ++) {
        long seed =
            (long) chunkX * REGION_X_MULTIPLIER
                + (long) chunkZ * REGION_Z_MULTIPLIER
                + worldSeed
                + SALT;
        Random rand = new Random(seed);
        if (rand.nextFloat() < FREQUENCY) {
          int blockX = chunkX * 16 + 9;
          int blockZ = chunkZ * 16 + 9;

          if (Math.abs(blockX) <= radiusBlocks && Math.abs(blockZ) <= radiusBlocks) {
            if (validator == null
                || validator.isValidPosition(StructureType.BURIED_TREASURE, blockX, blockZ)) {
              results.add(new StructurePos(blockX, blockZ, StructureType.BURIED_TREASURE));
            }
          }
        }
      }
    }

    return results;
  }
}
