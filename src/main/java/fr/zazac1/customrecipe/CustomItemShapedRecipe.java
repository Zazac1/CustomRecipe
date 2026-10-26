package fr.zazac1.customrecipe;

import java.util.Map;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.world.World;

/** Keeps the vanilla shaped serializer compatible with exact/count-aware matching. */
public final class CustomItemShapedRecipe extends ShapedRecipe implements CountedCraftingRecipe {
    private final CustomItemCraftingRecipe matcher;

    public CustomItemShapedRecipe(ShapedRecipe delegate, ItemStack result, CustomRecipeEntry definition,
                                  Map<String, ItemStack> customStacks) {
        super(delegate.getId(), delegate.getGroup(), delegate.getWidth(),
                delegate.getHeight(), delegate.getIngredients(), result.copy());
        matcher = new CustomItemCraftingRecipe(delegate, definition, customStacks);
    }

    @Override public boolean matches(CraftingInventory input, World world) {
        return matcher.matches(input, world);
    }

    @Override public int[] requiredCounts(CraftingInventory input) {
        return matcher.requiredCounts(input);
    }

    @Override public int[] placementCounts(CraftingInventory input) {
        return matcher.placementCounts(input);
    }
}
