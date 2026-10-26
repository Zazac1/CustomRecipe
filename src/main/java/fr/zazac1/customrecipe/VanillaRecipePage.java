package fr.zazac1.customrecipe;

import java.util.List;

/** Data sent to the client; recipe IDs stay authoritative on the server. */
public final class VanillaRecipePage {
        private final List<VanillaRecipeInfo> recipes;
        private final int page;
        private final int total;
        public VanillaRecipePage(List<VanillaRecipeInfo> recipes, int page, int total) { this.recipes=recipes; this.page=page; this.total=total; }
        public List<VanillaRecipeInfo> recipes() { return recipes; }
        public int page() { return page; }
        public int total() { return total; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaRecipePage other=(VanillaRecipePage)obj; return java.util.Objects.equals(recipes, other.recipes) && java.util.Objects.equals(page, other.page) && java.util.Objects.equals(total, other.total); }
        @Override public int hashCode() { return java.util.Objects.hash(recipes, page, total); }

    /**
     * Ingredients are row-major. Shaped recipes retain their exact JSON pattern;
     * shapeless recipes retain the order of their ingredients array.
     */
    public static final class VanillaRecipeInfo {
        private final String id;
        private final String result;
        private final List<String> slots;
        private final int gridWidth;
        private final int gridHeight;
        private final boolean shapeless;
        private final boolean special;
        public VanillaRecipeInfo(String id, String result, List<String> slots,
                                    int gridWidth, int gridHeight, boolean shapeless,
                                    boolean special) { this.id=id; this.result=result; this.slots=slots; this.gridWidth=gridWidth; this.gridHeight=gridHeight; this.shapeless=shapeless; this.special=special; }
        public String id() { return id; }
        public String result() { return result; }
        public List<String> slots() { return slots; }
        public int gridWidth() { return gridWidth; }
        public int gridHeight() { return gridHeight; }
        public boolean shapeless() { return shapeless; }
        public boolean special() { return special; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaRecipeInfo other=(VanillaRecipeInfo)obj; return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(result, other.result) && java.util.Objects.equals(slots, other.slots) && java.util.Objects.equals(gridWidth, other.gridWidth) && java.util.Objects.equals(gridHeight, other.gridHeight) && java.util.Objects.equals(shapeless, other.shapeless) && java.util.Objects.equals(special, other.special); }
        @Override public int hashCode() { return java.util.Objects.hash(id, result, slots, gridWidth, gridHeight, shapeless, special); }
}
}
