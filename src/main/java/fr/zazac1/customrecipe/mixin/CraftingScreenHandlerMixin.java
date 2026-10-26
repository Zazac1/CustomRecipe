package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftResultInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.inventory.container.WorkbenchContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/** Keeps a custom recipe chosen from the green book selected during shift-crafting. */
@Mixin(WorkbenchContainer.class)
abstract class CraftingMenuMixin {
    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/item/crafting/IRecipeType;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/world/World;)Ljava/util/Optional;")
    )
    private static Optional<ICraftingRecipe> customrecipe$keepBookRecipe(
            RecipeManager manager, IRecipeType<ICraftingRecipe> type, IInventory input, World world,
            int containerId, World enclosingWorld, PlayerEntity player,
            CraftingInventory inventory, CraftResultInventory resultContainer) {
        // Minecraft 1.16 passes the menu ID rather than the menu itself here.
        // Only reuse a selection belonging to the player's matching open menu.
        Container handler = player.containerMenu;
        if (handler != null && handler.containerId == containerId
                && handler instanceof CustomRecipeBookSelection selection) {
            IRecipe<?> saved = selection.customrecipe$getBookRecipe();
            if (saved instanceof ICraftingRecipe craftingRecipe
                    && input instanceof CraftingInventory craftingInput
                    && craftingRecipe.matches(craftingInput, world)) {
                return Optional.of(craftingRecipe);
            }
        }
        return manager.getRecipeFor(type, (CraftingInventory) input, world);
    }
}
