package fr.isaac.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.Set;

/** Keeps the original recipe but rejects configured material variants at craft time. */
public final class VariantFilteredCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate;
    private final Set<String> blockedMaterials;

    public VariantFilteredCraftingRecipe(CraftingRecipe delegate, Set<String> blockedMaterials) {
        this.delegate = delegate;
        this.blockedMaterials = Set.copyOf(blockedMaterials);
    }

    public CraftingRecipe delegate() { return delegate; }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        if (!delegate.matches(input, world)) return false;
        return input.getStacks().stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> Registries.ITEM.getId(stack.getItem()).toString())
                .noneMatch(blockedMaterials::contains);
    }

    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) { return delegate.craft(input, registries); }
    @Override public boolean fits(int width, int height) { return delegate.fits(width, height); }
    @Override public ItemStack getResult(RegistryWrapper.WrapperLookup registries) { return delegate.getResult(registries); }
    @Override public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingRecipeCategory getCategory() { return delegate.getCategory(); }
    @Override public DefaultedList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
}
