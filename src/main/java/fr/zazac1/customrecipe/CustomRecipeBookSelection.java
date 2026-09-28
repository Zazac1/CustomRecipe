package fr.zazac1.customrecipe;

import net.minecraft.world.item.crafting.Recipe;

/** Accessor implemented by the recipe-book screen-handler mixin. */
public interface CustomRecipeBookSelection {
    Recipe<?> customrecipe$getBookRecipe();
}
