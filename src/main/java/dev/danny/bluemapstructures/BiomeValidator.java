package dev.danny.bluemapstructures;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Validates structure positions against biome requirements using the world's BiomeSource.
 * Uses the noise-based biome source directly, so it works for ungenerated chunks
 * and is safe to call from any thread.
 */
public class BiomeValidator {

    private final BiomeSource biomeSource;
    private final MultiNoiseUtil.MultiNoiseSampler noiseSampler;
    private final Map<StructureType, List<TagKey<Biome>>> tagCache;

    public BiomeValidator(ServerWorld world) {
        var chunkManager = world.getChunkManager();
        this.biomeSource = chunkManager.getChunkGenerator().getBiomeSource();
        this.noiseSampler = chunkManager.getNoiseConfig().getMultiNoiseSampler();

        // Pre-build TagKey objects for each structure type
        this.tagCache = new EnumMap<>(StructureType.class);
        for (StructureType type : StructureType.values()) {
            List<TagKey<Biome>> tags = type.biomeTagIds().stream()
                    .map(id -> TagKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", id)))
                    .toList();
            tagCache.put(type, tags);
        }
    }

    public boolean isValidPosition(StructureType type, int blockX, int blockZ) {
        List<TagKey<Biome>> tags = tagCache.get(type);
        if (tags.isEmpty()) {
            return true; // No biome restriction
        }

        // Biome coordinates are block coordinates >> 2 (4x4 block grid)
        RegistryEntry<Biome> biome = biomeSource.getBiome(
                blockX >> 2, 64 >> 2, blockZ >> 2, noiseSampler);

        for (TagKey<Biome> tag : tags) {
            if (biome.isIn(tag)) {
                return true;
            }
        }

        return false;
    }
}
