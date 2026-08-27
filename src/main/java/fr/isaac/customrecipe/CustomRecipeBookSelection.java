package fr.isaac.customrecipe;

import net.minecraft.recipe.Recipe;

/** Accessor implemented by the recipe-book screen-handler mixin. */
public interface CustomRecipeBookSelection {
    Recipe<?> customrecipe$getBookRecipe();
}
