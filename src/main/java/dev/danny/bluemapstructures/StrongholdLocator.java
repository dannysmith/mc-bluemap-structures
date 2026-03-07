package dev.danny.bluemapstructures;

import dev.danny.bluemapstructures.StructureLocator.StructurePos;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;

public class StrongholdLocator {

  private static final int COUNT = 128;
  private static final int DISTANCE = 32;
  private static final int SPREAD = 3;

  public static List<StructurePos> findStrongholds(
      long worldSeed, int radiusBlocks, ServerWorld world) {
    if (world != null) {
      List<StructurePos> vanillaPositions = readVanillaPositions(world, radiusBlocks);
      if (vanillaPositions != null) {
        return vanillaPositions;
      }
    }
    return findStrongholdsGeometric(worldSeed, radiusBlocks);
  }

  /**
   * Reads pre-computed stronghold positions from vanilla's StructurePlacementCalculator. These
   * positions include biome snapping, so they're accurate to the actual in-game locations.
   */
  private static List<StructurePos> readVanillaPositions(ServerWorld world, int radiusBlocks) {
    try {
      StructurePlacementCalculator calculator =
          world.getChunkManager().getStructurePlacementCalculator();

      for (var setEntry : calculator.getStructureSets()) {
        StructureSet set = setEntry.value();
        if (set.placement() instanceof ConcentricRingsStructurePlacement concentric) {
          List<ChunkPos> chunks = calculator.getPlacementPositions(concentric);
          List<StructurePos> results = new ArrayList<>();

          for (ChunkPos chunk : chunks) {
            int blockX = chunk.getStartX() + 8;
            int blockZ = chunk.getStartZ() + 8;
            if (Math.abs(blockX) <= radiusBlocks && Math.abs(blockZ) <= radiusBlocks) {
              results.add(new StructurePos(blockX, blockZ, StructureType.STRONGHOLD));
            }
          }

          BlueMapStructuresMod.LOGGER.info(
              "Read {} stronghold positions from vanilla (biome-snapped)", results.size());
          return results;
        }
      }
    } catch (Exception e) {
      BlueMapStructuresMod.LOGGER.warn(
          "Failed to read vanilla stronghold positions, using geometric fallback", e);
    }
    return null;
  }

  /** Geometric ring placement without biome snapping. Used as fallback (tests, errors). */
  static List<StructurePos> findStrongholdsGeometric(long worldSeed, int radiusBlocks) {
    List<StructurePos> results = new ArrayList<>();
    Random rand = new Random(worldSeed);

    int ringCount = SPREAD;
    double angle = rand.nextDouble() * Math.PI * 2.0;
    int placed = 0;
    int ring = 0;

    for (int i = 0; i < COUNT; i++) {
      double dist =
          (4.0 * DISTANCE + (double) DISTANCE * ring * 6.0)
              + (rand.nextDouble() - 0.5) * (double) DISTANCE * 2.5;
      int chunkX = (int) Math.round(Math.cos(angle) * dist);
      int chunkZ = (int) Math.round(Math.sin(angle) * dist);

      int blockX = chunkX * 16 + 8;
      int blockZ = chunkZ * 16 + 8;

      if (Math.abs(blockX) <= radiusBlocks && Math.abs(blockZ) <= radiusBlocks) {
        results.add(new StructurePos(blockX, blockZ, StructureType.STRONGHOLD));
      }

      angle += Math.PI * 2.0 / ringCount;
      placed++;

      if (placed == ringCount) {
        ring++;
        placed = 0;
        ringCount += 2 * ringCount / (ring + 1);
        if (ringCount > COUNT - i - 1) {
          ringCount = COUNT - i - 1;
        }
        angle += rand.nextDouble() * Math.PI * 2.0;
      }
    }

    return results;
  }
}
