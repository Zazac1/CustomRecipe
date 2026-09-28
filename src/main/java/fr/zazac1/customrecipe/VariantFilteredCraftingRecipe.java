package fr.zazac1.customrecipe;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.Level;
import java.util.Set;

/** Compatibility wrapper retained for legacy material-variant rules. */
public final class VariantFilteredCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate; private final Set<String> blocked;
    public VariantFilteredCraftingRecipe(CraftingRecipe delegate, Set<String> blocked) { this.delegate = delegate; this.blocked = Set.copyOf(blocked); }
    public CraftingRecipe delegate() { return delegate; }
    @Override public boolean matches(CraftingContainer input, Level world) { if (!delegate.matches(input, world)) return false; for (int i=0;i<input.getContainerSize();i++) { ItemStack s=input.getItem(i); if(!s.isEmpty() && blocked.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) return false; } return true; }
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
