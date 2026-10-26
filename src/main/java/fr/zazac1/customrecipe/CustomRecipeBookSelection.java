package fr.zazac1.customrecipe;

import net.minecraft.item.crafting.IRecipe;

/** Accessor implemented by the recipe-book screen-handler mixin. */
public interface CustomRecipeBookSelection {
    IRecipe<?> customrecipe$getBookRecipe();
}
