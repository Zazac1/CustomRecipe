package fr.zazac1.customrecipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

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
    public boolean matches(CraftingInput input, Level level) {
        if (!delegate.matches(input, level)) return false;
        int[] required = requiredCounts(input);
        return required.length > 0 && required.length == input.size();
    }

    @Override
    public int[] requiredCounts(CraftingInput input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, true) : requiredShapelessCounts(input, true);
    }

    @Override
    public int[] placementCounts(CraftingInput input) {
        return "shaped".equalsIgnoreCase(definition.type)
                ? requiredShapedCounts(input, false) : requiredShapelessCounts(input, false);
    }

    private int[] requiredShapelessCounts(CraftingInput input, boolean enforceAmounts) {
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
            ItemStack stack = input.getItem(i);
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

    private int[] requiredShapedCounts(CraftingInput input, boolean enforceAmounts) {
        List<String> rows = shrink(definition.pattern);
        if (rows.isEmpty() || input.height() != rows.size() || input.width() != rows.getFirst().length()) {
            return new int[0];
        }
        int[] normal = requiredRowCounts(input, rows, false, enforceAmounts);
        return normal.length > 0 ? normal : requiredRowCounts(input, rows, true, enforceAmounts);
    }

    private int[] requiredRowCounts(CraftingInput input, List<String> rows, boolean mirrored,
                                    boolean enforceAmounts) {
        int width = rows.getFirst().length();
        int[] required = new int[input.size()];
        Map<String, Set<String>> distinctItems = new HashMap<>();
        for (int y = 0; y < rows.size(); y++) {
            for (int x = 0; x < width; x++) {
                char symbol = rows.get(y).charAt(mirrored ? width - x - 1 : x);
                List<String> tokens = symbol == ' ' || definition.keys == null
                        ? List.of() : definition.keyOptions(symbol + "");
                ItemStack actual = input.getItem(x, y);
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
                    required[y * width + x] = count;
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
        return id != null && !actual.isEmpty() && BuiltInRegistries.ITEM.getKey(actual.getItem()).equals(id);
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

    private static List<String> shrink(List<String> pattern) {
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

    @Override public ItemStack assemble(CraftingInput input) { return delegate.assemble(input); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) { return delegate.getRemainingItems(input); }
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    @Override public boolean isSpecial() { return true; }
    @Override public String group() { return delegate.group(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
    @Override
    public List<RecipeDisplay> display() {
        return delegate.display().stream().map(this::withIngredientCounts).toList();
    }

    private RecipeDisplay withIngredientCounts(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            List<String> rows = shrink(definition.pattern);
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
        if (token == null || token.isBlank()) return SlotDisplay.Empty.INSTANCE;
        ItemStack stack = customStacks.containsKey(token)
                ? customStacks.get(token).copy()
                : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.tryParse(token)));
        stack.setCount(Math.max(1, Math.min(count, stack.getMaxStackSize())));
        return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
    }

    private SlotDisplay countedDisplay(List<String> tokens, int count) {
        List<SlotDisplay> choices = tokens.stream().map(token -> countedDisplay(token, count)).toList();
        if (choices.isEmpty()) return SlotDisplay.Empty.INSTANCE;
        return choices.size() == 1 ? choices.getFirst() : new SlotDisplay.Composite(choices);
    }

    /** Exact stacks and grid positions used by the server-side recipe-book placer. */
    public List<BookIngredient> bookIngredients(int gridWidth, int gridHeight) {
        List<BookIngredient> result = new ArrayList<>();
        if ("shaped".equalsIgnoreCase(definition.type)) {
            List<String> rows = shrink(definition.pattern);
            if (rows.isEmpty() || rows.size() > gridHeight || rows.getFirst().length() > gridWidth) return List.of();
            int width = rows.getFirst().length();
            int left = width < gridWidth / 2.0F ? (int) Math.floor(gridWidth / 2.0F - width / 2.0F) : 0;
            int top = rows.size() < gridHeight / 2.0F
                    ? (int) Math.floor(gridHeight / 2.0F - rows.size() / 2.0F) : 0;
            for (int y = 0; y < rows.size(); y++) {
                for (int x = 0; x < width; x++) {
                    String symbol = rows.get(y).charAt(x) + "";
                    String token = rows.get(y).charAt(x) == ' ' || definition.keys == null
                            ? "" : definition.keys.get(symbol);
                    if (token == null || token.isBlank()) continue;
                    ItemStack stack = ingredientStack(token, definition.keyCount(symbol));
                    if (stack.isEmpty()) return List.of();
                    result.add(new BookIngredient((top + y) * gridWidth + left + x, stack));
                }
            }
        } else if (definition.ingredients != null) {
            int slot = 0;
            for (int i = 0; i < definition.ingredients.size(); i++) {
                String token = definition.ingredients.get(i);
                if (token == null || token.isBlank()) continue;
                if (slot >= gridWidth * gridHeight) return List.of();
                ItemStack stack = ingredientStack(token, definition.ingredientCount(i));
                if (stack.isEmpty()) return List.of();
                result.add(new BookIngredient(slot++, stack));
            }
        }
        return result;
    }

    private ItemStack ingredientStack(String token, int count) {
        ItemStack stack = customStacks.containsKey(token)
                ? customStacks.get(token).copy()
                : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.tryParse(token)));
        if (stack.isEmpty()) return ItemStack.EMPTY;
        stack.setCount(Math.max(1, Math.min(count, stack.getMaxStackSize())));
        return stack;
    }

    private record Expected(List<String> tokens, int count, String distinctGroup) {}
    private record Actual(int inputIndex, ItemStack stack) {}
    public record BookIngredient(int gridSlot, ItemStack stack) {}
}
