package fr.isaac.customrecipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/** Keeps a disabled crafting recipe visible to management screens while preventing it from matching. */
public final class DisabledCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe delegate;

    public DisabledCraftingRecipe(CraftingRecipe delegate) {
        this.delegate = delegate;
    }

    public CraftingRecipe delegate() { return delegate; }

    @Override public boolean matches(CraftingInput input, Level world) { return false; }
    @Override public ItemStack assemble(CraftingInput input) { return delegate.assemble(input); }
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    @Override public String group() { return delegate.group(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
    @Override public List<RecipeDisplay> display() { return delegate.display(); }
}
