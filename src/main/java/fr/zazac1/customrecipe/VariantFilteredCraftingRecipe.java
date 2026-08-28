package fr.zazac1.customrecipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import java.util.Set;

/** Compatibility wrapper retained for legacy material-variant rules. */
public final class VariantFilteredCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate; private final Set<String> blocked;
    public VariantFilteredCraftingRecipe(CraftingRecipe delegate, Set<String> blocked) { this.delegate = delegate; this.blocked = Set.copyOf(blocked); }
    public CraftingRecipe delegate() { return delegate; }
    @Override public boolean matches(RecipeInputInventory input, World world) { if (!delegate.matches(input, world)) return false; for (int i=0;i<input.size();i++) { ItemStack s=input.getStack(i); if(!s.isEmpty() && blocked.contains(Registries.ITEM.getId(s.getItem()).toString())) return false; } return true; }
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
