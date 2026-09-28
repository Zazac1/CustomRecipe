package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookMenu.class)
abstract class RecipeBookMenuMixin implements CustomRecipeBookSelection {
    @Unique
    private Recipe<?> customrecipe$bookRecipe;

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(boolean craftAll, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    /**
     * InputSlotFiller uses no-callback slot writes. Refresh one crafting input
     * after a custom selection so the result is recalculated immediately.
     */
    @Inject(method = "handlePlacement", at = @At("RETURN"))
    private void customrecipe$refreshCustomBookResult(boolean craftAll, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        if (recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) {
            ((AbstractContainerMenu) (Object) this).getSlot(1).setChanged();
        }
    }

    @Override
    public Recipe<?> customrecipe$getBookRecipe() {
        return customrecipe$bookRecipe;
    }
}
