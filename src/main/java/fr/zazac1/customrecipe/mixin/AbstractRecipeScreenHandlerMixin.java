package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.RecipeBookContainer;
import net.minecraft.item.crafting.IRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookContainer.class)
abstract class RecipeBookMenuMixin implements CustomRecipeBookSelection {
    @Unique private IRecipe<?> customrecipe$bookRecipe;

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(boolean craftAll, IRecipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    @Inject(method = "handlePlacement", at = @At("RETURN"))
    private void customrecipe$refreshCustomBookResult(boolean craftAll, IRecipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        if (!recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) return;
        RecipeBookContainer<?> menu = (RecipeBookContainer<?>) (Object) this;
        menu.getSlot(menu.getResultSlotIndex() + 1).setChanged();
    }

    @Override public IRecipe<?> customrecipe$getBookRecipe() { return customrecipe$bookRecipe; }
}
