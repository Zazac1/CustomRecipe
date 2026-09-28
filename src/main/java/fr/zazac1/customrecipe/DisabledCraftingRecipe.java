package fr.zazac1.customrecipe;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.Level;

/** Compatibility wrapper retained for legacy configurations. */
public final class DisabledCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate;
    public DisabledCraftingRecipe(CraftingRecipe delegate) { this.delegate = delegate; }
    public CraftingRecipe delegate() { return delegate; }
    @Override public boolean matches(CraftingContainer input, Level world) { return false; }
    @Override public ItemStack assemble(CraftingContainer input, RegistryAccess registries) { return delegate.assemble(input, registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return delegate.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return delegate.getResultItem(registries); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) { return delegate.getRemainingItems(input); }
    @Override public ResourceLocation getId() { return delegate.getId(); }
    @Override public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public RecipeType<?> getType() { return delegate.getType(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public NonNullList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
}
