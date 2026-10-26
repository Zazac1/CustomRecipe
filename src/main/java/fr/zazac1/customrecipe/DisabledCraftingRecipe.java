package fr.zazac1.customrecipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;

import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

/** Compatibility wrapper retained for legacy configurations. */
public final class DisabledCraftingRecipe implements ICraftingRecipe {
    private final ICraftingRecipe delegate;
    public DisabledCraftingRecipe(ICraftingRecipe delegate) { this.delegate = delegate; }
    public ICraftingRecipe delegate() { return delegate; }
    @Override public boolean matches(CraftingInventory input, World world) { return false; }
    @Override public ItemStack assemble(CraftingInventory input) { return delegate.assemble(input); }
    @Override public boolean canCraftInDimensions(int width, int height) { return delegate.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem() { return delegate.getResultItem(); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInventory input) { return delegate.getRemainingItems(input); }
    @Override public ResourceLocation getId() { return delegate.getId(); }
    @Override public IRecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public IRecipeType<?> getType() { return delegate.getType(); }
    @Override public NonNullList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public String getGroup() { return delegate.getGroup(); }
}
