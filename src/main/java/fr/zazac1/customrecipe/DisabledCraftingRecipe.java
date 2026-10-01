package fr.zazac1.customrecipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;

import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import java.util.List;

/** Keeps a disabled crafting recipe visible to management screens while preventing it from matching. */
public final class DisabledCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate;

    public DisabledCraftingRecipe(CraftingRecipe delegate) {
        this.delegate = delegate;
    }

    public CraftingRecipe delegate() { return delegate; }

    @Override public boolean matches(CraftingInput input, Level world) { return false; }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return delegate.assemble(input, registries); }
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    /**
     * In 1.21.11 REI and the vanilla recipe book consume the server's
     * synchronized RecipeDisplay catalogue instead of the crafting lookup.
     * Keeping the delegate displays here therefore made a disabled recipe
     * remain visible in REI even though {@link #matches} correctly rejected
     * it at the crafting table.
     */
    @Override public List<RecipeDisplay> display() { return List.of(); }
    @Override public String group() { return delegate.group(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
}
