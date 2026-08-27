package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.CustomRecipeBookSelection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/** Keeps a custom recipe chosen from the green book selected during shift-crafting. */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
    @Redirect(
            method = "updateResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/recipe/RecipeManager;getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/Optional;")
    )
    private static Optional<CraftingRecipe> customrecipe$keepBookRecipe(
            RecipeManager manager, RecipeType<CraftingRecipe> type, Inventory input, World world,
            ScreenHandler handler, World enclosingWorld, PlayerEntity player,
            RecipeInputInventory inventory, CraftingResultInventory resultInventory) {
        if (handler instanceof CustomRecipeBookSelection selection) {
            Recipe<?> saved = selection.customrecipe$getBookRecipe();
            if (saved instanceof CraftingRecipe craftingRecipe
                    && input instanceof RecipeInputInventory craftingInput
                    && craftingRecipe.matches(craftingInput, world)) {
                return Optional.of(craftingRecipe);
            }
        }
        return manager.getFirstMatch(type, (RecipeInputInventory) input, world);
    }
}
