package fr.zazac1.customrecipe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResource;
import net.minecraft.util.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Matches local ModMenu drafts against installed and other local crafting recipes. */
final class LocalRecipeConflictDetector {
    private static Set<String> vanillaCraftingOutputs = fr.zazac1.customrecipe.Java8.set();

    private LocalRecipeConflictDetector() {}

    static void refresh(List<CustomRecipeEntry> customRecipes, Minecraft client) {
        List<DefaultRecipe> defaults = loadDefaults(client);
        rememberVanillaCraftingOutputs(defaults);
        for (CustomRecipeEntry custom : customRecipes) {
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            for (DefaultRecipe candidate : defaults) {
                if (!sameInputs(custom, candidate)) continue;
                if (candidate.result().equals(custom.result)) conflicts.add(candidate.id());
                else sameShape.add(candidate.id());
            }
            for (CustomRecipeEntry candidate : customRecipes) {
                if (candidate == custom || sameRecipeId(custom, candidate) || !sameInputs(custom, candidate)) continue;
                if (sameOutputItem(custom, candidate)) conflicts.add(candidate.serverRecipeId().toString());
                else sameShape.add(candidate.serverRecipeId().toString());
            }
            conflicts.sort(String::compareTo);
            sameShape.sort(String::compareTo);
            custom.conflicting_recipes = conflicts;
            custom.same_shape_recipes = sameShape;
        }
    }

    static void refreshVanillaCraftingOutputs(Minecraft client) {
        rememberVanillaCraftingOutputs(loadDefaults(client));
    }

    static boolean hasVanillaCraftingOutput(String itemId) {
        return itemId != null && vanillaCraftingOutputs.contains(itemId);
    }

    private static void rememberVanillaCraftingOutputs(List<DefaultRecipe> defaults) {
        Set<String> outputs = new HashSet<>();
        for (DefaultRecipe recipe : defaults) {
            if (recipe.id().startsWith("minecraft:")) outputs.add(recipe.result());
        }
        vanillaCraftingOutputs = Collections.unmodifiableSet(outputs);
    }

