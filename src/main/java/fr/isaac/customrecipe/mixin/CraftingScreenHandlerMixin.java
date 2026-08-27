package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.CustomRecipeMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Keeps a custom recipe chosen from the green book selected during shift-crafting. */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
    @Unique
    private RecipeEntry<CraftingRecipe> customrecipe$bookRecipe;

    @Inject(method = "onInputSlotFillFinish", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(ServerWorld world, RecipeEntry<CraftingRecipe> recipe, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.id().getValue().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    @Redirect(
            method = "updateResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/recipe/ServerRecipeManager;getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;")
    )
    private static Optional<RecipeEntry<CraftingRecipe>> customrecipe$keepBookRecipe(
            ServerRecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, World world,
            RecipeEntry<CraftingRecipe> requested, ScreenHandler handler, ServerWorld serverWorld,
            PlayerEntity player, RecipeInputInventory inventory, CraftingResultInventory resultInventory,
            RecipeEntry<CraftingRecipe> currentRecipe) {
        RecipeEntry<CraftingRecipe> preferred = requested;
        if (preferred == null && handler instanceof CraftingScreenHandler) {
            CraftingScreenHandlerMixin mixin = (CraftingScreenHandlerMixin) (Object) handler;
            RecipeEntry<CraftingRecipe> saved = mixin.customrecipe$bookRecipe;
            if (saved != null && input instanceof CraftingRecipeInput craftingInput
                    && saved.value().matches(craftingInput, world)) {
                preferred = saved;
            } else if (saved != null) {
                mixin.customrecipe$bookRecipe = null;
            }
        }
        return manager.getFirstMatch(type, (CraftingRecipeInput) input, world, preferred);
    }
}
