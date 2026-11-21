package fr.zazac1.customrecipe;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomRecipeMod implements ModInitializer {

    public static final String MOD_ID = "customrecipe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WorldRecipeAssignments.initialize();
        ServerConfigNetworking.initialize();
        if (Boolean.getBoolean("customrecipe.basicValidation")) {
            try {
                Class.forName("fr.zazac1.customrecipe.validation.BasicValidation")
                        .getMethod("initialize").invoke(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not initialize the development validation suite", error);
            }
        }
        installClientStartupValidationIfRequested();
        LOGGER.info("[CustomRecipe] Initialized.");
    }

    private static void installClientStartupValidationIfRequested() {
        if (!Boolean.getBoolean("customrecipe.clientStartupValidation")) return;
        try {
            Class.forName("fr.zazac1.customrecipe.validation.MenuSmokeValidation")
                    .getMethod("install").invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not initialize the client menu smoke test", error);
        }
    }
}
