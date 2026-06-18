package dev.danny.bluemapstructures;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Validates structure positions against biome requirements using the world's BiomeSource. Uses the
 * noise-based biome source directly, so it works for ungenerated chunks and is safe to call from
 * any thread.
 */
public class BiomeValidator {

  private final BiomeSource biomeSource;
  private final Climate.Sampler noiseSampler;
  private final Map<StructureType, List<TagKey<Biome>>> tagCache;

  public BiomeValidator(ServerLevel world) {
    var chunkSource = world.getChunkSource();
    this.biomeSource = chunkSource.getGenerator().getBiomeSource();
    this.noiseSampler = chunkSource.randomState().sampler();

    // Pre-build TagKey objects for each structure type
    this.tagCache = new EnumMap<>(StructureType.class);
    for (StructureType type : StructureType.values()) {
      List<TagKey<Biome>> tags =
          type.biomeTagIds().stream()
              .map(
                  id ->
                      TagKey.create(
                          Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", id)))
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
    Holder<Biome> biome =
        biomeSource.getNoiseBiome(blockX >> 2, 64 >> 2, blockZ >> 2, noiseSampler);

    for (TagKey<Biome> tag : tags) {
      if (biome.is(tag)) {
        return true;
      }
    }

    return false;
  }
}
