package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative conflict detection for custom crafting recipes.
 *
 * <p>A conflict exists when a real 3x3 crafting grid can match both recipes.
 * This deliberately compares ingredient acceptance rather than serialized JSON:
 * tags and ingredient alternatives are resolved by Minecraft before this class
 * is called.</p>
 */
public final class RecipeConflictAnalyzer {
    private RecipeConflictAnalyzer() {}

    public static boolean refresh(List<CustomRecipeEntry> customs, Iterable<RecipeEntry<?>> candidates,
                                  Collection<String> disabledRecipeIds, RegistryWrapper.WrapperLookup registries) {
        if (customs == null) return false;
        Set<String> disabled = disabledRecipeIds == null ? Set.of() : new HashSet<>(disabledRecipeIds);
        boolean changed = false;

        for (CustomRecipeEntry entry : customs) {
            List<String> duplicates = new ArrayList<>();
            List<String> overlaps = new ArrayList<>();
            if (isActive(entry) && layoutOf(entry) != null) {
                for (RecipeEntry<?> candidateEntry : candidates) {
                    Identifier id = candidateEntry.id();
                    if (isGeneratedCustom(id) || disabled.contains(id.toString())) continue;
                    if (!(candidateEntry.value() instanceof CraftingRecipe wrapped)) continue;
                    if (!overlaps(entry, wrapped)) continue;
                    if (sameOutput(entry, wrapped, registries)) duplicates.add(id.toString());
                    else overlaps.add(id.toString());
                }
                for (CustomRecipeEntry candidate : customs) {
                    if (candidate == entry || !isActive(candidate) || sameRecipeId(entry, candidate)
                            || !overlaps(entry, candidate)) continue;
                    if (sameOutput(entry, candidate)) duplicates.add(candidate.serverRecipeId().toString());
                    else overlaps.add(candidate.serverRecipeId().toString());
                }
            }
            duplicates = distinctSorted(duplicates);
            overlaps = distinctSorted(overlaps);
            if (!duplicates.equals(entry.conflicting_recipes) || !overlaps.equals(entry.same_shape_recipes)) {
                entry.conflicting_recipes = duplicates;
                entry.same_shape_recipes = overlaps;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean overlaps(CustomRecipeEntry custom, CraftingRecipe wrappedCandidate) {
        if (wrappedCandidate instanceof DisabledCraftingRecipe) return false;
        if (wrappedCandidate instanceof VariantFilteredCraftingRecipe filtered
                && filtered.blocksAny(inputItems(custom))) return false;
        CraftingRecipe candidate = unwrap(wrappedCandidate);
        Layout customLayout = layoutOf(custom);
        if (customLayout == null) return false;
        if (candidate instanceof ShapedRecipe shaped) {
            Layout candidateLayout = layoutOf(shaped);
            return candidateLayout != null && overlapsCustomAndCandidate(customLayout, candidateLayout);
        }
        List<Ingredient> ingredients = candidate.getIngredients();
        return !ingredients.isEmpty() && matchesUnordered(inputItems(customLayout), ingredients);
    }

    private static boolean overlaps(CustomRecipeEntry first, CustomRecipeEntry second) {
        Layout firstLayout = layoutOf(first);
        Layout secondLayout = layoutOf(second);
        return firstLayout != null && secondLayout != null && overlapsCustomLayouts(firstLayout, secondLayout);
    }

    private static boolean overlapsCustomLayouts(Layout first, Layout second) {
        if (first.shaped && second.shaped) {
            if (first.width != second.width || first.height != second.height) return false;
            for (int index = 0; index < first.slots.size(); index++) {
                String a = first.slots.get(index);
                String b = second.slots.get(index);
                if (a == null ? b != null : !a.equals(b)) return false;
            }
            return true;
        }
        List<String> firstItems = inputItems(first);
        List<String> secondItems = inputItems(second);
        firstItems.sort(String::compareTo);
        secondItems.sort(String::compareTo);
        return firstItems.equals(secondItems);
    }

    private static boolean overlapsCustomAndCandidate(Layout custom, Layout candidate) {
        if (custom.shaped && candidate.shaped) {
            if (custom.width != candidate.width || custom.height != candidate.height) return false;
            for (int index = 0; index < custom.slots.size(); index++) {
                String item = custom.slots.get(index);
                Ingredient ingredient = candidate.ingredients.get(index);
                if (item == null ? ingredient != null : ingredient == null || !accepts(ingredient, item)) return false;
            }
            return true;
        }
        return matchesUnordered(inputItems(custom), candidate.nonEmptyIngredients());
    }

    private static boolean matchesUnordered(List<String> items, List<Ingredient> ingredients) {
        if (items.size() != ingredients.size()) return false;
        boolean[] assigned = new boolean[ingredients.size()];
        return assignItem(0, items, ingredients, assigned);
    }

    /** Small bipartite matching: it preserves duplicated ingredients and handles tags/alternatives. */
    private static boolean assignItem(int itemIndex, List<String> items, List<Ingredient> ingredients, boolean[] assigned) {
        if (itemIndex == items.size()) return true;
        String item = items.get(itemIndex);
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            if (assigned[ingredientIndex] || !accepts(ingredients.get(ingredientIndex), item)) continue;
            assigned[ingredientIndex] = true;
            if (assignItem(itemIndex + 1, items, ingredients, assigned)) return true;
            assigned[ingredientIndex] = false;
        }
        return false;
    }

    private static boolean accepts(Ingredient ingredient, String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id != null && Registries.ITEM.containsId(id) && ingredient.test(new ItemStack(Registries.ITEM.get(id)));
    }

    private static boolean sameOutput(CustomRecipeEntry custom, CraftingRecipe candidate,
                                      RegistryWrapper.WrapperLookup registries) {
        Identifier resultId = Identifier.tryParse(custom.result);
        if (resultId == null) return false;
        try {
            ItemStack result = unwrap(candidate).craft(CraftingRecipeInput.EMPTY, registries);
            return !result.isEmpty() && resultId.equals(Registries.ITEM.getId(result.getItem()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean sameOutput(CustomRecipeEntry first, CustomRecipeEntry second) {
        return first.result != null && first.result.equals(second.result);
    }

    private static boolean isActive(CustomRecipeEntry entry) {
        return entry != null && !Boolean.TRUE.equals(entry.corrupted)
                && !Boolean.FALSE.equals(entry.enabled) && !Boolean.FALSE.equals(entry.server_enabled)
                && WorldRecipeAssignments.isRecipeActive(entry);
    }

    private static boolean sameRecipeId(CustomRecipeEntry first, CustomRecipeEntry second) {
        return first.id != null && !first.id.isBlank() && first.id.equals(second.id);
    }

    private static boolean isGeneratedCustom(Identifier id) {
        return id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/");
    }

    private static CraftingRecipe unwrap(CraftingRecipe recipe) {
        while (true) {
            if (recipe instanceof VariantFilteredCraftingRecipe filtered) recipe = filtered.delegate();
            else if (recipe instanceof DisabledCraftingRecipe disabled) recipe = disabled.delegate();
            else return recipe;
        }
    }

    private static Layout layoutOf(CustomRecipeEntry entry) {
        if (entry == null) return null;
        if (!"shaped".equalsIgnoreCase(entry.type)) {
            if (entry.ingredients == null || entry.ingredients.isEmpty()) return null;
            List<String> items = new ArrayList<>();
            for (String item : entry.ingredients) {
                if (item == null || item.isBlank()) return null;
                items.add(item.trim());
            }
            return new Layout(false, items.size(), 1, items, List.of());
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0);
        List<String> slots = new ArrayList<>();
        for (String row : entry.pattern) {
            for (int column = 0; column < width; column++) {
                char symbol = column < row.length() ? row.charAt(column) : ' ';
                String item = symbol == ' ' ? null : entry.keys.get(String.valueOf(symbol));
                if (symbol != ' ' && (item == null || item.isBlank())) return null;
                slots.add(item == null ? null : item.trim());
            }
        }
        return trim(new Layout(true, width, entry.pattern.size(), slots, List.of()));
    }

    private static Layout layoutOf(ShapedRecipe recipe) {
        List<String> items = new ArrayList<>();
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient value : recipe.getIngredients()) {
            Ingredient ingredient = value == null || value.isEmpty() ? null : value;
            items.add(null);
            ingredients.add(ingredient);
        }
        return trim(new Layout(true, recipe.getWidth(), recipe.getHeight(), items, ingredients));
    }

    private static Layout trim(Layout source) {
        int left = source.width, right = -1, top = source.height, bottom = -1;
        for (int row = 0; row < source.height; row++) {
            for (int column = 0; column < source.width; column++) {
                if (!source.isEmpty(row * source.width + column)) {
                    left = Math.min(left, column);
                    right = Math.max(right, column);
                    top = Math.min(top, row);
                    bottom = Math.max(bottom, row);
                }
            }
        }
        if (right < left || bottom < top) return null;
        List<String> slots = new ArrayList<>();
        List<Ingredient> ingredients = new ArrayList<>();
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                int index = row * source.width + column;
                slots.add(source.slots.get(index));
                ingredients.add(source.ingredients.isEmpty() ? null : source.ingredients.get(index));
            }
        }
        return new Layout(true, right - left + 1, bottom - top + 1, slots, ingredients);
    }

    private static List<String> inputItems(CustomRecipeEntry entry) {
        Layout layout = layoutOf(entry);
        return layout == null ? List.of() : inputItems(layout);
    }

    private static List<String> inputItems(Layout layout) {
        List<String> items = new ArrayList<>();
        for (String item : layout.slots) if (item != null) items.add(item);
        return items;
    }

    private static List<String> distinctSorted(List<String> ids) {
        return ids.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private record Layout(boolean shaped, int width, int height, List<String> slots, List<Ingredient> ingredients) {
        boolean isEmpty(int index) {
            return ingredients.isEmpty() ? slots.get(index) == null : ingredients.get(index) == null;
        }

        List<Ingredient> nonEmptyIngredients() {
            return ingredients.stream().filter(java.util.Objects::nonNull).toList();
        }
    }
}
