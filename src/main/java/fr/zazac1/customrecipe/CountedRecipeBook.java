package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.RecipeBookContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.network.play.server.SPlaceGhostRecipePacket;

/** Shared, non-mutating allocation for book availability and complete grid placement. */
public final class CountedRecipeBook {
    private CountedRecipeBook() {}

    public static boolean handles(IRecipe<?> recipe) {
        return recipe instanceof ICraftingRecipe
                && recipe.getIngredients().stream().anyMatch(ingredient -> ingredient instanceof CountedIngredient);
    }

    private static int amount(Ingredient ingredient) {
        return ingredient instanceof CountedIngredient ? ((CountedIngredient) ingredient).amount() : 1;
    }

    /** One stack per ingredient, including empty pattern cells; never changes the sources. */
    public static ItemStack[] allocate(IRecipe<?> recipe, List<ItemStack> sources, int crafts) {
        if (crafts < 1 || crafts > 64) return null;
        List<Ingredient> ingredients = recipe.getIngredients();
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        for (ItemStack stack : sources) {
            if (stack.isEmpty()) continue;
            int kind = 0;
            while (kind < kinds.size() && !Java8.sameItemAndTag(kinds.get(kind), stack)) kind++;
            if (kind == kinds.size()) { kinds.add(stack.copy()); totals.add(0); }
            totals.set(kind, totals.get(kind) + stack.getCount());
        }
        ItemStack[] result = new ItemStack[ingredients.size()];
        Arrays.fill(result, ItemStack.EMPTY);
        List<Integer> order = new ArrayList<>();
        int[] available = new int[totals.size()];
        for (int i = 0; i < available.length; i++) available[i] = totals.get(i);
        for (int i = 0; i < ingredients.size(); i++) if (!ingredients.get(i).isEmpty()) order.add(i);
        // Reserve the most constrained requirements before general item matches.
        order.sort(Comparator.comparingInt(index -> choices(ingredients.get(index), kinds, crafts)));
        return !order.isEmpty() && assign(ingredients, kinds, available, order, result, 0, crafts) ? result : null;
    }

    private static int choices(Ingredient ingredient, List<ItemStack> kinds, int crafts) {
        int count = 0;
        for (ItemStack kind : kinds) if (ingredient.test(kind) && amount(ingredient) * crafts <= kind.getMaxStackSize()) count++;
        return count;
    }

    private static boolean assign(List<Ingredient> ingredients, List<ItemStack> kinds, int[] available,
                                  List<Integer> order, ItemStack[] result, int next, int crafts) {
        if (next == order.size()) return true;
        int index = order.get(next);
        Ingredient ingredient = ingredients.get(index);
        int required = amount(ingredient) * crafts;
        for (int i = 0; i < kinds.size(); i++) {
            ItemStack kind = kinds.get(i);
            if (available[i] < required || required > kind.getMaxStackSize() || !ingredient.test(kind)) continue;
            available[i] -= required;
            ItemStack chosen = kind.copy();
            chosen.setCount(required);
            result[index] = chosen;
            if (assign(ingredients, kinds, available, order, result, next + 1, crafts)) return true;
            available[i] += required;
            result[index] = ItemStack.EMPTY;
        }
        return false;
    }

