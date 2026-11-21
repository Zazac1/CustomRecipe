package fr.zazac1.customrecipe;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@Mod(CustomRecipeMod.MOD_ID)
public class CustomRecipeMod {

    public static final String MOD_ID = "customrecipe";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CustomRecipeMod() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(CountedIngredient::setup);
        ModNetworking.initialize();
        WorldRecipeAssignments.initialize();
        ServerConfigNetworking.initialize();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.zazac1.customrecipe.client.ClientInit.initialize());
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
