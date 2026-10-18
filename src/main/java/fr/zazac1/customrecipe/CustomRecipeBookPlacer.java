package fr.zazac1.customrecipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Places component-exact custom recipe ingredients without vanilla's damaged-item exclusion. */
public final class CustomRecipeBookPlacer {
    private CustomRecipeBookPlacer() {}

    /**
     * Returns null when vanilla should handle the request (and display its ghost recipe on failure).
     */
    public static RecipeBookMenu.PostPlaceAction place(int gridWidth, int gridHeight,
                                                        List<Slot> inputSlots, Inventory inventory,
                                                        CustomItemCraftingRecipe recipe,
                                                        boolean useMaxItems) {
        List<CustomItemCraftingRecipe.BookIngredient> requirements =
                recipe.bookIngredients(gridWidth, gridHeight);
        if (requirements.isEmpty()) return null;

        NonNullList<ItemStack> inventoryItems = inventory.getNonEquipmentItems();
        List<ItemStack> simulated = new ArrayList<>(inventoryItems.size());
        for (ItemStack stack : inventoryItems) simulated.add(stack.copy());
        for (Slot slot : inputSlots) {
            if (!insert(simulated, slot.getItem().copy())) return null;
        }

        int maximum = maximumCrafts(simulated, requirements);
        if (maximum < 1) return null;

        int current = currentCrafts(inputSlots, requirements);
        int crafts = useMaxItems ? maximum : Math.min(maximum, Math.max(1, current + 1));

        // The simulation above guarantees these transfers fit without dropping items.
        for (Slot slot : inputSlots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            insert(inventoryItems, stack.copy());
            slot.set(ItemStack.EMPTY);
        }

        int inventorySize = inventoryItems.size();
        for (CustomItemCraftingRecipe.BookIngredient requirement : requirements) {
            ItemStack expected = requirement.stack();
            int missing = expected.getCount() * crafts;
            for (int slot = 0; slot < inventorySize && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (!CustomItemLibrary.exactMatch(candidate, expected)) continue;
                int moved = Math.min(missing, candidate.getCount());
                inventory.removeItem(slot, moved);
                missing -= moved;
            }
            if (missing > 0) throw new IllegalStateException("Reserved custom recipe ingredient disappeared");

            ItemStack placed = expected.copyWithCount(expected.getCount() * crafts);
            inputSlots.get(requirement.gridSlot()).set(placed);
        }

        inventory.setChanged();
        return RecipeBookMenu.PostPlaceAction.NOTHING;
    }

    private static int currentCrafts(List<Slot> inputSlots,
                                     List<CustomItemCraftingRecipe.BookIngredient> requirements) {
        CustomItemCraftingRecipe.BookIngredient[] bySlot =
                new CustomItemCraftingRecipe.BookIngredient[inputSlots.size()];
        for (CustomItemCraftingRecipe.BookIngredient requirement : requirements) {
            if (requirement.gridSlot() < 0 || requirement.gridSlot() >= bySlot.length) return 0;
            bySlot[requirement.gridSlot()] = requirement;
        }

        int crafts = Integer.MAX_VALUE;
        for (int slot = 0; slot < inputSlots.size(); slot++) {
            ItemStack actual = inputSlots.get(slot).getItem();
            CustomItemCraftingRecipe.BookIngredient requirement = bySlot[slot];
            if (requirement == null) {
                if (!actual.isEmpty()) return 0;
                continue;
            }
            if (!CustomItemLibrary.exactMatch(actual, requirement.stack())) return 0;
            crafts = Math.min(crafts, actual.getCount() / requirement.stack().getCount());
        }
        return crafts == Integer.MAX_VALUE ? 0 : crafts;
    }

    private static int maximumCrafts(List<ItemStack> available,
                                     List<CustomItemCraftingRecipe.BookIngredient> requirements) {
        int slotLimit = Integer.MAX_VALUE;
        for (CustomItemCraftingRecipe.BookIngredient requirement : requirements) {
            ItemStack stack = requirement.stack();
            slotLimit = Math.min(slotLimit, stack.getMaxStackSize() / stack.getCount());
        }
        if (slotLimit < 1 || slotLimit == Integer.MAX_VALUE) return 0;

        int maximum = 0;
        for (int crafts = 1; crafts <= slotLimit; crafts++) {
            if (!canReserve(available, requirements, crafts)) break;
            maximum = crafts;
        }
        return maximum;
    }

    private static boolean canReserve(List<ItemStack> available,
                                      List<CustomItemCraftingRecipe.BookIngredient> requirements,
                                      int crafts) {
        int[] counts = new int[available.size()];
        for (int i = 0; i < counts.length; i++) counts[i] = available.get(i).getCount();

        for (CustomItemCraftingRecipe.BookIngredient requirement : requirements) {
            int missing = requirement.stack().getCount() * crafts;
            for (int i = 0; i < available.size() && missing > 0; i++) {
                if (counts[i] <= 0 || !CustomItemLibrary.exactMatch(available.get(i), requirement.stack())) continue;
                int used = Math.min(missing, counts[i]);
                counts[i] -= used;
                missing -= used;
            }
            if (missing > 0) return false;
        }
        return true;
    }

    private static boolean insert(List<ItemStack> inventory, ItemStack incoming) {
        if (incoming.isEmpty()) return true;

        for (ItemStack existing : inventory) {
            if (incoming.isEmpty()) return true;
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, incoming)) continue;
            int moved = Math.min(incoming.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (moved <= 0) continue;
            existing.grow(moved);
            incoming.shrink(moved);
        }
        for (int slot = 0; slot < inventory.size() && !incoming.isEmpty(); slot++) {
            if (!inventory.get(slot).isEmpty()) continue;
            int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            inventory.set(slot, incoming.copyWithCount(moved));
            incoming.shrink(moved);
        }
        return incoming.isEmpty();
    }
}
