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
import java.util.LinkedHashSet;
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

    /** Runtime settings for the loaded save. The library is intentionally not a fallback. */
    public static WorldRecipeConfig activeWorldConfig() {
        return activeWorldConfig(get());
    }

    public static WorldRecipeConfig activeWorldConfig(ModConfig config) {
        if (config == null) return new WorldRecipeConfig();
        WorldRecipeConfig world = config.findWorldConfig(WorldRecipeAssignments.activeWorldId());
        return world == null ? new WorldRecipeConfig() : world;
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
            normalize(defaults);
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
        if (config.editor_world_id == null) config.editor_world_id = "";
        if (config.editor_world_name == null) config.editor_world_name = "";
        if (config.disabled_builtin == null) config.disabled_builtin = new java.util.ArrayList<>();
        if (config.known_by_default_builtin == null) config.known_by_default_builtin = new java.util.ArrayList<>();
        if (config.disabled_recipes == null) config.disabled_recipes = new java.util.ArrayList<>();
        if (config.disabled_recipe_variants == null) config.disabled_recipe_variants = new java.util.ArrayList<>();
        if (config.custom_recipes == null) config.custom_recipes = new java.util.ArrayList<>();

        // Version 0 stored one shared list plus optional world_ids on recipes.
        // Split those recipes once: a library recipe remains in the library and
        // every assigned world receives its own independent copy.
        if (config.recipe_target_version < 1) migrateLegacyTargets(config);
        if (config.global_library == null) config.global_library = new WorldRecipeConfig();
        if (config.world_configs == null) config.world_configs = new LinkedHashMap<>();
        if (config.world_names == null) config.world_names = new LinkedHashMap<>();
        normalizeRecipeConfig(config.global_library);
        config.world_configs.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null);
        for (WorldRecipeConfig worldConfig : config.world_configs.values()) normalizeRecipeConfig(worldConfig);

        // Retain the old fields while saved files transition. Runtime code and
        // the editor now read targets exclusively.
        WorldRecipeConfig legacyFields = new WorldRecipeConfig();
        legacyFields.custom_recipes = config.custom_recipes;
        legacyFields.disabled_builtin = config.disabled_builtin;
        legacyFields.known_by_default_builtin = config.known_by_default_builtin;
        legacyFields.disabled_recipes = config.disabled_recipes;
        legacyFields.disabled_recipe_variants = config.disabled_recipe_variants;
        normalizeRecipeConfig(legacyFields);
        config.custom_recipes = legacyFields.custom_recipes;
        config.disabled_builtin = legacyFields.disabled_builtin;
        config.known_by_default_builtin = legacyFields.known_by_default_builtin;
        config.disabled_recipes = legacyFields.disabled_recipes;
        config.disabled_recipe_variants = legacyFields.disabled_recipe_variants;
    }

    /** Deep copy used when a library recipe is added to a world. */
    public static CustomRecipeEntry copyRecipe(CustomRecipeEntry source) {
        return copyForTarget(source);
    }

    private static void migrateLegacyTargets(ModConfig config) {
        WorldRecipeConfig library = new WorldRecipeConfig();
        library.disabled_builtin = new ArrayList<>(config.disabled_builtin);
        library.known_by_default_builtin = new ArrayList<>(config.known_by_default_builtin);
        library.disabled_recipes = new ArrayList<>(config.disabled_recipes);
        library.disabled_recipe_variants = new ArrayList<>(config.disabled_recipe_variants);
        Map<String, WorldRecipeConfig> worlds = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();

        for (CustomRecipeEntry recipe : config.custom_recipes) {
            if (recipe == null || recipe.world_ids == null || recipe.world_ids.isEmpty()) {
                library.custom_recipes.add(copyForTarget(recipe));
                continue;
            }
            for (String worldId : recipe.world_ids) {
                if (worldId == null || worldId.isBlank()) continue;
                worlds.computeIfAbsent(worldId, ignored -> new WorldRecipeConfig())
                        .custom_recipes.add(copyForTarget(recipe));
                if (recipe.world_names != null && recipe.world_names.containsKey(worldId)) {
                    names.put(worldId, recipe.world_names.get(worldId));
                }
            }
        }
        config.global_library = library;
        config.world_configs = worlds;
        config.world_names = names;
        config.recipe_target_version = 1;
    }

    /** Gson copying preserves new metadata without risking shared recipe lists. */
    private static CustomRecipeEntry copyForTarget(CustomRecipeEntry source) {
        if (source == null) return null;
        CustomRecipeEntry copy = GSON.fromJson(GSON.toJson(source), CustomRecipeEntry.class);
        copy.world_ids = null;
        copy.world_names = null;
        return copy;
    }

    private static void normalizeRecipeConfig(WorldRecipeConfig recipeConfig) {
        if (recipeConfig.disabled_builtin == null) recipeConfig.disabled_builtin = new ArrayList<>();
        if (recipeConfig.known_by_default_builtin == null) recipeConfig.known_by_default_builtin = new ArrayList<>();
        if (recipeConfig.disabled_recipes == null) recipeConfig.disabled_recipes = new ArrayList<>();
        if (recipeConfig.disabled_recipe_variants == null) recipeConfig.disabled_recipe_variants = new ArrayList<>();
        if (recipeConfig.hidden_quick_add_builtin == null) recipeConfig.hidden_quick_add_builtin = new ArrayList<>();
        if (recipeConfig.quick_add_recipes == null) recipeConfig.quick_add_recipes = new ArrayList<>();
        if (recipeConfig.custom_recipes == null) recipeConfig.custom_recipes = new ArrayList<>();

        // v1.3.0 stores Quick Add as independent snapshots rather than a flag
        // linking back to a mutable library/world recipe.
        for (CustomRecipeEntry recipe : recipeConfig.custom_recipes) {
            if (!Boolean.TRUE.equals(recipe.quick_add)) continue;
            CustomRecipeEntry snapshot = copyForTarget(recipe);
            snapshot.quick_add = null;
            recipeConfig.quick_add_recipes.add(snapshot);
            recipe.quick_add = null;
        }

        Set<String> variantKeys = new HashSet<>();
        recipeConfig.disabled_recipe_variants.removeIf(rule -> rule == null || rule.recipe_id == null || rule.recipe_id.isBlank()
                || rule.material_id == null || rule.material_id.isBlank()
                || !variantKeys.add(rule.recipe_id + "\u0000" + rule.material_id));

        Map<String, CustomRecipeEntry> legacyRecipes = new LinkedHashMap<>();
        List<CustomRecipeEntry> normalizedRecipes = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (CustomRecipeEntry recipe : recipeConfig.custom_recipes) {
            if (recipe == null) continue;
            if (recipe.required_mods == null) recipe.required_mods = new LinkedHashMap<>();
            if (recipe.missing_items == null) recipe.missing_items = new ArrayList<>();
            if (recipe.conflicting_recipes == null) recipe.conflicting_recipes = new ArrayList<>();
            if (recipe.world_ids != null) {
                recipe.world_ids.removeIf(worldId -> worldId == null || worldId.isBlank());
                recipe.world_ids = new ArrayList<>(new LinkedHashSet<>(recipe.world_ids));
                if (recipe.world_names == null) recipe.world_names = new LinkedHashMap<>();
                recipe.world_names.keySet().removeIf(worldId -> !recipe.world_ids.contains(worldId));
            } else {
                recipe.world_names = null;
            }
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
        recipeConfig.custom_recipes = normalizedRecipes;
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
