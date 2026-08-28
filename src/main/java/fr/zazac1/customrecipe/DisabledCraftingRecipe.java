package fr.zazac1.customrecipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** Compatibility wrapper retained for legacy configurations. */
public final class DisabledCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate;
    public DisabledCraftingRecipe(CraftingRecipe delegate) { this.delegate = delegate; }
    public CraftingRecipe delegate() { return delegate; }
    @Override public boolean matches(RecipeInputInventory input, World world) { return false; }
    @Override public ItemStack craft(RecipeInputInventory input, DynamicRegistryManager registries) { return delegate.craft(input, registries); }
    @Override public boolean fits(int width, int height) { return delegate.fits(width, height); }
    @Override public ItemStack getOutput(DynamicRegistryManager registries) { return delegate.getOutput(registries); }
    @Override public Identifier getId() { return delegate.getId(); }
    @Override public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingRecipeCategory getCategory() { return delegate.getCategory(); }
    @Override public DefaultedList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
}
