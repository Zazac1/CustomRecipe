package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/** Keeps a custom recipe chosen from the green book selected during shift-crafting. */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;")
    )
    private static Optional<CraftingRecipe> customrecipe$keepBookRecipe(
            RecipeManager manager, RecipeType<CraftingRecipe> type, Container input, Level world,
            AbstractContainerMenu handler, Level enclosingWorld, Player player,
            CraftingContainer inventory, ResultContainer resultContainer) {
        if (handler instanceof CustomRecipeBookSelection selection) {
            Recipe<?> saved = selection.customrecipe$getBookRecipe();
            if (saved instanceof CraftingRecipe craftingRecipe
                    && input instanceof CraftingContainer craftingInput
                    && craftingRecipe.matches(craftingInput, world)) {
                return Optional.of(craftingRecipe);
            }
        }
        return manager.getRecipeFor(type, (CraftingContainer) input, world);
    }
}
