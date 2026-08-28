package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** Keeps a disabled crafting recipe visible to management screens while preventing it from matching. */
public final class DisabledCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate;

    public DisabledCraftingRecipe(CraftingRecipe delegate) {
        this.delegate = delegate;
    }

    public CraftingRecipe delegate() { return delegate; }

    @Override public boolean matches(CraftingRecipeInput input, World world) { return false; }
    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) { return delegate.craft(input, registries); }
    @Override public boolean fits(int width, int height) { return delegate.fits(width, height); }
    @Override public ItemStack getResult(RegistryWrapper.WrapperLookup registries) { return delegate.getResult(registries); }
    @Override public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingRecipeCategory getCategory() { return delegate.getCategory(); }
    @Override public DefaultedList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
}
