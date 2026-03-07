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

  @ParameterizedTest(name = "village region ({0},{1})")
  @CsvSource({
    "-2,-2", "-2,-1", "-2,0", "-2,1", "-2,2", "-1,-2", "-1,-1", "-1,0", "-1,1", "-1,2", "0,-2",
    "0,-1", "0,0", "0,1", "0,2", "1,-2", "1,-1", "1,0", "1,1", "1,2", "2,-2", "2,-1", "2,0", "2,1",
    "2,2",
  })
  void village_rng_matches_minecraft(int rx, int rz) {
    assertLinearSpreadMatches(rx, rz, 34, 8, 10387312);
  }

  @ParameterizedTest(name = "trial_chambers region ({0},{1})")
  @CsvSource({
    "-2,-2", "-2,-1", "-2,0", "-2,1", "-2,2", "-1,-2", "-1,-1", "-1,0", "-1,1", "-1,2", "0,-2",
    "0,-1", "0,0", "0,1", "0,2", "1,-2", "1,-1", "1,0", "1,1", "1,2", "2,-2", "2,-1", "2,0", "2,1",
    "2,2",
  })
  void trial_chambers_rng_matches_minecraft(int rx, int rz) {
    assertLinearSpreadMatches(rx, rz, 34, 12, 94251327);
  }

  private void assertLinearSpreadMatches(int rx, int rz, int spacing, int separation, int salt) {
    int range = spacing - separation;

    // Our algorithm (same as StructureLocator.getStructureChunk)
    long regionSeed = (long) rx * 341873128712L + (long) rz * 132897987541L + SEED + (long) salt;
    java.util.Random ourRand = new java.util.Random(regionSeed);
    int ourChunkX = rx * spacing + ourRand.nextInt(range);
    int ourChunkZ = rz * spacing + ourRand.nextInt(range);

    // Minecraft's algorithm
    ChunkRandom mcRand = new ChunkRandom(new CheckedRandom(0L));
    mcRand.setRegionSeed(SEED, rx, rz, salt);
    int mcChunkX = rx * spacing + SpreadType.LINEAR.get(mcRand, range);
    int mcChunkZ = rz * spacing + SpreadType.LINEAR.get(mcRand, range);

    assertEquals(mcChunkX, ourChunkX, "ChunkX mismatch at region (" + rx + "," + rz + ")");
    assertEquals(mcChunkZ, ourChunkZ, "ChunkZ mismatch at region (" + rx + "," + rz + ")");
  }
}
