package fr.zazac1.customrecipe;

import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;

/** Server-side validation used immediately before a counted crafting result is taken. */
public final class CountedCraftingGuard {
    private CountedCraftingGuard() {}

    public static boolean canTake(RecipeInputInventory input, CraftingResultInventory resultInventory) {
        RecipeEntry<?> lastRecipe = resultInventory.getLastRecipe();
        if (lastRecipe == null || !(lastRecipe.value() instanceof CountedCraftingRecipe counted)) return true;

        CraftingRecipeInput recipeInput = input.createPositionedRecipeInput().input();
        int[] required = counted.requiredCounts(recipeInput);
        return required.length > 0 && required.length == recipeInput.size();
    }
}
