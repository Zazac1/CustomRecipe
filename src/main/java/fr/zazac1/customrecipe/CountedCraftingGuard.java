package fr.zazac1.customrecipe;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Server-side validation used immediately before a counted crafting result is taken. */
public final class CountedCraftingGuard {
    private CountedCraftingGuard() {}

    public static boolean canTake(CraftingContainer input, RecipeCraftingHolder resultContainer) {
        RecipeHolder<?> lastRecipe = resultContainer.getRecipeUsed();
        CraftingInput recipeInput = input.asPositionedCraftInput().input();
        return matches(recipeInput, lastRecipe);
    }

    public static boolean matches(CraftingInput recipeInput, RecipeHolder<?> recipe) {
        if (recipe == null || !(recipe.value() instanceof CountedCraftingRecipe counted)) return true;
        int[] required = counted.requiredCounts(recipeInput);
        return required.length > 0 && required.length == recipeInput.size();
    }
}
