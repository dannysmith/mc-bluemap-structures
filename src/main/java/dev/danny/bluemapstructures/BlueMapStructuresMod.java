package dev.danny.bluemapstructures;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class BlueMapStructuresMod implements ModInitializer {
    public static final String MOD_ID = "bluemap-structures";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("BlueMap Structure Markers loading...");

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            long worldSeed = server.getOverworld().getSeed();
            LOGGER.info("World seed: {}", worldSeed);

            Path configDir = FabricLoader.getInstance().getConfigDir();
            ModConfig config = ModConfig.load(configDir);

            BlueMapIntegration.register(server, worldSeed, config);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BlueMapIntegration.unregister();
        });
    }
}
