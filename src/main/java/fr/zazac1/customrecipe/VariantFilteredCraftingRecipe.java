package fr.zazac1.customrecipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.IRecipeType;

import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import java.util.Set;

/** Compatibility wrapper retained for legacy material-variant rules. */
public final class VariantFilteredCraftingRecipe implements ICraftingRecipe {
    private final ICraftingRecipe delegate; private final Set<String> blocked;
    public VariantFilteredCraftingRecipe(ICraftingRecipe delegate, Set<String> blocked) { this.delegate = delegate; this.blocked = fr.zazac1.customrecipe.Java8.setCopy(blocked); }
    public ICraftingRecipe delegate() { return delegate; }
    public boolean blocksAny(java.util.Collection<String> materialIds) { return materialIds.stream().anyMatch(blocked::contains); }
    @Override public boolean matches(CraftingInventory input, World world) { if (!delegate.matches(input, world)) return false; for (int i=0;i<input.getContainerSize();i++) { ItemStack s=input.getItem(i); if(!s.isEmpty() && blocked.contains(Registry.ITEM.getKey(s.getItem()).toString())) return false; } return true; }
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
