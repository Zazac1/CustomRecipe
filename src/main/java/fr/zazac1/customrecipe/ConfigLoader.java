package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("customrecipe.json");

    private static ModConfig cached = null;

    public static ModConfig get() {
        if (cached == null) cached = load();
        return cached;
    }

    /** Forces a reload from disk (called before each recipe injection). */
    public static void invalidate() {
        cached = null;
    }

    public static void saveAndInvalidate(ModConfig config) {
        normalize(config);
        save(config);
        invalidate();
    }

    /** Persists an automatically detected integrity state without discarding the loaded config. */
    public static void saveIntegrityState(ModConfig config) {
        normalize(config);
        save(config);
    }

    /** Serializes a config for the OP-only server editor. */
    public static String toJson(ModConfig config) {
        normalize(config);
        return GSON.toJson(config);
    }

    /** Compares recipes without relying on their position in a config file. */
    public static boolean sameRecipe(CustomRecipeEntry first, CustomRecipeEntry second) {
        if (first == null || second == null) return false;
        if (first.id != null && !first.id.isBlank() && second.id != null && !second.id.isBlank()) {
            return first.id.equals(second.id);
        }
        return recipeFingerprint(first).equals(recipeFingerprint(second));
    }

    /** Returns null when a JSON payload is invalid. */
    public static ModConfig fromJson(String json) {
        try {
            ModConfig config = GSON.fromJson(json, ModConfig.class);
            if (config == null) return null;
            normalize(config);
            return config;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaults = new ModConfig();
            save(defaults);
            CustomRecipeMod.LOGGER.info("[CustomRecipe] Config created at: {}", CONFIG_PATH);
            return defaults;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            ModConfig config = fromJson(json);
            return config != null ? config : new ModConfig();
        } catch (IOException e) {
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to read config: {}", e.getMessage());
            return new ModConfig();
        }
    }

    private static void save(ModConfig config) {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) {
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to write config: {}", e.getMessage());
        }
    }

    private static void normalize(ModConfig config) {
        if (config.disabled_builtin == null) config.disabled_builtin = new java.util.ArrayList<>();
        if (config.known_by_default_builtin == null) config.known_by_default_builtin = new java.util.ArrayList<>();
        if (config.disabled_recipes == null) config.disabled_recipes = new java.util.ArrayList<>();
        if (config.disabled_recipe_variants == null) config.disabled_recipe_variants = new java.util.ArrayList<>();
        if (config.custom_recipes == null) config.custom_recipes = new java.util.ArrayList<>();

        Set<String> variantKeys = new HashSet<>();
        config.disabled_recipe_variants.removeIf(rule -> rule == null || rule.recipe_id == null || rule.recipe_id.isBlank()
                || rule.material_id == null || rule.material_id.isBlank()
                || !variantKeys.add(rule.recipe_id + "\u0000" + rule.material_id));

        Map<String, CustomRecipeEntry> legacyRecipes = new LinkedHashMap<>();
        List<CustomRecipeEntry> normalizedRecipes = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (CustomRecipeEntry recipe : config.custom_recipes) {
            if (recipe == null) continue;
            if (recipe.required_mods == null) recipe.required_mods = new LinkedHashMap<>();
            if (recipe.missing_items == null) recipe.missing_items = new ArrayList<>();
            if (recipe.conflicting_recipes == null) recipe.conflicting_recipes = new ArrayList<>();
            // Conflict metadata written before the same-output rule existed is invalid:
            // it contained every recipe with matching inputs, including different outputs.
            if (recipe.same_shape_recipes == null) {
                recipe.same_shape_recipes = new ArrayList<>();
                recipe.conflicting_recipes.clear();
            }
            RecipeIntegrity.rememberRequiredMods(recipe);
            if (recipe.id == null || recipe.id.isBlank()) {
                String fingerprint = recipeFingerprint(recipe);
                CustomRecipeEntry existing = legacyRecipes.get(fingerprint);
                if (existing != null) {
                    // Old configs had no ID. A disabled server state wins over an enabled duplicate.
                    if (Boolean.FALSE.equals(recipe.enabled) && !Boolean.FALSE.equals(existing.enabled)) {
                        normalizedRecipes.set(normalizedRecipes.indexOf(existing), recipe);
                        legacyRecipes.put(fingerprint, recipe);
                    }
                    continue;
                }
                recipe.id = "legacy-" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
                legacyRecipes.put(fingerprint, recipe);
            }
            if (!usedIds.add(recipe.id)) {
                recipe.id = "duplicate-" + UUID.nameUUIDFromBytes(
                        (recipeFingerprint(recipe) + "#" + normalizedRecipes.size()).getBytes(StandardCharsets.UTF_8));
                usedIds.add(recipe.id);
            }
            normalizedRecipes.add(recipe);
        }
        config.custom_recipes = normalizedRecipes;
    }

    private static String recipeFingerprint(CustomRecipeEntry recipe) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", recipe.type);
        data.put("ingredients", recipe.ingredients);
        data.put("pattern", recipe.pattern);
        data.put("keys", recipe.keys);
        data.put("result", recipe.result);
        data.put("count", recipe.count);
        return GSON.toJson(data);
    }

    private ConfigLoader() {}
}
