package fr.isaac.customrecipe;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {

    /**
     * Recipe IDs (without namespace) of built-in recipes to disable.
     * Example: ["saddle", "name_tag"]
     */
    public List<String> disabled_builtin = new ArrayList<>();

    /**
     * User-created custom recipes.
     */
    public List<CustomRecipeEntry> custom_recipes = new ArrayList<>();

    /** Vrai si l'écran de bienvenue a déjà été affiché. */
    public boolean seen_welcome = false;
}
