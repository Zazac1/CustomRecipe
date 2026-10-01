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
        LOGGER.info("[CustomRecipe] NeoForge 1.21.11 initialized.");
    }
}
