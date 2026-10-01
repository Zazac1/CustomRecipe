package fr.zazac1.customrecipe;

import java.util.List;

/** IDs of the optional recipe templates shipped with Custom Recipe. */
public final class BuiltinRecipeIds {
    public static final List<String> ALL = List.of(
            "totem_of_undying",
            "enchanted_golden_apple",
            "elytra",
            "experience_bottle",
            "heavy_core"
    );

    public static boolean contains(String id) {
        return ALL.contains(id);
    }

    private BuiltinRecipeIds() {}
}
