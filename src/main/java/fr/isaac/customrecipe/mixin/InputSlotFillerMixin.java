package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.CustomRecipeMod;
import net.minecraft.recipe.InputSlotFiller;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.server.network.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets a visible custom recipe be selected from a mixed vanilla/custom book group. */
@Mixin(InputSlotFiller.class)
public abstract class InputSlotFillerMixin {
    @Redirect(
            method = "fillInputSlots(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/recipe/Recipe;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerRecipeBook;contains(Lnet/minecraft/recipe/Recipe;)Z")
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
            method = "fillInputSlots(Lnet/minecraft/recipe/Recipe;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/AbstractRecipeScreenHandler;matches(Lnet/minecraft/recipe/Recipe;)Z")
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean customrecipe$refillSelectedCustomRecipe(AbstractRecipeScreenHandler handler, Recipe recipe) {
        return !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) && handler.matches(recipe);
    }
}
