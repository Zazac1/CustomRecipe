package fr.zazac1.customrecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

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
    /** Required stack size for each shapeless ingredient slot (1–64). */
    public List<Integer> ingredient_counts = new ArrayList<>();

    // ── shaped ────────────────────────────────────────────────────────────
    /** 3 pattern rows, each up to 3 characters (' ' = empty slot). */
    public List<String> pattern = new ArrayList<>();
    /** Maps pattern character → item ID. */
    public Map<String, String> keys = new LinkedHashMap<>();
    /** Required stack size for each shaped pattern character (1–64). */
    public Map<String, Integer> key_counts = new LinkedHashMap<>();

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

    /** World IDs where this recipe is currently available. Null marks a legacy global recipe. */
    public List<String> world_ids;

    /** Human-readable world names for the assignment editor. */
    public Map<String, String> world_names;

    /** Whether every player learns this recipe automatically. */
    public Boolean known_by_default;

    /** Shows this saved recipe in the Quick Add sidebar. */
    public Boolean quick_add;

    /** Global Library state: automatically keeps this recipe available in every world. */
    public Boolean global_auto_add;

    /** Source library recipe for an automatically managed world copy. */
    public String global_library_source_id;

    /** Non-vanilla mod IDs and versions present when this recipe was saved. */
    public Map<String, String> required_mods = new LinkedHashMap<>();

    /** Set automatically when an output or ingredient item no longer exists. */
    public Boolean corrupted;

    /** Missing item IDs that caused {@link #corrupted} to be set. */
    public List<String> missing_items = new ArrayList<>();

    /** Active crafting recipe IDs that can match the same grid and produce the same item. */
    public List<String> conflicting_recipes = new ArrayList<>();

    /** Active crafting recipe IDs that can match the same grid but produce a different item. */
    public List<String> same_shape_recipes = new ArrayList<>();

    public int ingredientCount(int index) {
        if (ingredient_counts == null || index < 0 || index >= ingredient_counts.size()) return 1;
        Integer value = ingredient_counts.get(index);
        return clampIngredientCount(value == null ? 1 : value);
    }

    public int keyCount(String key) {
        if (key_counts == null || key == null) return 1;
        Integer value = key_counts.get(key);
        return clampIngredientCount(value == null ? 1 : value);
    }

    public boolean hasCountedIngredients() {
        if (ingredient_counts != null && ingredient_counts.stream().anyMatch(value -> value != null && value > 1)) {
            return true;
        }
        return key_counts != null && key_counts.values().stream().anyMatch(value -> value != null && value > 1);
    }

    public static int clampIngredientCount(int value) {
        return Math.max(1, Math.min(64, value));
    }

    /** Stable server key used for recipe loading and automatic recipe-book unlocks. */
    public ResourceLocation serverRecipeId() {
        String source = id == null || id.isBlank() ? result : id;
        String safeId = source == null ? "recipe"
                : source.replaceAll("[^a-zA-Z0-9_./-]", "_").toLowerCase(java.util.Locale.ROOT);
        return new ResourceLocation(CustomRecipeMod.MOD_ID, "custom/" + safeId);
    }
}
