package fr.zazac1.customrecipe;

import java.util.List;
import java.util.Objects;

/**
 * Data sent to the client; recipe IDs stay authoritative on the server.
 * Ordinary fields and a no-arg constructor support Minecraft's pre-record Gson.
 */
public final class VanillaRecipePage {
    private List<VanillaRecipeInfo> recipes = List.of();
    private int page;
    private int total;

    private VanillaRecipePage() {}

    public VanillaRecipePage(List<VanillaRecipeInfo> recipes, int page, int total) {
        this.recipes = recipes;
        this.page = page;
        this.total = total;
    }

    public List<VanillaRecipeInfo> recipes() { return recipes; }
    public int page() { return page; }
    public int total() { return total; }

    @Override public boolean equals(Object other) {
        return other instanceof VanillaRecipePage value
                && page == value.page && total == value.total && Objects.equals(recipes, value.recipes);
    }
    @Override public int hashCode() { return Objects.hash(recipes, page, total); }

    /**
     * Ingredients are row-major. Shaped recipes retain their exact JSON pattern;
     * shapeless recipes retain the order of their ingredients array.
     */
    public static final class VanillaRecipeInfo {
        private String id;
        private String result;
        private List<String> slots = List.of();
        private int gridWidth;
        private int gridHeight;
        private boolean shapeless;
        private boolean special;

        private VanillaRecipeInfo() {}

        public VanillaRecipeInfo(String id, String result, List<String> slots,
                                 int gridWidth, int gridHeight, boolean shapeless, boolean special) {
            this.id = id;
            this.result = result;
            this.slots = slots;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.shapeless = shapeless;
            this.special = special;
        }

        public String id() { return id; }
        public String result() { return result; }
        public List<String> slots() { return slots; }
        public int gridWidth() { return gridWidth; }
        public int gridHeight() { return gridHeight; }
        public boolean shapeless() { return shapeless; }
        public boolean special() { return special; }

        @Override public boolean equals(Object other) {
            return other instanceof VanillaRecipeInfo value
                    && gridWidth == value.gridWidth && gridHeight == value.gridHeight
                    && shapeless == value.shapeless && special == value.special
                    && Objects.equals(id, value.id) && Objects.equals(result, value.result)
                    && Objects.equals(slots, value.slots);
        }
        @Override public int hashCode() {
            return Objects.hash(id, result, slots, gridWidth, gridHeight, shapeless, special);
        }
    }
}
