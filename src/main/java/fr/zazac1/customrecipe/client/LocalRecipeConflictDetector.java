package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.RecipeConflictAnalyzer;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/** Bridges the local editor to the integrated server's authoritative recipe data. */
final class LocalRecipeConflictDetector {
    private LocalRecipeConflictDetector() {}

    static void refresh(List<CustomRecipeEntry> customRecipes, MinecraftClient client, List<String> disabledRecipeIds) {
        // Client resource packs are not the authoritative set of data packs. Only
        // the integrated server has resolved tags, alternatives, and serializers.
        if (client.getServer() != null) {
            RecipeConflictAnalyzer.refresh(customRecipes, client.getServer().getRecipeManager().values(),
                    disabledRecipeIds, client.getServer().getRegistryManager());
            return;
        }

        // Do not carry warnings from another world or data-pack set into Mod Menu.
        for (CustomRecipeEntry recipe : customRecipes) {
            recipe.conflicting_recipes = new ArrayList<>();
            recipe.same_shape_recipes = new ArrayList<>();
        }
    }
}