    private static List<DefaultRecipe> loadDefaults(Minecraft client) {
        List<DefaultRecipe> recipes = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Map<ResourceLocation, IResource> resources = fr.zazac1.customrecipe.client.compat.LegacyResources.listResources(client.getResourceManager(), "recipes",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, IResource> resource : resources.entrySet()) {
            ResourceLocation resourceId = resource.getKey();
            if (resourceId.getNamespace().equals(CustomRecipeMod.MOD_ID)
                    && resourceId.getPath().startsWith("recipes/custom/")) continue;
            try (var input = resource.getValue().getInputStream()) {
                String recipeId = resourceId.getNamespace() + ":" + resourceId.getPath()
                        .substring("recipes/".length(), resourceId.getPath().length() - ".json".length());
                DefaultRecipe recipe = parse(recipeId, new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8));
                if (recipe != null && seenIds.add(recipe.id())) recipes.add(recipe);
            } catch (Exception ignored) {
                // Optional or malformed datapack recipes are not comparable locally.
            }
        }
        loadBundledVanillaRecipes(client, recipes, seenIds);
        return recipes;
    }

    /** Development clients may not mount Minecraft's own recipe resources. */
    private static void loadBundledVanillaRecipes(Minecraft client, List<DefaultRecipe> recipes, Set<String> seenIds) {
        try {
            var source = client.getClass().getProtectionDomain().getCodeSource();
            if (source == null) return;
            Path path = java.nio.file.Paths.get(source.getLocation().toURI());
            if (!Files.isRegularFile(path)) return;
            try (JarFile jar = new JarFile(path.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String pathName = entry.getName();
                    if (!pathName.startsWith("data/minecraft/recipe/") || !pathName.endsWith(".json")) continue;
                    try (var input = jar.getInputStream(entry)) {
                        String recipeId = "minecraft:" + pathName.substring("data/minecraft/recipe/".length(), pathName.length() - ".json".length());
                        DefaultRecipe recipe = parse(recipeId, new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8));
                        if (recipe != null && seenIds.add(recipe.id())) recipes.add(recipe);
                    }
                }
            }
        } catch (Exception ignored) {
            // Unusual launchers can hide the Minecraft JAR; datapack resources still work above.
        }
    }

    private static DefaultRecipe parse(String id, String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";
            String result = resultId(root);
            if (!type.contains("crafting_") || result.trim().isEmpty()) return null;
            if (type.contains("crafting_shaped")) return parseShaped(id, result, root);
            return parseShapeless(id, result, root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static DefaultRecipe parseShaped(String id, String result, JsonObject root) {
        if (!root.has("pattern") || !root.has("key")) return null;
        var pattern = root.getAsJsonArray("pattern");
        JsonObject keys = root.getAsJsonObject("key");
        List<String> slots = new ArrayList<>();
        int width = 0;
        for (JsonElement row : pattern) width = Math.max(width, row.getAsString().length());
        for (JsonElement row : pattern) {
            String line = row.getAsString();
            for (int column = 0; column < width; column++) {
                if (column >= line.length() || line.charAt(column) == ' ') slots.add("");
                else {
                    String item = directItem(keys.get(String.valueOf(line.charAt(column))));
                    if (item == null) return null;
                    slots.add(item);
                }
            }
        }
        return new DefaultRecipe(id, result, true, trim(new Grid(width, pattern.size(), slots)));
    }

    private static DefaultRecipe parseShapeless(String id, String result, JsonObject root) {
        if (!root.has("ingredients") || !root.get("ingredients").isJsonArray()) return null;
        List<String> ingredients = new ArrayList<>();
        for (JsonElement ingredient : root.getAsJsonArray("ingredients")) {
            String item = directItem(ingredient);
            if (item == null) return null;
            ingredients.add(item);
        }
        ingredients.sort(String::compareTo);
        return new DefaultRecipe(id, result, false, new Grid(ingredients.size(), 1, ingredients));
    }

    private static String resultId(JsonObject root) {
        JsonElement result = root.get("result");
        if (result == null) return "";
        if (result.isJsonPrimitive()) return result.getAsString();
        if (result.isJsonObject() && result.getAsJsonObject().has("id")) return result.getAsJsonObject().get("id").getAsString();
        return "";
    }

    /** Tags and ingredient alternatives need the authoritative server comparison. */
    private static String directItem(JsonElement ingredient) {
        if (ingredient == null || ingredient.isJsonNull()) return null;
        if (ingredient.isJsonPrimitive()) {
            String item = ingredient.getAsString();
            return item.startsWith("#") ? null : item;
        }
        if (!ingredient.isJsonObject()) return null;
        JsonObject object = ingredient.getAsJsonObject();
        return object.has("item") ? object.get("item").getAsString() : null;
    }

    private static boolean sameInputs(CustomRecipeEntry custom, DefaultRecipe candidate) {
        boolean shaped = "shaped".equalsIgnoreCase(custom.type);
        if (shaped != candidate.shaped()) return false;
        Grid customGrid = shaped ? customShapedGrid(custom) : customShapelessGrid(custom);
        return customGrid != null && customGrid.width() == candidate.inputs().width()
                && customGrid.height() == candidate.inputs().height()
                && customGrid.slots().equals(candidate.inputs().slots());
    }

    private static boolean sameInputs(CustomRecipeEntry first, CustomRecipeEntry second) {
        boolean shaped = "shaped".equalsIgnoreCase(first.type);
        if (shaped != "shaped".equalsIgnoreCase(second.type)) return false;
        Grid firstGrid = shaped ? customShapedGrid(first) : customShapelessGrid(first);
        Grid secondGrid = shaped ? customShapedGrid(second) : customShapelessGrid(second);
        return firstGrid != null && firstGrid.equals(secondGrid);
    }

    /** Output count intentionally does not matter: it is still a recipe-book conflict. */
    private static boolean sameOutputItem(CustomRecipeEntry first, CustomRecipeEntry second) {
        return first.result != null && first.result.equals(second.result);
    }

    private static boolean sameRecipeId(CustomRecipeEntry first, CustomRecipeEntry second) {
        return first.id != null && !first.id.trim().isEmpty() && first.id.equals(second.id);
    }

    private static Grid customShapedGrid(CustomRecipeEntry recipe) {
        if (recipe.pattern == null || recipe.keys == null) return null;
        int height = recipe.pattern.size();
        int width = recipe.pattern.stream().mapToInt(String::length).max().orElse(0);
        List<String> slots = new ArrayList<>();
        for (String row : recipe.pattern) {
            for (int column = 0; column < width; column++) {
                if (column >= row.length() || row.charAt(column) == ' ') slots.add("");
                else {
                    String item = recipe.keys.get(String.valueOf(row.charAt(column)));
                    if (item == null || item.trim().isEmpty()) return null;
                    slots.add(item);
                }
            }
        }
        return trim(new Grid(width, height, slots));
    }

    private static Grid customShapelessGrid(CustomRecipeEntry recipe) {
        if (recipe.ingredients == null || recipe.ingredients.stream().anyMatch(item -> item == null || item.trim().isEmpty())) return null;
        List<String> inputs = new ArrayList<>(recipe.ingredients);
        inputs.sort(String::compareTo);
        return new Grid(inputs.size(), 1, inputs);
    }

    private static Grid trim(Grid source) {
        int left = source.width(), right = -1, top = source.height(), bottom = -1;
        for (int row = 0; row < source.height(); row++) {
            for (int column = 0; column < source.width(); column++) {
                if (source.slots().get(row * source.width() + column).trim().isEmpty()) continue;
                left = Math.min(left, column);
                right = Math.max(right, column);
                top = Math.min(top, row);
                bottom = Math.max(bottom, row);
            }
        }
        if (right < left || bottom < top) return new Grid(0, 0, fr.zazac1.customrecipe.Java8.list());
        int width = right - left + 1;
        List<String> slots = new ArrayList<>();
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                slots.add(source.slots().get(row * source.width() + column));
            }
        }
        return new Grid(width, bottom - top + 1, slots);
    }

    private static final class DefaultRecipe {
        private final String id;
        private final String result;
        private final boolean shaped;
        private final Grid inputs;
        public DefaultRecipe(String id, String result, boolean shaped, Grid inputs) { this.id=id; this.result=result; this.shaped=shaped; this.inputs=inputs; }
        public String id() { return id; }
        public String result() { return result; }
        public boolean shaped() { return shaped; }
        public Grid inputs() { return inputs; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; DefaultRecipe other=(DefaultRecipe)obj; return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(result, other.result) && java.util.Objects.equals(shaped, other.shaped) && java.util.Objects.equals(inputs, other.inputs); }
        @Override public int hashCode() { return java.util.Objects.hash(id, result, shaped, inputs); }
}
    private static final class Grid {
        private final int width;
        private final int height;
        private final List<String> slots;
        public Grid(int width, int height, List<String> slots) { this.width=width; this.height=height; this.slots=slots; }
        public int width() { return width; }
        public int height() { return height; }
        public List<String> slots() { return slots; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; Grid other=(Grid)obj; return java.util.Objects.equals(width, other.width) && java.util.Objects.equals(height, other.height) && java.util.Objects.equals(slots, other.slots); }
        @Override public int hashCode() { return java.util.Objects.hash(width, height, slots); }
}
}
