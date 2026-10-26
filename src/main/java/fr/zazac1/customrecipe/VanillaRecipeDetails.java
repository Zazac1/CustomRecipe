package fr.zazac1.customrecipe;

import java.util.List;
import java.util.Objects;

/** Material previews with fields that Minecraft's older Gson can deserialize. */
public final class VanillaRecipeDetails {
    private String recipeId;
    private List<VariantPreview> variants = List.of();

    private VanillaRecipeDetails() {}

    public VanillaRecipeDetails(String recipeId, List<VariantPreview> variants) {
        this.recipeId = recipeId;
        this.variants = variants;
    }

    public String recipeId() { return recipeId; }
    public List<VariantPreview> variants() { return variants; }

    @Override public boolean equals(Object other) {
        return other instanceof VanillaRecipeDetails value
                && Objects.equals(recipeId, value.recipeId) && Objects.equals(variants, value.variants);
    }
    @Override public int hashCode() { return Objects.hash(recipeId, variants); }

    public static final class VariantPreview {
        private String materialId;
        private List<String> slots = List.of();

        private VariantPreview() {}

        public VariantPreview(String materialId, List<String> slots) {
            this.materialId = materialId;
            this.slots = slots;
        }

        public String materialId() { return materialId; }
        public List<String> slots() { return slots; }

        @Override public boolean equals(Object other) {
            return other instanceof VariantPreview value
                    && Objects.equals(materialId, value.materialId) && Objects.equals(slots, value.slots);
        }
        @Override public int hashCode() { return Objects.hash(materialId, slots); }
    }
}
