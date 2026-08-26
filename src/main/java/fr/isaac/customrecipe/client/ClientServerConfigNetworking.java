package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.ModConfig;
import fr.isaac.customrecipe.SaveServerConfigPayload;
import fr.isaac.customrecipe.VanillaRecipeQueryPayload;
import fr.isaac.customrecipe.VanillaRecipeDetailsQueryPayload;
import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-only sender for the OP server editor. */
@Environment(EnvType.CLIENT)
public final class ClientServerConfigNetworking {
    private static final Gson GSON = new Gson();
    public static void save(ModConfig config) {
        ClientPlayNetworking.send(new SaveServerConfigPayload(ConfigLoader.toJson(config)));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput, int page) {
        ClientPlayNetworking.send(new VanillaRecipeQueryPayload(
                GSON.toJson(new RecipeQuery(query, matchIngredients, matchOutput, page))));
    }

    public static void requestVanillaDetails(String recipeId) {
        ClientPlayNetworking.send(new VanillaRecipeDetailsQueryPayload(recipeId));
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput, int page) {}

    private ClientServerConfigNetworking() {}
}
