package fr.zazac1.customrecipe;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

/** Refreshes REI after Custom Recipe injects runtime recipes into the server manager. */
final class ReiCompat {
    private ReiCompat() {}

    static void refreshAfterRecipeReload(MinecraftServer server) {
        if (!FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) return;
        // REI's normal data-pack listener runs before ServerRecipeManager receives
        // our injected recipes. Run its own reload once the server reload is done.
        server.execute(() -> {
            try {
                Class<?> stage = Class.forName("me.shedaniel.rei.api.common.registry.ReloadStage");
                Class<?> interruption = Class.forName("me.shedaniel.rei.impl.common.plugins.ReloadInterruptionContext");
                Object never = interruption.getMethod("ofNever").invoke(null);
                Class<?> reloadManager = Class.forName("me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl");
                Method reload = reloadManager.getMethod("reloadPlugins", stage, interruption);
                reload.invoke(null, null, never);
            } catch (ReflectiveOperationException e) {
                CustomRecipeMod.LOGGER.debug("[CustomRecipe] Could not refresh REI displays: {}", e.getMessage());
            }
        });
    }
}
