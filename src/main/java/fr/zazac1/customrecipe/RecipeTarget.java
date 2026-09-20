package fr.zazac1.customrecipe;

/** A destination edited by Recipe Creator. */
public interface RecipeTarget {
    String id();
    String displayName();
    WorldRecipeConfig resolve(ModConfig config);
    boolean isWorld();
}
