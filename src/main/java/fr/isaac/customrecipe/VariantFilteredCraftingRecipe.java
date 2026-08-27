package fr.isaac.customrecipe;

import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

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
        return input.items().stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .noneMatch(blockedMaterials::contains);
    }

    @Override public ItemStack assemble(CraftingInput input) { return delegate.assemble(input); }
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    @Override public String group() { return delegate.group(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
    @Override public List<RecipeDisplay> display() { return delegate.display(); }
}
