package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.item.crafting.ServerRecipePlacer;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.container.RecipeBookContainer;
import net.minecraft.item.crafting.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets a visible custom recipe be selected from a mixed vanilla/custom book group. */
@Mixin(ServerRecipePlacer.class)
public abstract class InputSlotFillerMixin {
    @Shadow protected RecipeBookContainer<?> menu;

    @Inject(method = "recipeClicked(Lnet/minecraft/entity/player/ServerPlayerEntity;Lnet/minecraft/item/crafting/IRecipe;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void customrecipe$placeCompleteRecipe(net.minecraft.entity.player.ServerPlayerEntity player,
                                                IRecipe<?> recipe, boolean craftAll, CallbackInfo ci) {
        if (recipe == null || !fr.zazac1.customrecipe.CountedRecipeBook.handles(recipe)) return;
        fr.zazac1.customrecipe.CountedRecipeBook.place(player, menu, recipe, craftAll);
        ci.cancel();
    }

    @Redirect(
            method = "recipeClicked(Lnet/minecraft/entity/player/ServerPlayerEntity;Lnet/minecraft/item/crafting/IRecipe;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/crafting/ServerRecipeBook;contains(Lnet/minecraft/item/crafting/IRecipe;)Z")
    )
    private boolean customrecipe$allowVisibleCustomRecipe(ServerRecipeBook recipeBook, IRecipe<?> recipe) {
        return recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) || recipeBook.contains(recipe);
    }

    /**
     * The vanilla filler returns early when the current grid already matches a
     * recipe. A custom recipe can share that grid with a vanilla one, so force
     * a refill to make the newly selected custom output take effect.
     */
    @Redirect(
            method = "handleRecipeClicked(Lnet/minecraft/item/crafting/IRecipe;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/container/RecipeBookContainer;recipeMatches(Lnet/minecraft/item/crafting/IRecipe;)Z")
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean customrecipe$refillSelectedCustomRecipe(RecipeBookContainer handler, IRecipe recipe) {
        return !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) && handler.recipeMatches(recipe);
    }
}
