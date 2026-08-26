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
}
