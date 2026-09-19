package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.SaveServerConfigPayload;
import fr.zazac1.customrecipe.ValidateServerConfigPayload;
import fr.zazac1.customrecipe.VanillaRecipeQueryPayload;
import fr.zazac1.customrecipe.VanillaRecipeDetailsQueryPayload;
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

    /** Requests a server-only integrity and conflict check without saving anything. */
    public static void validate(ModConfig config) {
        ClientPlayNetworking.send(new ValidateServerConfigPayload(ConfigLoader.toJson(config)));
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
