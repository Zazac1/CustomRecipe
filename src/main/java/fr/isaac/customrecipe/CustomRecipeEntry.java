package fr.isaac.customrecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single user-created custom crafting recipe.
 * Serialized to / from JSON via Gson.
 */
public class CustomRecipeEntry {

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
}
