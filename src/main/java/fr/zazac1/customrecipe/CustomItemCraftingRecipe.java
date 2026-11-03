package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Adds exact NBT and per-slot quantity matching to a vanilla-compatible recipe. */
public final class CustomItemCraftingRecipe implements CraftingRecipe, CountedCraftingRecipe {
    private final CraftingRecipe delegate;
    private final CustomRecipeEntry definition;
    private final Map<String, ItemStack> customStacks;

    public CustomItemCraftingRecipe(CraftingRecipe delegate, CustomRecipeEntry definition,
                                    Map<String, ItemStack> customStacks) {
        this.delegate = delegate;
        this.definition = definition;
        this.customStacks = Map.copyOf(customStacks);
    }

    @Override public boolean matches(CraftingContainer input, Level world) {
        int[] required = requiredCounts(input);
        return required.length > 0 && required.length == input.getContainerSize();
    }

    @Override public int[] requiredCounts(CraftingContainer input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, true) : requiredShapelessCounts(input, true);
    }

    @Override public int[] placementCounts(CraftingContainer input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, false) : requiredShapelessCounts(input, false);
    }

    private int[] requiredShapelessCounts(CraftingContainer input, boolean enforceAmounts) {
        List<Expected> expected = new ArrayList<>();
        if (definition.ingredients != null) for (int i = 0; i < definition.ingredients.size(); i++) {
            String token = definition.ingredients.get(i);
            if (token != null && !token.isBlank()) {
                List<String> tokens = definition.ingredientOptions(i);
                expected.add(new Expected(tokens, definition.ingredientCount(i),
                        definition.ingredientRequiresDistinct(i) ? distinctGroup(tokens) : null));
            }
        }
        List<Actual> actual = new ArrayList<>();
        for (int i = 0; i < input.getContainerSize(); i++) if (!input.getItem(i).isEmpty()) actual.add(new Actual(i, input.getItem(i)));
        if (expected.size() != actual.size()) return new int[0];
        int[] required = new int[input.getContainerSize()];
        return assign(expected, actual, 0, new boolean[actual.size()], required, enforceAmounts,
                new HashMap<>()) ? required : new int[0];
    }

    private boolean assign(List<Expected> expected, List<Actual> actual, int index, boolean[] used,
                           int[] required, boolean enforceAmounts,
                           Map<String, Set<String>> distinctItems) {
        if (index == expected.size()) return true;
        Expected ingredient = expected.get(index);
        for (int i = 0; i < actual.size(); i++) {
            Actual candidate = actual.get(i);
            if (used[i]) continue;
            String matchedToken = matchingToken(ingredient.tokens(), candidate.stack(), ingredient.count(), enforceAmounts);
            if (matchedToken == null) continue;
            Set<String> usedByGroup = ingredient.distinctGroup() == null ? null
                    : distinctItems.computeIfAbsent(ingredient.distinctGroup(), ignored -> new HashSet<>());
            if (usedByGroup != null && !usedByGroup.add(matchedToken)) continue;
            used[i] = true;
            required[candidate.inputIndex()] = ingredient.count();
            if (assign(expected, actual, index + 1, used, required, enforceAmounts, distinctItems)) return true;
            required[candidate.inputIndex()] = 0;
            used[i] = false;
            if (usedByGroup != null) usedByGroup.remove(matchedToken);
        }
        return false;
    }

    private int[] requiredShapedCounts(CraftingContainer input, boolean enforceAmounts) {
        List<String> rows = shrinkPattern(definition.pattern);
        if (rows.isEmpty()) return new int[0];
        int patternWidth = rows.get(0).length();
        int patternHeight = rows.size();
        if (patternWidth > input.getWidth() || patternHeight > input.getHeight()) return new int[0];
        for (int offsetY = 0; offsetY <= input.getHeight() - patternHeight; offsetY++) {
            for (int offsetX = 0; offsetX <= input.getWidth() - patternWidth; offsetX++) {
                int[] normal = requiredRowCounts(input, rows, offsetX, offsetY, false, enforceAmounts);
                if (normal.length > 0) return normal;
                int[] mirrored = requiredRowCounts(input, rows, offsetX, offsetY, true, enforceAmounts);
                if (mirrored.length > 0) return mirrored;
            }
        }
        return new int[0];
    }

    private int[] requiredRowCounts(CraftingContainer input, List<String> rows, int offsetX, int offsetY,
                                    boolean mirrored, boolean enforceAmounts) {
        int patternWidth = rows.get(0).length();
        int[] required = new int[input.getContainerSize()];
        Map<String, Set<String>> distinctItems = new HashMap<>();
        for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
            boolean inside = x >= offsetX && x < offsetX + patternWidth
                    && y >= offsetY && y < offsetY + rows.size();
            char symbol = ' ';
            if (inside) {
                int patternX = x - offsetX;
                symbol = rows.get(y - offsetY).charAt(mirrored ? patternWidth - patternX - 1 : patternX);
            }
            List<String> tokens = symbol == ' ' || definition.keys == null
                    ? List.of() : definition.keyOptions(symbol + "");
            int inputIndex = y * input.getWidth() + x;
            ItemStack actual = input.getItem(inputIndex);
            if (tokens.isEmpty()) {
                if (!actual.isEmpty()) return new int[0];
            } else {
                int count = definition.keyCount(symbol + "");
                String matchedToken = matchingToken(tokens, actual, count, enforceAmounts);
                if (matchedToken == null) return new int[0];
                if (definition.keyRequiresDistinct(symbol + "")) {
                    Set<String> usedByGroup = distinctItems.computeIfAbsent(distinctGroup(tokens),
                            ignored -> new HashSet<>());
                    if (!usedByGroup.add(matchedToken)) return new int[0];
                }
                required[inputIndex] = count;
            }
        }
        return required;
    }

    private boolean matchesToken(String token, ItemStack actual, int count, boolean enforceAmount) {
        if (actual == null || actual.isEmpty() || (enforceAmount && actual.getCount() < count)) return false;
        ItemStack custom = customStacks.get(token);
        if (custom != null) return CustomItemLibrary.exactMatch(actual, custom);
        ResourceLocation id = ResourceLocation.tryParse(token);
        return id != null && BuiltInRegistries.ITEM.getKey(actual.getItem()).equals(id);
    }

    private boolean matchesAnyToken(List<String> tokens, ItemStack actual, int count, boolean enforceAmount) {
        return matchingToken(tokens, actual, count, enforceAmount) != null;
    }

    private String matchingToken(List<String> tokens, ItemStack actual, int count, boolean enforceAmount) {
        for (String token : tokens) {
            if (matchesToken(token, actual, count, enforceAmount)) return token;
        }
        return null;
    }

    private static String distinctGroup(List<String> tokens) {
        List<String> normalized = new ArrayList<>(new LinkedHashSet<>(tokens));
        normalized.sort(String::compareTo);
        return String.join("\u001F", normalized);
    }

    public static List<String> shrinkPattern(List<String> pattern) {
        if (pattern == null || pattern.isEmpty()) return List.of();
        int top = 0, bottom = pattern.size() - 1;
        while (top <= bottom && pattern.get(top).isBlank()) top++;
        while (bottom >= top && pattern.get(bottom).isBlank()) bottom--;
        if (top > bottom) return List.of();
        int left = Integer.MAX_VALUE, right = -1;
        for (int y = top; y <= bottom; y++) {
            String row = pattern.get(y) == null ? "" : pattern.get(y);
            for (int x = 0; x < row.length(); x++) if (row.charAt(x) != ' ') {
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
        }
        if (right < left) return List.of();
        List<String> result = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            StringBuilder row = new StringBuilder(pattern.get(y) == null ? "" : pattern.get(y));
            while (row.length() <= right) row.append(' ');
            result.add(row.substring(left, right + 1));
        }
        return result;
    }

    @Override public ItemStack assemble(CraftingContainer input, RegistryAccess registries) { return delegate.assemble(input, registries); }
    @Override public boolean canCraftInDimensions(int width, int height) { return delegate.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return delegate.getResultItem(registries); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) { return delegate.getRemainingItems(input); }
    @Override public NonNullList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public ResourceLocation getId() { return delegate.getId(); }
    @Override public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public boolean isSpecial() { return delegate.isSpecial(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }

    private record Expected(List<String> tokens, int count, String distinctGroup) {}
    private record Actual(int inputIndex, ItemStack stack) {}
}
