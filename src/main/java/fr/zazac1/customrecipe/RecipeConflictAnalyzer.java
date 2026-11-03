package fr.zazac1.customrecipe;

import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    public static boolean refresh(List<CustomRecipeEntry> customs, Iterable<IRecipe<?>> candidates,
                                  Collection<String> disabledRecipeIds, DynamicRegistries registries) {
        if (customs == null) return false;
        Set<String> disabled = disabledRecipeIds == null ? fr.zazac1.customrecipe.Java8.set() : new HashSet<>(disabledRecipeIds);
        boolean changed = false;

        for (CustomRecipeEntry entry : customs) {
            List<String> duplicates = new ArrayList<>();
            List<String> overlaps = new ArrayList<>();
            if (isActive(entry) && layoutOf(entry) != null) {
                for (IRecipe<?> candidateEntry : candidates) {
                    ResourceLocation id = candidateEntry.getId();
                    if (isGeneratedCustom(id) || disabled.contains(id.toString())) continue;
                    if (!(candidateEntry instanceof ICraftingRecipe wrapped)) continue;
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

    private static boolean overlaps(CustomRecipeEntry custom, ICraftingRecipe wrappedCandidate) {
        if (wrappedCandidate instanceof DisabledCraftingRecipe) return false;
        Layout customLayout = layoutOf(custom);
        if (customLayout == null) return false;
        if (wrappedCandidate instanceof VariantFilteredCraftingRecipe filtered
                && customLayout.nonEmptyOptions().stream().anyMatch(filtered::blocksAll)) return false;
        ICraftingRecipe candidate = unwrap(wrappedCandidate);
        if (candidate instanceof ShapedRecipe shaped) {
            Layout candidateLayout = layoutOf(shaped);
            return candidateLayout != null && overlapsCustomAndCandidate(customLayout, candidateLayout);
        }
        List<Ingredient> ingredients = candidate.getIngredients();
        return !ingredients.isEmpty() && matchesUnordered(customLayout.nonEmptyOptions(), ingredients);
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
                List<String> a = first.slots.get(index);
                List<String> b = second.slots.get(index);
                if (a == null ? b != null : b == null || !overlap(a, b)) return false;
            }
            return true;
        }
        return matchesUnorderedOptions(first.nonEmptyOptions(), second.nonEmptyOptions());
    }

    private static boolean overlapsCustomAndCandidate(Layout custom, Layout candidate) {
        if (custom.shaped && candidate.shaped) {
            if (custom.width != candidate.width || custom.height != candidate.height) return false;
            for (int index = 0; index < custom.slots.size(); index++) {
                List<String> item = custom.slots.get(index);
                Ingredient ingredient = candidate.ingredients.get(index);
                if (item == null ? ingredient != null : ingredient == null || !acceptsAny(ingredient, item)) return false;
            }
            return true;
        }
        return matchesUnordered(custom.nonEmptyOptions(), candidate.nonEmptyIngredients());
    }

    private static boolean matchesUnordered(List<List<String>> options, List<Ingredient> ingredients) {
        if (options.size() != ingredients.size()) return false;
        boolean[] assigned = new boolean[ingredients.size()];
        return assignItem(0, options, ingredients, assigned);
    }

    /** Small bipartite matching: it preserves duplicated ingredients and handles tags/alternatives. */
    private static boolean assignItem(int itemIndex, List<List<String>> options, List<Ingredient> ingredients, boolean[] assigned) {
        if (itemIndex == options.size()) return true;
        List<String> item = options.get(itemIndex);
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            if (assigned[ingredientIndex] || !acceptsAny(ingredients.get(ingredientIndex), item)) continue;
            assigned[ingredientIndex] = true;
            if (assignItem(itemIndex + 1, options, ingredients, assigned)) return true;
            assigned[ingredientIndex] = false;
        }
        return false;
    }

    private static boolean accepts(Ingredient ingredient, String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id != null && Java8.registryContains(Registry.ITEM, id) && ingredient.test(new ItemStack(Registry.ITEM.get(id)));
    }

    private static boolean acceptsAny(Ingredient ingredient, List<String> options) {
        return options.stream().anyMatch(item -> accepts(ingredient, item));
    }

    private static boolean overlap(List<String> first, List<String> second) {
        return first.stream().anyMatch(second::contains);
    }

    private static boolean matchesUnorderedOptions(List<List<String>> first, List<List<String>> second) {
        if (first.size() != second.size()) return false;
        boolean[] assigned = new boolean[second.size()];
        return assignOptions(0, first, second, assigned);
    }

    private static boolean assignOptions(int index, List<List<String>> first, List<List<String>> second,
                                         boolean[] assigned) {
        if (index == first.size()) return true;
        for (int candidate = 0; candidate < second.size(); candidate++) {
            if (assigned[candidate] || !overlap(first.get(index), second.get(candidate))) continue;
            assigned[candidate] = true;
            if (assignOptions(index + 1, first, second, assigned)) return true;
            assigned[candidate] = false;
        }
        return false;
    }

    private static boolean sameOutput(CustomRecipeEntry custom, ICraftingRecipe candidate,
                                      DynamicRegistries registries) {
        ResourceLocation resultId = ResourceLocation.tryParse(custom.result);
        if (resultId == null) return false;
        try {
            ItemStack result = unwrap(candidate).getResultItem();
            return !result.isEmpty() && resultId.equals(Registry.ITEM.getKey(result.getItem()));
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
        return first.id != null && !first.id.trim().isEmpty() && first.id.equals(second.id);
    }

    private static boolean isGeneratedCustom(ResourceLocation id) {
        return id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/");
    }

    private static ICraftingRecipe unwrap(ICraftingRecipe recipe) {
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
            List<List<String>> items = new ArrayList<>();
            for (int index = 0; index < entry.ingredients.size(); index++) {
                String item = entry.ingredients.get(index);
                if (item == null || item.trim().isEmpty()) return null;
                items.add(entry.ingredientOptions(index));
            }
            return new Layout(false, items.size(), 1, items, fr.zazac1.customrecipe.Java8.list());
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0);
        List<List<String>> slots = new ArrayList<>();
        for (String row : entry.pattern) {
            for (int column = 0; column < width; column++) {
                char symbol = column < row.length() ? row.charAt(column) : ' ';
                String item = symbol == ' ' ? null : entry.keys.get(String.valueOf(symbol));
                if (symbol != ' ' && (item == null || item.trim().isEmpty())) return null;
                slots.add(item == null ? null : entry.keyOptions(String.valueOf(symbol)));
            }
        }
        return trim(new Layout(true, width, entry.pattern.size(), slots, fr.zazac1.customrecipe.Java8.list()));
    }

    private static Layout layoutOf(ShapedRecipe recipe) {
        List<List<String>> items = new ArrayList<>();
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
        List<List<String>> slots = new ArrayList<>();
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

    private static List<String> distinctSorted(List<String> ids) {
        return ids.stream().distinct().sorted(Comparator.naturalOrder()).collect(java.util.stream.Collectors.toList());
    }

    private static final class Layout {
        private final boolean shaped;
        private final int width;
        private final int height;
        private final List<List<String>> slots;
        private final List<Ingredient> ingredients;
        public Layout(boolean shaped, int width, int height, List<List<String>> slots, List<Ingredient> ingredients) { this.shaped=shaped; this.width=width; this.height=height; this.slots=slots; this.ingredients=ingredients; }
        public boolean shaped() { return shaped; }
        public int width() { return width; }
        public int height() { return height; }
        public List<List<String>> slots() { return slots; }
        public List<Ingredient> ingredients() { return ingredients; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; Layout other=(Layout)obj; return java.util.Objects.equals(shaped, other.shaped) && java.util.Objects.equals(width, other.width) && java.util.Objects.equals(height, other.height) && java.util.Objects.equals(slots, other.slots) && java.util.Objects.equals(ingredients, other.ingredients); }
        @Override public int hashCode() { return java.util.Objects.hash(shaped, width, height, slots, ingredients); }

        boolean isEmpty(int index) {
            return ingredients.isEmpty() ? slots.get(index) == null : ingredients.get(index) == null;
        }

        List<Ingredient> nonEmptyIngredients() {
            return ingredients.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
        }

        List<List<String>> nonEmptyOptions() {
            return slots.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
        }
    }
}
