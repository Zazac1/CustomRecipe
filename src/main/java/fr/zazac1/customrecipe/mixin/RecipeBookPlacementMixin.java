package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.CustomRecipeBookPlacer;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** Uses component-exact inventory stacks when the recipe book places a custom recipe. */
@Mixin(AbstractCraftingMenu.class)
public abstract class RecipeBookPlacementMixin {
    @Redirect(
            method = "handlePlacement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/recipebook/ServerPlaceRecipe;placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;")
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private RecipeBookMenu.PostPlaceAction customrecipe$placeExactCustomRecipe(
            ServerPlaceRecipe.CraftingMenuAccess menu, int gridWidth, int gridHeight,
            List<Slot> inputSlots, List<Slot> slotsToClear, Inventory inventory,
            RecipeHolder holder, boolean useMaxItems, boolean bypassClearCheck) {
        Recipe<?> value = holder.value();
        if (value instanceof CustomItemCraftingRecipe customRecipe) {
            RecipeBookMenu.PostPlaceAction placed = CustomRecipeBookPlacer.place(
                    gridWidth, gridHeight, inputSlots, inventory, customRecipe, useMaxItems);
            if (placed != null) return placed;
        }
        return ServerPlaceRecipe.placeRecipe(menu, gridWidth, gridHeight, inputSlots, slotsToClear,
                inventory, holder, useMaxItems, bypassClearCheck);
    }
}
