package dev.danny.bluemapstructures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Verifies our RNG seeding matches Minecraft's ChunkRandom.setRegionSeed(). If these tests fail,
 * the structure position algorithm is producing wrong results.
 */
class ChunkRandomVerificationTest {

  private static final long SEED = 12345L;
  private static final int VILLAGE_SALT = 10387312;
  private static final int VILLAGE_SPACING = 34;
  private static final int VILLAGE_SEPARATION = 8;

  @ParameterizedTest(name = "region ({0},{1})")
  @CsvSource({
    "-2,-2", "-2,-1", "-2,0", "-2,1", "-2,2", "-1,-2", "-1,-1", "-1,0", "-1,1", "-1,2", "0,-2",
    "0,-1", "0,0", "0,1", "0,2", "1,-2", "1,-1", "1,0", "1,1", "1,2", "2,-2", "2,-1", "2,0", "2,1",
    "2,2",
  })
  void our_rng_matches_minecraft_chunk_random(int rx, int rz) {
    int range = VILLAGE_SPACING - VILLAGE_SEPARATION;

    // Our algorithm (same as StructureLocator.getStructureChunk)
    long regionSeed =
        (long) rx * 341873128712L + (long) rz * 132897987541L + SEED + (long) VILLAGE_SALT;
    java.util.Random ourRand = new java.util.Random(regionSeed);
    int ourChunkX = rx * VILLAGE_SPACING + ourRand.nextInt(range);
    int ourChunkZ = rz * VILLAGE_SPACING + ourRand.nextInt(range);

    // Minecraft's algorithm
    ChunkRandom mcRand = new ChunkRandom(new CheckedRandom(0L));
    mcRand.setRegionSeed(SEED, rx, rz, VILLAGE_SALT);
    int mcChunkX = rx * VILLAGE_SPACING + SpreadType.LINEAR.get(mcRand, range);
    int mcChunkZ = rz * VILLAGE_SPACING + SpreadType.LINEAR.get(mcRand, range);

    assertEquals(mcChunkX, ourChunkX, "ChunkX mismatch at region (" + rx + "," + rz + ")");
    assertEquals(mcChunkZ, ourChunkZ, "ChunkZ mismatch at region (" + rx + "," + rz + ")");
  }
}
