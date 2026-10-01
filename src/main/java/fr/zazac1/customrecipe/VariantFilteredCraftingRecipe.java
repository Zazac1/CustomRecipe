package fr.zazac1.customrecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import java.util.List;
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
    public boolean matches(CraftingInput input, Level world) {
        if (!delegate.matches(input, world)) return false;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && blockedMaterials.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return false;
            }
        }
        return true;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return delegate.assemble(input, registries); }
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    /**
     * The vanilla recipe book cannot express "this recipe except birch planks".
     * Hiding it avoids its auto-fill selecting a blocked material and leaving a
     * broken crafting grid behind.
     */
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    /**
     * A material-specific disable is not representable by the recipe-book or
     * REI display protocols. Hide that display entirely so neither UI offers
     * an entry that can fail with the blocked material variant.
     */
    @Override public List<RecipeDisplay> display() { return List.of(); }
    @Override public String group() { return delegate.group(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
}
