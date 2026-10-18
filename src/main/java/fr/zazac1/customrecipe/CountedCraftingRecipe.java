package fr.zazac1.customrecipe;

import net.minecraft.world.item.crafting.CraftingInput;

/** Exposes the per-grid-slot amounts that must be consumed for one craft. */
public interface CountedCraftingRecipe {
    /**
     * Returns one required amount per slot in the positioned crafting input.
     * Empty slots contain zero. An empty array means the input no longer matches.
     */
    int[] requiredCounts(CraftingInput input);

    /** Same layout lookup used while the recipe book has only placed one item per slot. */
    default int[] placementCounts(CraftingInput input) {
        return requiredCounts(input);
    }
}
