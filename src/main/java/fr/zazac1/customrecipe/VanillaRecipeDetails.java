package fr.zazac1.customrecipe;

import java.util.List;

/** Alternative material previews for one recipe, fetched only when its preview is opened. */
public final class VanillaRecipeDetails {
        private final String recipeId;
        private final List<VariantPreview> variants;
        public VanillaRecipeDetails(String recipeId, List<VariantPreview> variants) { this.recipeId=recipeId; this.variants=variants; }
        public String recipeId() { return recipeId; }
        public List<VariantPreview> variants() { return variants; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaRecipeDetails other=(VanillaRecipeDetails)obj; return java.util.Objects.equals(recipeId, other.recipeId) && java.util.Objects.equals(variants, other.variants); }
        @Override public int hashCode() { return java.util.Objects.hash(recipeId, variants); }

    public static final class VariantPreview {
        private final String materialId;
        private final List<String> slots;
        public VariantPreview(String materialId, List<String> slots) { this.materialId=materialId; this.slots=slots; }
        public String materialId() { return materialId; }
        public List<String> slots() { return slots; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VariantPreview other=(VariantPreview)obj; return java.util.Objects.equals(materialId, other.materialId) && java.util.Objects.equals(slots, other.slots); }
        @Override public int hashCode() { return java.util.Objects.hash(materialId, slots); }
}
}
