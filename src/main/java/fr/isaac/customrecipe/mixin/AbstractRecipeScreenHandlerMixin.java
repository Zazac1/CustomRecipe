package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.CustomRecipeBookSelection;
import fr.isaac.customrecipe.CustomRecipeMod;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeScreenHandler.class)
public abstract class AbstractRecipeScreenHandlerMixin implements CustomRecipeBookSelection {
    @Unique
    private Recipe<?> customrecipe$bookRecipe;

    @Inject(method = "fillInputSlots", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(boolean craftAll, Recipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    /**
     * InputSlotFiller uses no-callback slot writes. Refresh one crafting input
     * after a custom selection so the result is recalculated immediately.
     */
    @Inject(method = "fillInputSlots", at = @At("RETURN"))
    private void customrecipe$refreshCustomBookResult(boolean craftAll, Recipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        if (recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) {
            ((ScreenHandler) (Object) this).getSlot(1).markDirty();
        }
    }

    @Override
    public Recipe<?> customrecipe$getBookRecipe() {
        return customrecipe$bookRecipe;
    }
}
