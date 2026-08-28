package fr.zazac1.customrecipe;

/** Blocks one vanilla/modded crafting recipe only when it uses this material. */
public class RecipeVariantRule {
    public String recipe_id = "";
    public String material_id = "";

    public RecipeVariantRule() {}

    public RecipeVariantRule(String recipeId, String materialId) {
        this.recipe_id = recipeId;
        this.material_id = materialId;
    }
}
