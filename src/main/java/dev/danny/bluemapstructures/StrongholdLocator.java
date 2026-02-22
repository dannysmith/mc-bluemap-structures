package dev.danny.bluemapstructures;

import dev.danny.bluemapstructures.StructureLocator.StructurePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StrongholdLocator {

    private static final int COUNT = 128;
    private static final int DISTANCE = 32;
    private static final int SPREAD = 3;

    public static List<StructurePos> findStrongholds(long worldSeed, int radiusBlocks) {
        List<StructurePos> results = new ArrayList<>();
        Random rand = new Random(worldSeed);

        int ringCount = SPREAD;
        double ringDistance = 6.0 * DISTANCE;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        int placed = 0;
        int ring = 0;

        for (int i = 0; i < COUNT; i++) {
            double dist = (4.0 * ringDistance + ringDistance * ring * 6.0)
                    + (rand.nextDouble() - 0.5) * ringDistance * 2.5;
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
