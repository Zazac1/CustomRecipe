package fr.zazac1.customrecipe;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Represents a single user-created custom crafting recipe.
 * Serialized to / from JSON via Gson.
 */
public class CustomRecipeEntry {

    /** Stable identity shared by the local editor and the server. */
    public String id = "";

    /** "shapeless" or "shaped" */
    public String type = "shapeless";

    // ── shapeless ─────────────────────────────────────────────────────────
    /** List of ingredient item IDs (e.g. "minecraft:diamond"). Up to 9. */
    public List<String> ingredients = new ArrayList<>();

    // ── shaped ────────────────────────────────────────────────────────────
    /** 3 pattern rows, each up to 3 characters (' ' = empty slot). */
    public List<String> pattern = new ArrayList<>();
    /** Maps pattern character → item ID. */
    public Map<String, String> keys = new LinkedHashMap<>();

    // ── common ────────────────────────────────────────────────────────────
    /** Result item ID. */
    public String result = "";
    /** Result stack size (1–64). */
    public int count = 1;

    /**
     * null = activée (compatibilité avec les anciennes configs).
     * Boolean.FALSE = désactivée.
     */
    public Boolean enabled;

    /**
     * Server publication state. {@code null} keeps existing configurations
     * compatible: recipes created before this field existed remain published.
     * New ModMenu recipes start as {@code false} until an OP adds them.
     */
    public Boolean server_enabled;

    /** Whether every player should know this recipe as soon as they join. */
    public Boolean known_by_default;

    /** Non-vanilla mod IDs and versions present when this recipe was saved. */
    public Map<String, String> required_mods = new LinkedHashMap<>();

    /** Set automatically when an output or ingredient item no longer exists. */
    public Boolean corrupted;

    /** Missing item IDs that caused this recipe to be marked corrupted. */
    public List<String> missing_items = new ArrayList<>();

    /** Default crafting recipe IDs with identical inputs and output item. */
    public List<String> conflicting_recipes = new ArrayList<>();

    /** Default crafting recipe IDs with identical inputs but a different output item. */
    public List<String> same_shape_recipes = new ArrayList<>();

    /** Stable recipe ID shared by reloads and recipe-book unlocks. */
    public Identifier serverRecipeId() {
        String source = id == null || id.isBlank() ? result : id;
        String safeId = source == null ? "recipe"
                : source.replaceAll("[^a-zA-Z0-9_./-]", "_").toLowerCase(Locale.ROOT);
        return Identifier.of(CustomRecipeMod.MOD_ID, "custom/" + safeId);
    }
}
