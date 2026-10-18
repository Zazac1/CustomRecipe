package fr.zazac1.customrecipe;

import net.minecraft.world.inventory.CraftingContainer;

/** Exposes the per-grid-slot amounts that must be consumed for one craft. */
public interface CountedCraftingRecipe {
    /**
     * Returns one required amount per slot in the positioned crafting input.
     * Empty slots contain zero. An empty array means the input no longer matches.
     */
    int[] requiredCounts(CraftingContainer input);

    /** Same layout lookup used while the recipe book has only placed one item per slot. */
    default int[] placementCounts(CraftingContainer input) {
        return requiredCounts(input);
    }
}
