package dev.danny.bluemapstructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StructureLocator {

  // Minecraft's region seed multipliers — used by ChunkRandom.setRegionSeed() to mix
  // region coordinates into the seed. These are hardcoded constants in vanilla MC.
  private static final long REGION_X_MULTIPLIER = 341873128712L;
  private static final long REGION_Z_MULTIPLIER = 132897987541L;

  public record StructurePos(int blockX, int blockZ, StructureType type, boolean hasShip) {
    public StructurePos(int blockX, int blockZ, StructureType type) {
      this(blockX, blockZ, type, false);
    }
  }

  public static List<StructurePos> findStructures(
      StructureType type, long worldSeed, int radiusBlocks, BiomeValidator validator) {
    if (type == StructureType.STRONGHOLD) {
      return StrongholdLocator.findStrongholds(worldSeed, radiusBlocks);
    }

    if (type == StructureType.BURIED_TREASURE) {
      return BuriedTreasureLocator.findBuriedTreasure(worldSeed, radiusBlocks, validator);
    }

    // Fortress and Bastion share a grid — handle together
    if (type == StructureType.FORTRESS || type == StructureType.BASTION) {
      return findNetherComplex(type, worldSeed, radiusBlocks, validator);
    }

    List<StructurePos> results = new ArrayList<>();
    int spacing = type.spacing;
    int radiusChunks = radiusBlocks / 16;
    int regionMin = Math.floorDiv(-radiusChunks, spacing) - 1;
    int regionMax = Math.floorDiv(radiusChunks, spacing) + 1;

    for (int regionX = regionMin; regionX <= regionMax; regionX++) {
      for (int regionZ = regionMin; regionZ <= regionMax; regionZ++) {
        int[] chunk = getStructureChunk(type, worldSeed, regionX, regionZ);
        int blockX = chunk[0] * 16 + 8;
        int blockZ = chunk[1] * 16 + 8;

        if (Math.abs(blockX) <= radiusBlocks && Math.abs(blockZ) <= radiusBlocks) {
          if (validator == null || validator.isValidPosition(type, blockX, blockZ)) {
            boolean hasShip =
                type == StructureType.END_CITY
                    && EndCityShipDetector.hasShip(worldSeed, chunk[0], chunk[1]);
            results.add(new StructurePos(blockX, blockZ, type, hasShip));
          }
        }
      }
    }

    return results;
  }

  private static int[] getStructureChunk(
      StructureType type, long worldSeed, int regionX, int regionZ) {
    long regionSeed =
        (long) regionX * REGION_X_MULTIPLIER
            + (long) regionZ * REGION_Z_MULTIPLIER
            + worldSeed
            + (long) type.salt;
    Random rand = new Random(regionSeed);

    int range = type.spacing - type.separation;
    int chunkX, chunkZ;

    if (type.spreadType == StructureType.SpreadType.TRIANGULAR) {
      chunkX = regionX * type.spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2;
      chunkZ = regionZ * type.spacing + (rand.nextInt(range) + rand.nextInt(range)) / 2;
    } else {
      chunkX = regionX * type.spacing + rand.nextInt(range);
      chunkZ = regionZ * type.spacing + rand.nextInt(range);
    }

    return new int[] {chunkX, chunkZ};
  }

  private static List<StructurePos> findNetherComplex(
      StructureType requested, long worldSeed, int radiusBlocks, BiomeValidator validator) {
    List<StructurePos> results = new ArrayList<>();
    int spacing = 27;
    int separation = 4;
    int salt = 30084232;
    int radiusChunks = radiusBlocks / 16;
    int regionMin = Math.floorDiv(-radiusChunks, spacing) - 1;
    int regionMax = Math.floorDiv(radiusChunks, spacing) + 1;

    // Pre-compute the two multipliers for setCarverSeed (they only depend on worldSeed)
    Random carverInit = new Random(worldSeed);
    long carverMultA = carverInit.nextLong();
    long carverMultB = carverInit.nextLong();

    for (int regionX = regionMin; regionX <= regionMax; regionX++) {
      for (int regionZ = regionMin; regionZ <= regionMax; regionZ++) {
        long regionSeed =
            (long) regionX * REGION_X_MULTIPLIER
                + (long) regionZ * REGION_Z_MULTIPLIER
                + worldSeed
                + (long) salt;
        Random rand = new Random(regionSeed);

        int range = spacing - separation;
        int chunkX = regionX * spacing + rand.nextInt(range);
        int chunkZ = regionZ * spacing + rand.nextInt(range);

        // Weight roll uses setCarverSeed with the output chunk position (MC 1.18+)
        long carverSeed = (carverMultA * (long) chunkX) ^ (carverMultB * (long) chunkZ) ^ worldSeed;
        Random weightRand = new Random(carverSeed);
        int roll = weightRand.nextInt(5);
        boolean isFortress = roll < 2;

        StructureType actualType = isFortress ? StructureType.FORTRESS : StructureType.BASTION;
        if (actualType != requested) continue;

        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;

        if (Math.abs(blockX) <= radiusBlocks && Math.abs(blockZ) <= radiusBlocks) {
          if (validator == null || validator.isValidPosition(actualType, blockX, blockZ)) {
            results.add(new StructurePos(blockX, blockZ, actualType));
          }
        }
      }
    }

    return results;
  }
}
