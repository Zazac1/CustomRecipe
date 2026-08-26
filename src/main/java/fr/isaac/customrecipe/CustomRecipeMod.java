package fr.isaac.customrecipe;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomRecipeMod implements ModInitializer {

    public static final String MOD_ID = "customrecipe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerConfigNetworking.initialize();
        LOGGER.info("[CustomRecipe] Initialized.");
    }
}
