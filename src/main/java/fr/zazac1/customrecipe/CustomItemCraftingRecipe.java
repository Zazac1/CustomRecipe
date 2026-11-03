package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.NonNullList;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;

import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.world.World;

/** Adds exact NBT and per-slot quantity matching to a vanilla-compatible recipe. */
public final class CustomItemCraftingRecipe implements ICraftingRecipe, CountedCraftingRecipe {
    private final ICraftingRecipe delegate;
    private final CustomRecipeEntry definition;
    private final Map<String, ItemStack> customStacks;

    public CustomItemCraftingRecipe(ICraftingRecipe delegate, CustomRecipeEntry definition,
                                    Map<String, ItemStack> customStacks) {
        this.delegate = delegate;
        this.definition = definition;
        this.customStacks = fr.zazac1.customrecipe.Java8.mapCopy(customStacks);
    }

    @Override public boolean matches(CraftingInventory input, World world) {
        int[] required = requiredCounts(input);
        return required.length > 0 && required.length == input.getContainerSize();
    }

    @Override public int[] requiredCounts(CraftingInventory input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, true) : requiredShapelessCounts(input, true);
    }

    @Override public int[] placementCounts(CraftingInventory input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, false) : requiredShapelessCounts(input, false);
    }

    private int[] requiredShapelessCounts(CraftingInventory input, boolean enforceAmounts) {
        List<Expected> expected = new ArrayList<>();
        if (definition.ingredients != null) for (int i = 0; i < definition.ingredients.size(); i++) {
            String token = definition.ingredients.get(i);
            if (token != null && !token.trim().isEmpty()) {
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
                new HashMap<String, Set<String>>()) ? required : new int[0];
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
            Set<String> usedByGroup = ingredient.distinctGroup() == null ? null : distinctItems.get(ingredient.distinctGroup());
            if (ingredient.distinctGroup() != null && usedByGroup == null) {
                usedByGroup = new HashSet<>();
                distinctItems.put(ingredient.distinctGroup(), usedByGroup);
            }
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

    private int[] requiredShapedCounts(CraftingInventory input, boolean enforceAmounts) {
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

    private int[] requiredRowCounts(CraftingInventory input, List<String> rows, int offsetX, int offsetY,
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
                    ? java.util.Collections.emptyList() : definition.keyOptions(symbol + "");
            int inputIndex = y * input.getWidth() + x;
            ItemStack actual = input.getItem(inputIndex);
            if (tokens.isEmpty()) {
                if (!actual.isEmpty()) return new int[0];
            } else {
                int count = definition.keyCount(symbol + "");
                String matchedToken = matchingToken(tokens, actual, count, enforceAmounts);
                if (matchedToken == null) return new int[0];
                if (definition.keyRequiresDistinct(symbol + "")) {
                    String group = distinctGroup(tokens);
                    Set<String> usedByGroup = distinctItems.get(group);
                    if (usedByGroup == null) {
                        usedByGroup = new HashSet<>();
                        distinctItems.put(group, usedByGroup);
                    }
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
        return id != null && Registry.ITEM.getKey(actual.getItem()).equals(id);
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
        java.util.Collections.sort(normalized);
        return String.join("\u001F", normalized);
    }

    public static List<String> shrinkPattern(List<String> pattern) {
        if (pattern == null || pattern.isEmpty()) return fr.zazac1.customrecipe.Java8.list();
        int top = 0, bottom = pattern.size() - 1;
        while (top <= bottom && pattern.get(top).trim().isEmpty()) top++;
        while (bottom >= top && pattern.get(bottom).trim().isEmpty()) bottom--;
        if (top > bottom) return fr.zazac1.customrecipe.Java8.list();
        int left = Integer.MAX_VALUE, right = -1;
        for (int y = top; y <= bottom; y++) {
            String row = pattern.get(y) == null ? "" : pattern.get(y);
            for (int x = 0; x < row.length(); x++) if (row.charAt(x) != ' ') {
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
        }
        if (right < left) return fr.zazac1.customrecipe.Java8.list();
        List<String> result = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            StringBuilder row = new StringBuilder(pattern.get(y) == null ? "" : pattern.get(y));
            while (row.length() <= right) row.append(' ');
            result.add(row.substring(left, right + 1));
        }
        return result;
    }

    @Override public ItemStack assemble(CraftingInventory input) { return delegate.assemble(input); }
    @Override public boolean canCraftInDimensions(int width, int height) { return delegate.canCraftInDimensions(width, height); }
    @Override public ItemStack getResultItem() { return delegate.getResultItem(); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInventory input) { return delegate.getRemainingItems(input); }
    @Override public NonNullList<Ingredient> getIngredients() { return delegate.getIngredients(); }
    @Override public ResourceLocation getId() { return delegate.getId(); }
    @Override public IRecipeSerializer<?> getSerializer() { return delegate.getSerializer(); }
    @Override public boolean isSpecial() { return delegate.isSpecial(); }
    @Override public String getGroup() { return delegate.getGroup(); }

    private static final class Expected {
        private final List<String> tokens;
        private final int count;
        private final String distinctGroup;
        public Expected(List<String> tokens, int count, String distinctGroup) { this.tokens=tokens; this.count=count; this.distinctGroup=distinctGroup; }
        public List<String> tokens() { return tokens; }
        public int count() { return count; }
        public String distinctGroup() { return distinctGroup; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; Expected other=(Expected)obj; return java.util.Objects.equals(tokens, other.tokens) && java.util.Objects.equals(count, other.count) && java.util.Objects.equals(distinctGroup, other.distinctGroup); }
        @Override public int hashCode() { return java.util.Objects.hash(tokens, count, distinctGroup); }
}
    private static final class Actual {
        private final int inputIndex;
        private final ItemStack stack;
        public Actual(int inputIndex, ItemStack stack) { this.inputIndex=inputIndex; this.stack=stack; }
        public int inputIndex() { return inputIndex; }
        public ItemStack stack() { return stack; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; Actual other=(Actual)obj; return java.util.Objects.equals(inputIndex, other.inputIndex) && java.util.Objects.equals(stack, other.stack); }
        @Override public int hashCode() { return java.util.Objects.hash(inputIndex, stack); }
}
}
