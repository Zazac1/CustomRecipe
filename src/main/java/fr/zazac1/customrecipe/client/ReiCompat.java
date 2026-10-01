package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Rebuilds REI after the server replaces the client's recipe catalogue. */
final class ReiCompat {
    private ReiCompat() { }

    static void refreshAfterRecipeCatalogueSync() {
        if (!ModList.get().isLoaded("roughlyenoughitems")) return;
        Minecraft.getInstance().execute(() -> {
            try {
                Class<?> stage = Class.forName("me.shedaniel.rei.api.common.registry.ReloadStage");
                Class<?> interruption = Class.forName("me.shedaniel.rei.impl.common.plugins.ReloadInterruptionContext");
                Object never = interruption.getMethod("ofNever").invoke(null);
                Class<?> reloadManager = Class.forName("me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl");
                Method reload = reloadManager.getMethod("reloadPlugins", stage, interruption);
                // A null stage means a complete REI plugin reload, including
                // the vanilla crafting category that reads RecipeManager.
                reload.invoke(null, null, never);
            } catch (ReflectiveOperationException exception) {
                CustomRecipeMod.LOGGER.debug("[CustomRecipe] Could not refresh REI displays: {}", exception.getMessage());
            }
        });
    }
}
