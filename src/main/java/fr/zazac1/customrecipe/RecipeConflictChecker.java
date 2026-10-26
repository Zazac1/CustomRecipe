package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.util.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

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
        WorldRecipeConfig active = ConfigLoader.activeWorldConfig(config);
        for (CustomRecipeEntry entry : active.custom_recipes) {
            RecipeIntegrity.refresh(entry, config.custom_items);
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            RecipeSignature signature = Boolean.TRUE.equals(entry.corrupted) ? null : signatureOf(entry);
            if (signature != null) for (IRecipe<?> candidate : server.getRecipeManager().getRecipes()) {
                ResourceLocation id = candidate.getId();
                if (!(candidate instanceof ICraftingRecipe wrapped)
                        || (id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/"))) continue;
                ICraftingRecipe existing = unwrap(wrapped);
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
                if (symbol != ' ' && (item == null || item.trim().isEmpty())) return null;
                slots.add(item == null ? "" : item.trim());
            }
            return trim(true, width, entry.pattern.size(), slots);
        }
        if (entry.ingredients == null || entry.ingredients.isEmpty()) return null;
        List<String> items = new ArrayList<>();
        for (String item : entry.ingredients) {
            if (item == null || item.trim().isEmpty()) return null;
            items.add(item.trim());
        }
        items.sort(String::compareTo);
        return new RecipeSignature(false, items.size(), 1, items);
    }

    private static RecipeSignature signatureOf(ICraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return trim(true, shaped.getWidth(), shaped.getHeight(), shaped.getIngredients().stream()
                    .map(RecipeConflictChecker::ingredientSignature).collect(java.util.stream.Collectors.toList()));
        }
        return new RecipeSignature(false, recipe.getIngredients().size(), 1, recipe.getIngredients().stream()
                .map(RecipeConflictChecker::ingredientSignature).sorted().collect(java.util.stream.Collectors.toList()));
    }

    private static RecipeSignature trim(boolean shaped, int sourceWidth, int sourceHeight, List<String> slots) {
        int left = sourceWidth, right = -1, top = sourceHeight, bottom = -1;
        for (int row = 0; row < sourceHeight; row++) for (int column = 0; column < sourceWidth; column++) {
            if (slots.get(row * sourceWidth + column).trim().isEmpty()) continue;
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

    private static boolean sameOutputItem(CustomRecipeEntry entry, ICraftingRecipe recipe, MinecraftServer server) {
        ResourceLocation expected = ResourceLocation.tryParse(entry.result);
        if (expected == null) return false;
        try {
            ItemStack result = recipe.getResultItem();
            return !result.isEmpty() && expected.equals(Registry.ITEM.getKey(result.getItem()));
        } catch (RuntimeException ignored) { return false; }
    }

    private static ICraftingRecipe unwrap(ICraftingRecipe recipe) {
        while (true) {
            if (recipe instanceof VariantFilteredCraftingRecipe filtered) recipe = filtered.delegate();
            else if (recipe instanceof DisabledCraftingRecipe disabled) recipe = disabled.delegate();
            else return recipe;
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .map(stack -> Registry.ITEM.getKey(stack.getItem()).toString()).sorted()
                .collect(Collectors.joining(","));
    }

    private static final class RecipeSignature {
        private final boolean shaped;
        private final int width;
        private final int height;
        private final List<String> ingredients;
        public RecipeSignature(boolean shaped, int width, int height, List<String> ingredients) { this.shaped=shaped; this.width=width; this.height=height; this.ingredients=ingredients; }
        public boolean shaped() { return shaped; }
        public int width() { return width; }
        public int height() { return height; }
        public List<String> ingredients() { return ingredients; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; RecipeSignature other=(RecipeSignature)obj; return java.util.Objects.equals(shaped, other.shaped) && java.util.Objects.equals(width, other.width) && java.util.Objects.equals(height, other.height) && java.util.Objects.equals(ingredients, other.ingredients); }
        @Override public int hashCode() { return java.util.Objects.hash(shaped, width, height, ingredients); }
}
}
