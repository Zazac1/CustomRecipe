package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.stats.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets a visible custom recipe be selected from a mixed vanilla/custom book group. */
@Mixin(ServerPlaceRecipe.class)
public abstract class InputSlotFillerMixin {
    @Redirect(
            method = "recipeClicked(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/Recipe;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/world/item/crafting/Recipe;)Z")
    )
    private boolean customrecipe$allowVisibleCustomRecipe(ServerRecipeBook recipeBook, Recipe<?> recipe) {
        return recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) || recipeBook.contains(recipe);
    }

    /**
     * The vanilla filler returns early when the current grid already matches a
     * recipe. A custom recipe can share that grid with a vanilla one, so force
     * a refill to make the newly selected custom output take effect.
     */
    @Redirect(
            method = "handleRecipeClicked(Lnet/minecraft/world/item/crafting/Recipe;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookMenu;recipeMatches(Lnet/minecraft/world/item/crafting/Recipe;)Z")
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean customrecipe$refillSelectedCustomRecipe(RecipeBookMenu handler, Recipe recipe) {
        return !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) && handler.recipeMatches(recipe);
    }
}
