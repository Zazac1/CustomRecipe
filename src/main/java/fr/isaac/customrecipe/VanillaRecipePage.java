package fr.isaac.customrecipe;

import java.util.List;

/** Data sent to the client; recipe IDs stay authoritative on the server. */
public record VanillaRecipePage(List<VanillaRecipeInfo> recipes, int page, int total) {
    /**
     * Ingredients are row-major. Shaped recipes retain their exact JSON pattern;
     * shapeless recipes retain the order of their ingredients array.
     */
    public record VanillaRecipeInfo(String id, String result, List<String> slots,
                                    int gridWidth, int gridHeight, boolean shapeless) {}
}