    public static void place(ServerPlayerEntity player, RecipeBookContainer<?> menu, IRecipe<?> recipe, boolean all) {
        int width = menu.getGridWidth(), height = menu.getGridHeight();
        if (!recipe.canCraftInDimensions(width, height) || width < 1 || height < 1) return;
        int first = menu.getResultSlotIndex() + 1;
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack stack : player.inventory.items) inventory.add(stack.copy());
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < width * height; i++) grid.add(menu.getSlot(first + i).getItem().copy());
        List<ItemStack> sources = new ArrayList<>(inventory);
        sources.addAll(grid);
        List<ItemStack> allocationSources = new ArrayList<>(grid);
        allocationSources.addAll(inventory);
        int[] layout = layout(recipe, width, height);
        int current = currentCrafts(recipe, grid, layout);
        int limit = all ? 64 : Math.min(64, current + 1);
        ItemStack[] chosen = null;
        for (int crafts = limit; crafts >= 1; crafts--) {
            chosen = allocate(recipe, allocationSources, crafts);
            if (chosen != null) break;
        }
        if (chosen == null) {
            player.connection.send(new SPlaceGhostRecipePacket(menu.containerId, recipe));
            return;
        }
        // Work entirely on copies until every ingredient and leftover has a home.
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : sources) remaining.add(stack.copy());
        for (ItemStack stack : chosen) {
            int missing = stack.getCount();
            for (ItemStack source : remaining) {
                if (missing == 0) break;
                if (!Java8.sameItemAndTag(source, stack)) continue;
                int taken = Math.min(missing, source.getCount());
                source.shrink(taken);
                missing -= taken;
            }
            if (missing != 0) return;
        }
        List<ItemStack> finalInventory = new ArrayList<>(remaining.subList(0, inventory.size()));
        for (int i = inventory.size(); i < remaining.size(); i++) {
            if (!insert(finalInventory, remaining.get(i).copy())) return;
        }
        for (int i = 0; i < layout.length; i++) {
            ItemStack stack = layout[i] < 0 ? ItemStack.EMPTY : chosen[layout[i]];
            Slot slot = menu.getSlot(first + i);
            if (!stack.isEmpty() && (!slot.mayPlace(stack) || stack.getCount() > slot.getMaxStackSize(stack))) return;
        }
        for (int i = 0; i < finalInventory.size(); i++) player.inventory.setItem(i, finalInventory.get(i));
        menu.clearCraftingContent();
        for (int i = 0; i < layout.length; i++) {
            menu.getSlot(first + i).set(layout[i] < 0 ? ItemStack.EMPTY : chosen[layout[i]].copy());
        }
        player.inventory.setChanged();
        menu.broadcastChanges();
    }

    private static int[] layout(IRecipe<?> recipe, int width, int height) {
        int[] layout = new int[width * height];
        Arrays.fill(layout, -1);
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            int offsetX = (width - shaped.getWidth()) / 2, offsetY = (height - shaped.getHeight()) / 2;
            for (int y = 0; y < shaped.getHeight(); y++) for (int x = 0; x < shaped.getWidth(); x++) {
                layout[(y + offsetY) * width + x + offsetX] = y * shaped.getWidth() + x;
            }
        } else {
            for (int i = 0; i < recipe.getIngredients().size() && i < layout.length; i++) layout[i] = i;
        }
        return layout;
    }

    private static int currentCrafts(IRecipe<?> recipe, List<ItemStack> grid, int[] layout) {
        int crafts = 64;
        for (int i = 0; i < layout.length; i++) {
            Ingredient ingredient = layout[i] < 0 ? Ingredient.EMPTY : recipe.getIngredients().get(layout[i]);
            ItemStack stack = grid.get(i);
            if (ingredient.isEmpty()) { if (!stack.isEmpty()) return 0; }
            else {
                if (!ingredient.test(stack)) return 0;
                crafts = Math.min(crafts, stack.getCount() / amount(ingredient));
            }
        }
        return crafts;
    }

    private static boolean insert(List<ItemStack> inventory, ItemStack remainder) {
        if (remainder.isEmpty()) return true;
        for (ItemStack stack : inventory) {
            if (!Java8.sameItemAndTag(stack, remainder)) continue;
            int moved = Math.min(remainder.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
            stack.grow(moved);
            remainder.shrink(moved);
            if (remainder.isEmpty()) return true;
        }
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.get(i).isEmpty()) continue;
            ItemStack moved = remainder.split(Math.min(remainder.getCount(), remainder.getMaxStackSize()));
            inventory.set(i, moved);
            if (remainder.isEmpty()) return true;
        }
        return false;
    }
}
