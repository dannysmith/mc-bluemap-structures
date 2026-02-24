package dev.danny.bluemapstructures;

import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.gen.chunk.placement.SpreadType;

/**
 * Compare our algorithm against the actual Minecraft classes.
 * If these differ, our RNG seeding is wrong.
 */
public class TestAgainstMinecraft {
    public static void main(String[] args) {
        long seed = 12345L;
        int salt = 10387312; // village
        int spacing = 34;
        int separation = 8;
        int range = spacing - separation; // 26

        System.out.println("Comparing our algorithm vs Minecraft classes for seed " + seed);
        System.out.println("Structure: Village (spacing=34, sep=8, salt=10387312, linear)");
        System.out.println();
        System.out.printf("%-12s %-20s %-20s %-8s%n", "Region", "Ours (chunkX,Z)", "MC (chunkX,Z)", "Match?");
        System.out.println("-".repeat(65));

        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                // --- Our algorithm ---
                long regionSeed = (long) rx * 341873128712L
                        + (long) rz * 132897987541L
                        + seed + (long) salt;
                java.util.Random ourRand = new java.util.Random(regionSeed);
                int ourChunkX = rx * spacing + ourRand.nextInt(range);
                int ourChunkZ = rz * spacing + ourRand.nextInt(range);

                // --- Minecraft's algorithm ---
                ChunkRandom mcRand = new ChunkRandom(new CheckedRandom(0L));
                mcRand.setRegionSeed(seed, rx, rz, salt);
                int mcOffX = SpreadType.LINEAR.get(mcRand, range);
                int mcOffZ = SpreadType.LINEAR.get(mcRand, range);
                int mcChunkX = rx * spacing + mcOffX;
                int mcChunkZ = rz * spacing + mcOffZ;

                boolean match = (ourChunkX == mcChunkX && ourChunkZ == mcChunkZ);
                System.out.printf("(%2d, %2d)     (%4d, %4d)          (%4d, %4d)          %s%n",
                        rx, rz, ourChunkX, ourChunkZ, mcChunkX, mcChunkZ,
                        match ? "YES" : "NO <<<");
            }
        }
    }
}
