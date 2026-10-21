package fr.zazac1.customrecipe;

import java.util.Map;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.world.World;

/** Keeps the vanilla shapeless serializer compatible with exact/count-aware matching. */
public final class CustomItemShapelessRecipe extends ShapelessRecipe implements CountedCraftingRecipe {
    private final CustomItemCraftingRecipe matcher;

    public CustomItemShapelessRecipe(ShapelessRecipe delegate, ItemStack result, CustomRecipeEntry definition,
                                     Map<String, ItemStack> customStacks) {
        super(delegate.getId(), delegate.getGroup(), delegate.getCategory(), result.copy(), delegate.getIngredients());
        matcher = new CustomItemCraftingRecipe(delegate, definition, customStacks);
    }

    @Override public boolean matches(RecipeInputInventory input, World world) {
        return matcher.matches(input, world);
    }

    @Override public int[] requiredCounts(RecipeInputInventory input) {
        return matcher.requiredCounts(input);
    }

    @Override public int[] placementCounts(RecipeInputInventory input) {
        return matcher.placementCounts(input);
    }
}
