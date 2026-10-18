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
                    expected.add(new Expected(token, definition.ingredientCount(i)));
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
        for (int y = 0; y < rows.size(); y++) {
            for (int x = 0; x < width; x++) {
                char symbol = rows.get(y).charAt(mirrored ? width - x - 1 : x);
                String token = symbol == ' ' || definition.keys == null ? "" : definition.keys.get(symbol + "");
                ItemStack actual = input.getItem(x, y);
                if (token == null || token.isBlank()) {
                    if (!actual.isEmpty()) return new int[0];
                } else {
                    int count = definition.keyCount(symbol + "");
                    if (!matchesToken(token, actual, count, enforceAmounts)) return new int[0];
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
                    String token = row.charAt(x) == ' ' || definition.keys == null ? "" : definition.keys.get(symbol);
                    ingredients.add(countedDisplay(token, definition.keyCount(symbol)));
                }
            }
            return new ShapedCraftingRecipeDisplay(shaped.width(), shaped.height(), ingredients,
                    shaped.result(), shaped.craftingStation());
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless && definition.ingredients != null) {
            List<SlotDisplay> ingredients = new ArrayList<>(definition.ingredients.size());
            for (int i = 0; i < definition.ingredients.size(); i++) {
                ingredients.add(countedDisplay(definition.ingredients.get(i), definition.ingredientCount(i)));
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

    private record Expected(String token, int count) {}
    private record Actual(int inputIndex, ItemStack stack) {}
    public record BookIngredient(int gridSlot, ItemStack stack) {}
}
