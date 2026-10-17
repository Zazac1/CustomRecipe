package fr.zazac1.customrecipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adds exact component matching to an otherwise vanilla-compatible recipe. */
public final class CustomItemCraftingRecipe implements CraftingRecipe {
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
        return "shaped".equalsIgnoreCase(definition.type)
                ? matchesShaped(input) : matchesShapeless(input);
    }

    private boolean matchesShapeless(CraftingInput input) {
        List<String> expected = definition.ingredients == null ? List.of()
                : definition.ingredients.stream().filter(value -> value != null && !value.isBlank()).toList();
        List<ItemStack> actual = input.items().stream().filter(stack -> !stack.isEmpty()).toList();
        if (expected.size() != actual.size()) return false;
        return assign(expected, actual, 0, new boolean[actual.size()]);
    }

    private boolean assign(List<String> expected, List<ItemStack> actual, int index, boolean[] used) {
        if (index == expected.size()) return true;
        String token = expected.get(index);
        for (int i = 0; i < actual.size(); i++) {
            if (used[i] || !matchesToken(token, actual.get(i))) continue;
            used[i] = true;
            if (assign(expected, actual, index + 1, used)) return true;
            used[i] = false;
        }
        return false;
    }

    private boolean matchesShaped(CraftingInput input) {
        List<String> rows = shrink(definition.pattern);
        if (rows.isEmpty() || input.height() != rows.size() || input.width() != rows.getFirst().length()) return false;
        return matchesRows(input, rows, false) || matchesRows(input, rows, true);
    }

    private boolean matchesRows(CraftingInput input, List<String> rows, boolean mirrored) {
        int width = rows.getFirst().length();
        for (int y = 0; y < rows.size(); y++) {
            for (int x = 0; x < width; x++) {
                char symbol = rows.get(y).charAt(mirrored ? width - x - 1 : x);
                String token = symbol == ' ' || definition.keys == null ? "" : definition.keys.get(symbol + "");
                ItemStack actual = input.getItem(x, y);
                if (token == null || token.isBlank()) {
                    if (!actual.isEmpty()) return false;
                } else if (!matchesToken(token, actual)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesToken(String token, ItemStack actual) {
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
    @Override public RecipeSerializer<? extends CraftingRecipe> getSerializer() { return delegate.getSerializer(); }
    @Override public CraftingBookCategory category() { return delegate.category(); }
    @Override public PlacementInfo placementInfo() { return delegate.placementInfo(); }
    @Override public boolean isSpecial() { return true; }
    @Override public String group() { return delegate.group(); }
    @Override public boolean showNotification() { return delegate.showNotification(); }
    @Override public List<RecipeDisplay> display() { return delegate.display(); }
}
