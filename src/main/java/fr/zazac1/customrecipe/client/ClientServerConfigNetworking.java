package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.ModNetworking;
import com.google.gson.Gson;

import java.util.List;

/** Client-only sender for the OP server editor. */
public final class ClientServerConfigNetworking {
    private static final Gson GSON = new Gson();
    public static void save(ModConfig config) {
        ModNetworking.save(ConfigLoader.toJson(config));
    }

    /** Requests a server-only integrity and conflict check without saving anything. */
    public static void validate(ModConfig config) {
        ModNetworking.validate(ConfigLoader.toJson(config));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput,
                                     String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {
        ModNetworking.queryVanilla(GSON.toJson(new RecipeQuery(query, matchIngredients, matchOutput,
                statusFilter, sourceFilter, disabledRecipeIds, page)));
    }

    public static void requestVanillaDetails(String recipeId) {
        ModNetworking.queryVanillaDetails(recipeId);
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ClientServerConfigNetworking() {}
}
