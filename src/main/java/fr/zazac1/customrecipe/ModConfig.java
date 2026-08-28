package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {

    /**
     * Recipe IDs (without namespace) of built-in recipes to disable.
     * Example: ["saddle", "name_tag"]
     */
    public List<String> disabled_builtin = new ArrayList<>();

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
}
