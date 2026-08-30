package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Server-side integrity and exact-input conflict validation for saved recipes. */
public final class RecipeConflictChecker {
    private RecipeConflictChecker() {}

    public static void refreshAndSave(MinecraftServer server) {
        ModConfig config = ConfigLoader.get();
        validate(server, config);
        ConfigLoader.saveIntegrityState(config);
    }

    public static void validate(MinecraftServer server, ModConfig config) {
        for (CustomRecipeEntry entry : config.custom_recipes) {
            RecipeIntegrity.refresh(entry);
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            RecipeSignature signature = Boolean.TRUE.equals(entry.corrupted) ? null : signatureOf(entry);
            if (signature != null) for (Recipe<?> candidate : server.getRecipeManager().values()) {
                Identifier id = candidate.getId();
                if (!(candidate instanceof CraftingRecipe wrapped)
                        || (id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/"))) continue;
                CraftingRecipe existing = unwrap(wrapped);
                if (!signature.equals(signatureOf(existing))) continue;
                if (sameOutputItem(entry, existing, server)) conflicts.add(id.toString());
                else sameShape.add(id.toString());
            }
            conflicts.sort(String::compareTo);
            sameShape.sort(String::compareTo);
            entry.conflicting_recipes = conflicts;
            entry.same_shape_recipes = sameShape;
        }
    }

    private static RecipeSignature signatureOf(CustomRecipeEntry entry) {
        if ("shaped".equalsIgnoreCase(entry.type)) {
            if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
            int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0);
            List<String> slots = new ArrayList<>();
            for (String row : entry.pattern) for (int column = 0; column < width; column++) {
                char symbol = column < row.length() ? row.charAt(column) : ' ';
                String item = symbol == ' ' ? "" : entry.keys.get(String.valueOf(symbol));
                if (symbol != ' ' && (item == null || item.isBlank())) return null;
                slots.add(item == null ? "" : item.trim());
            }
            return trim(true, width, entry.pattern.size(), slots);
        }
        if (entry.ingredients == null || entry.ingredients.isEmpty()) return null;
        List<String> items = new ArrayList<>();
        for (String item : entry.ingredients) {
            if (item == null || item.isBlank()) return null;
            items.add(item.trim());
        }
        items.sort(String::compareTo);
        return new RecipeSignature(false, items.size(), 1, items);
    }

    private static RecipeSignature signatureOf(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return trim(true, shaped.getWidth(), shaped.getHeight(), shaped.getIngredients().stream()
                    .map(RecipeConflictChecker::ingredientSignature).toList());
        }
        return new RecipeSignature(false, recipe.getIngredients().size(), 1, recipe.getIngredients().stream()
                .map(RecipeConflictChecker::ingredientSignature).sorted().toList());
    }

    private static RecipeSignature trim(boolean shaped, int sourceWidth, int sourceHeight, List<String> slots) {
        int left = sourceWidth, right = -1, top = sourceHeight, bottom = -1;
        for (int row = 0; row < sourceHeight; row++) for (int column = 0; column < sourceWidth; column++) {
            if (slots.get(row * sourceWidth + column).isBlank()) continue;
            left = Math.min(left, column); right = Math.max(right, column);
            top = Math.min(top, row); bottom = Math.max(bottom, row);
        }
        if (right < left || bottom < top) return null;
        List<String> trimmed = new ArrayList<>();
        for (int row = top; row <= bottom; row++) for (int column = left; column <= right; column++) {
            trimmed.add(slots.get(row * sourceWidth + column));
        }
        return new RecipeSignature(shaped, right - left + 1, bottom - top + 1, trimmed);
    }

    private static boolean sameOutputItem(CustomRecipeEntry entry, CraftingRecipe recipe, MinecraftServer server) {
        Identifier expected = Identifier.tryParse(entry.result);
        if (expected == null) return false;
        try {
            ItemStack result = recipe.getOutput(server.getRegistryManager());
            return !result.isEmpty() && expected.equals(Registries.ITEM.getId(result.getItem()));
        } catch (RuntimeException ignored) { return false; }
    }

    private static CraftingRecipe unwrap(CraftingRecipe recipe) {
        while (true) {
            if (recipe instanceof VariantFilteredCraftingRecipe filtered) recipe = filtered.delegate();
            else if (recipe instanceof DisabledCraftingRecipe disabled) recipe = disabled.delegate();
            else return recipe;
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return Arrays.stream(ingredient.getMatchingStacks())
                .map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).sorted()
                .collect(Collectors.joining(","));
    }

    private record RecipeSignature(boolean shaped, int width, int height, List<String> ingredients) {}
}
