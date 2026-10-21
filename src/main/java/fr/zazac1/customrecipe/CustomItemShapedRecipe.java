package fr.zazac1.customrecipe;

import java.util.Map;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Keeps the vanilla shaped serializer compatible with exact/count-aware matching. */
public final class CustomItemShapedRecipe extends ShapedRecipe implements CountedCraftingRecipe {
    private final CustomItemCraftingRecipe matcher;

    public CustomItemShapedRecipe(ShapedRecipe delegate, ItemStack result, CustomRecipeEntry definition,
                                  Map<String, ItemStack> customStacks) {
        super(delegate.getId(), delegate.getGroup(), delegate.category(), delegate.getWidth(),
                delegate.getHeight(), delegate.getIngredients(), result.copy());
        matcher = new CustomItemCraftingRecipe(delegate, definition, customStacks);
    }

    @Override public boolean matches(CraftingContainer input, Level world) {
        return matcher.matches(input, world);
    }

    @Override public int[] requiredCounts(CraftingContainer input) {
        return matcher.requiredCounts(input);
    }

    @Override public int[] placementCounts(CraftingContainer input) {
        return matcher.placementCounts(input);
    }
}
