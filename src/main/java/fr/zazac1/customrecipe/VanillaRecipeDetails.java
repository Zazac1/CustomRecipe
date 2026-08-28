package fr.zazac1.customrecipe;

import java.util.List;

/** Alternative material previews for one recipe, fetched only when its preview is opened. */
public record VanillaRecipeDetails(String recipeId, List<VariantPreview> variants) {
    public record VariantPreview(String materialId, List<String> slots) {}
}
