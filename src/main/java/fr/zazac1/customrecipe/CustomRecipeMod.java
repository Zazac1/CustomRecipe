package fr.zazac1.customrecipe;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common NeoForge entrypoint. Client code is loaded only on the physical client. */
@Mod(CustomRecipeMod.MOD_ID)
public final class CustomRecipeMod {
    public static final String MOD_ID = "customrecipe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CustomRecipeMod(IEventBus modBus, ModContainer container) {
        ModNetworking.initialize(modBus);
        WorldRecipeAssignments.initialize();
        ServerConfigNetworking.initialize();
        if (FMLEnvironment.getDist() == Dist.CLIENT) fr.zazac1.customrecipe.client.ClientInit.initialize(modBus, container);
        installClientStartupValidationIfRequested();
        LOGGER.info("[CustomRecipe] NeoForge 26.3 initialized.");
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
