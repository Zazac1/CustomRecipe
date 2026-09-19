package fr.zazac1.customrecipe;

/** The reusable recipe library. Library entries are never applied to a world. */
public final class GlobalRecipeTarget implements RecipeTarget {
    public static final GlobalRecipeTarget INSTANCE = new GlobalRecipeTarget();

    private GlobalRecipeTarget() {}

    @Override public String id() { return "global"; }
    @Override public String displayName() { return "Global Library"; }
    @Override public WorldRecipeConfig resolve(ModConfig config) { return config.global_library; }
    @Override public boolean isWorld() { return false; }
}
