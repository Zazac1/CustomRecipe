package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adds exact component and per-slot quantity matching to a vanilla-compatible recipe. */
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

    /** Vanilla-compatible representation used only for recipe synchronization. */
    public CraftingRecipe delegate() {
        return delegate;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World level) {
        int[] required = requiredCounts(input);
        return required.length > 0 && required.length == input.getSize();
    }

    @Override
    public int[] requiredCounts(CraftingRecipeInput input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, true) : requiredShapelessCounts(input, true);
    }

    @Override
    public int[] placementCounts(CraftingRecipeInput input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, false) : requiredShapelessCounts(input, false);
    }

    private int[] requiredShapelessCounts(CraftingRecipeInput input, boolean enforceAmounts) {
        List<Expected> expected = new ArrayList<>();
        if (definition.ingredients != null) {
            for (int i = 0; i < definition.ingredients.size(); i++) {
                String token = definition.ingredients.get(i);
                if (token != null && !token.isBlank()) {
                    expected.add(new Expected(token, definition.ingredientCount(i)));
                }
            }
        }
        List<Actual> actual = new ArrayList<>();
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty()) actual.add(new Actual(i, stack));
        }
        if (expected.size() != actual.size()) return new int[0];
        int[] required = new int[input.getSize()];
        return assign(expected, actual, 0, new boolean[actual.size()], required, enforceAmounts)
                ? required : new int[0];
    }

    private boolean assign(List<Expected> expected, List<Actual> actual, int index, boolean[] used,
                           int[] required, boolean enforceAmounts) {
        if (index == expected.size()) return true;
        Expected ingredient = expected.get(index);
        for (int i = 0; i < actual.size(); i++) {
            Actual candidate = actual.get(i);
            if (used[i] || !matchesToken(ingredient.token(), candidate.stack(), ingredient.count(), enforceAmounts)) continue;
            used[i] = true;
            required[candidate.inputIndex()] = ingredient.count();
            if (assign(expected, actual, index + 1, used, required, enforceAmounts)) return true;
            required[candidate.inputIndex()] = 0;
            used[i] = false;
        }
        return false;
    }

    private int[] requiredShapedCounts(CraftingRecipeInput input, boolean enforceAmounts) {
        List<String> rows = shrinkPattern(definition.pattern);
        if (rows.isEmpty()) return new int[0];
        int patternWidth = rows.getFirst().length();
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

    private int[] requiredRowCounts(CraftingRecipeInput input, List<String> rows, int offsetX, int offsetY,
                                    boolean mirrored,
                                    boolean enforceAmounts) {
        int patternWidth = rows.getFirst().length();
        int[] required = new int[input.getSize()];
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                boolean inside = x >= offsetX && x < offsetX + patternWidth
                        && y >= offsetY && y < offsetY + rows.size();
                char symbol = ' ';
                if (inside) {
                    int patternX = x - offsetX;
                    symbol = rows.get(y - offsetY).charAt(mirrored ? patternWidth - patternX - 1 : patternX);
                }
                String token = symbol == ' ' || definition.keys == null ? "" : definition.keys.get(symbol + "");
                ItemStack actual = input.getStackInSlot(x, y);
                if (token == null || token.isBlank()) {
                    if (!actual.isEmpty()) return new int[0];
                } else {
                    int count = definition.keyCount(symbol + "");
                    if (!matchesToken(token, actual, count, enforceAmounts)) return new int[0];
                    required[y * input.getWidth() + x] = count;
                }
            }
        }
        return required;
    }

    private boolean matchesToken(String token, ItemStack actual, int count, boolean enforceAmount) {
        if (actual == null || (enforceAmount && actual.getCount() < count)) return false;
        ItemStack custom = customStacks.get(token);
        if (custom != null) return CustomItemLibrary.exactMatch(actual, custom);
        Identifier id = Identifier.tryParse(token);
        return id != null && !actual.isEmpty() && Registries.ITEM.getId(actual.getItem()).equals(id);
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
            String row = pattern.get(y) == null ? "" : pattern.get(y);
            StringBuilder normalized = new StringBuilder(row);
            while (normalized.length() <= right) normalized.append(' ');
            result.add(normalized.substring(left, right + 1));
        }
        return result;
    }

    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        return delegate.craft(input, registries);
    }
    @Override public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
        return delegate.getRemainder(input);
    }
    @Override public boolean fits(int width, int height) { return delegate.fits(width, height); }
    @Override public ItemStack getResult(RegistryWrapper.WrapperLookup registries) {
        return delegate.getResult(registries);
    }
    @Override public DefaultedList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public RecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingRecipeCategory getCategory() { return delegate.getCategory(); }
    @Override public boolean isIgnoredInRecipeBook() { return delegate.isIgnoredInRecipeBook(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }

    private record Expected(String token, int count) {}
    private record Actual(int inputIndex, ItemStack stack) {}
}
