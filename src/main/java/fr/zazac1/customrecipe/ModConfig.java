package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {

    /** Schema 1 replaces shared recipe lists with independently editable targets. */
    public int recipe_target_version = 0;

    /** Reusable recipes. This target is intentionally not applied at runtime. */
    public WorldRecipeConfig global_library = new WorldRecipeConfig();

    /** Fully isolated state for each save, keyed by WorldRecipeAssignments.worldId. */
    public Map<String, WorldRecipeConfig> world_configs = new LinkedHashMap<>();

    /** Display names are metadata only; IDs remain the durable keys. */
    public Map<String, String> world_names = new LinkedHashMap<>();

    /** Server-only editor context, cleared before config persistence. */
    public String editor_world_id = "";
    public String editor_world_name = "";

    /**
     * Recipe IDs (without namespace) of built-in recipes to disable.
     * Example: ["saddle", "name_tag"]
     */
    public List<String> disabled_builtin = new ArrayList<>();

    /** Built-in recipe IDs (without namespace) unlocked for every player. */
    public List<String> known_by_default_builtin = new ArrayList<>();

    /**
     * Full recipe IDs disabled by a server administrator.
     * Examples: ["minecraft:torch", "some_mod:machine_recipe"].
     */
    public List<String> disabled_recipes = new ArrayList<>();

    /** Recipe/material pairs disabled without removing the entire recipe. */
    public List<RecipeVariantRule> disabled_recipe_variants = new ArrayList<>();

    /**
     * User-created custom recipes.
     */
    public List<CustomRecipeEntry> custom_recipes = new ArrayList<>();

    /** Vrai si l'écran de bienvenue a déjà été affiché. */
    public boolean seen_welcome = false;

    /** Local worlds that already received the editor tip on first entry. */
    public List<String> shown_world_editor_tips = new ArrayList<>();

    public WorldRecipeConfig getOrCreateWorldConfig(String worldId, String worldName) {
        if (worldId == null || worldId.isBlank()) return global_library;
        if (world_configs == null) world_configs = new LinkedHashMap<>();
        if (world_names == null) world_names = new LinkedHashMap<>();
        if (worldName != null && !worldName.isBlank()) world_names.put(worldId, worldName);
        return world_configs.computeIfAbsent(worldId, ignored -> new WorldRecipeConfig());
    }

    public WorldRecipeConfig findWorldConfig(String worldId) {
        return world_configs == null || worldId == null ? null : world_configs.get(worldId);
    }
}
