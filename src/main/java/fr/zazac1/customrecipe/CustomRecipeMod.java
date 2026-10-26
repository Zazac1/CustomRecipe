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
        LOGGER.info("[CustomRecipe] Initialized.");
    }
}
