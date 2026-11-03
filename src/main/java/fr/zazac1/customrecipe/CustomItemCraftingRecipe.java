package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Override
    public boolean matches(CraftingRecipeInput input, World level) {
        int[] required = requiredCounts(input);
        return required.length > 0 && required.length == input.size();
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
                    List<String> tokens = definition.ingredientOptions(i);
                    expected.add(new Expected(tokens, definition.ingredientCount(i),
                            definition.ingredientRequiresDistinct(i) ? distinctGroup(tokens) : null));
                }
            }
        }
        List<Actual> actual = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty()) actual.add(new Actual(i, stack));
        }
        if (expected.size() != actual.size()) return new int[0];
        int[] required = new int[input.size()];
        return assign(expected, actual, 0, new boolean[actual.size()], required, enforceAmounts,
                new HashMap<>())
                ? required : new int[0];
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
        int[] required = new int[input.size()];
        Map<String, Set<String>> distinctItems = new HashMap<>();
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                boolean inside = x >= offsetX && x < offsetX + patternWidth
                        && y >= offsetY && y < offsetY + rows.size();
                char symbol = ' ';
                if (inside) {
                    int patternX = x - offsetX;
                    symbol = rows.get(y - offsetY).charAt(mirrored ? patternWidth - patternX - 1 : patternX);
                }
                List<String> tokens = symbol == ' ' || definition.keys == null
                        ? List.of() : definition.keyOptions(symbol + "");
                ItemStack actual = input.getStackInSlot(x, y);
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
    @Override public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
        return delegate.getRecipeRemainders(input);
    }
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingRecipeCategory getCategory() { return delegate.getCategory(); }
    @Override public IngredientPlacement getIngredientPlacement() { return delegate.getIngredientPlacement(); }
    @Override public boolean isIgnoredInRecipeBook() { return delegate.isIgnoredInRecipeBook(); }
    @Override public String getGroup() { return delegate.getGroup(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
    @Override
    public List<RecipeDisplay> getDisplays() {
        return delegate.getDisplays().stream().map(this::withIngredientCounts).toList();
    }

    private RecipeDisplay withIngredientCounts(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            List<String> rows = shrinkPattern(definition.pattern);
            if (rows.size() != shaped.height() || rows.isEmpty() || rows.getFirst().length() != shaped.width()) {
                return display;
            }
            List<SlotDisplay> ingredients = new ArrayList<>(shaped.width() * shaped.height());
            for (String row : rows) {
                for (int x = 0; x < shaped.width(); x++) {
                    String symbol = row.charAt(x) + "";
                    List<String> tokens = row.charAt(x) == ' ' || definition.keys == null
                            ? List.of() : definition.keyOptions(symbol);
                    ingredients.add(countedDisplay(tokens, definition.keyCount(symbol)));
                }
            }
            return new ShapedCraftingRecipeDisplay(shaped.width(), shaped.height(), ingredients,
                    shaped.result(), shaped.craftingStation());
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless && definition.ingredients != null) {
            List<SlotDisplay> ingredients = new ArrayList<>(definition.ingredients.size());
            for (int i = 0; i < definition.ingredients.size(); i++) {
                ingredients.add(countedDisplay(definition.ingredientOptions(i), definition.ingredientCount(i)));
            }
            return new ShapelessCraftingRecipeDisplay(ingredients, shapeless.result(), shapeless.craftingStation());
        }
        return display;
    }

    private SlotDisplay countedDisplay(String token, int count) {
        if (token == null || token.isBlank()) return SlotDisplay.EmptySlotDisplay.INSTANCE;
        ItemStack stack = customStacks.containsKey(token)
                ? customStacks.get(token).copy()
                : new ItemStack(Registries.ITEM.get(Identifier.tryParse(token)));
        stack.setCount(Math.max(1, Math.min(count, stack.getMaxCount())));
        return new SlotDisplay.StackSlotDisplay(stack);
    }

    private SlotDisplay countedDisplay(List<String> tokens, int count) {
        List<SlotDisplay> choices = tokens.stream().map(token -> countedDisplay(token, count)).toList();
        if (choices.isEmpty()) return SlotDisplay.EmptySlotDisplay.INSTANCE;
        return choices.size() == 1 ? choices.getFirst() : new SlotDisplay.CompositeSlotDisplay(choices);
    }
    @Override public RecipeBookCategory getRecipeBookCategory() { return delegate.getRecipeBookCategory(); }

    private record Expected(List<String> tokens, int count, String distinctGroup) {}
    private record Actual(int inputIndex, ItemStack stack) {}
}
