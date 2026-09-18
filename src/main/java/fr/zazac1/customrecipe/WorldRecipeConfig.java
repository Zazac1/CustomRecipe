package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe state owned by one editable target: either the library or one world.
 * This deliberately contains every recipe-related setting so targets cannot
 * accidentally share mutable lists.
 */
public final class WorldRecipeConfig {
    public List<CustomRecipeEntry> custom_recipes = new ArrayList<>();
    public List<String> disabled_builtin = new ArrayList<>();
    public List<String> known_by_default_builtin = new ArrayList<>();
    public List<String> disabled_recipes = new ArrayList<>();
    public List<RecipeVariantRule> disabled_recipe_variants = new ArrayList<>();

    /** Built-in Quick Add entries hidden for this target. */
    public List<String> hidden_quick_add_builtin = new ArrayList<>();

    /** Independent recipe snapshots shown in this target's Quick Add sidebar. */
    public List<CustomRecipeEntry> quick_add_recipes = new ArrayList<>();

    /** True once the first-day Custom Recipes chat tip has been shown for this world. */
    public boolean shown_editor_tip = false;

    /** Creation fingerprint for the physical save folder that received the tip. */
    public String editor_tip_world_instance = "";
}
