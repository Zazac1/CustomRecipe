package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI discovers vanilla crafting recipes from Minecraft's synchronized recipe
 * manager. Custom Recipe injects its entries into that manager, so custom and
 * enabled vanilla recipes appear in JEI without maintaining a second recipe
 * representation. This plugin also makes the integration explicit and safely
 * optional: its class is loaded only when JEI is installed on the client.
 */
@JeiPlugin
public final class JeiCompatPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(CustomRecipeMod.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        CustomRecipeMod.LOGGER.info("[CustomRecipe] JEI compatibility enabled.");
    }
}
