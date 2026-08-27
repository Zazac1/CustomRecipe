package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.CustomRecipeBookSelection;
import fr.isaac.customrecipe.CustomRecipeMod;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.AbstractRecipeScreenHandler;
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

    @Override
    public Recipe<?> customrecipe$getBookRecipe() {
        return customrecipe$bookRecipe;
    }
}
