package dev.danny.bluemapstructures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int radiusBlocks = 5000;
    public Map<String, Boolean> enabledStructures = new LinkedHashMap<>();

    public ModConfig() {
        for (StructureType type : StructureType.values()) {
            enabledStructures.put(type.name(), true);
        }
    }

    public boolean isEnabled(StructureType type) {
        return enabledStructures.getOrDefault(type.name(), true);
    }

    public static ModConfig load(Path configDir) {
        Path configFile = configDir.resolve("bluemap-structures.json");
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config != null) {
                    // Add any new structure types that aren't in the saved config
                    for (StructureType type : StructureType.values()) {
                        config.enabledStructures.putIfAbsent(type.name(), true);
                    }
                    return config;
                }
            } catch (IOException e) {
                BlueMapStructuresMod.LOGGER.warn("Failed to read config, using defaults", e);
            }
        }

        ModConfig config = new ModConfig();
        config.save(configDir);
        return config;
    }

    public void save(Path configDir) {
        Path configFile = configDir.resolve("bluemap-structures.json");
        try {
            Files.createDirectories(configDir);
            Files.writeString(configFile, GSON.toJson(this));
        } catch (IOException e) {
            BlueMapStructuresMod.LOGGER.warn("Failed to save config", e);
        }
    }
}
