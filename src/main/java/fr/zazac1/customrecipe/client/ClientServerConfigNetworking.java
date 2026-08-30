package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.SaveServerConfigPayload;
import fr.zazac1.customrecipe.VanillaRecipeQueryPayload;
import fr.zazac1.customrecipe.VanillaRecipeDetailsQueryPayload;
import fr.zazac1.customrecipe.ValidateServerConfigPayload;
import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

/** Client-only sender for the OP server editor. */
@Environment(EnvType.CLIENT)
public final class ClientServerConfigNetworking {
    private static final Gson GSON = new Gson();
    public static void save(ModConfig config) {
        ClientPlayNetworking.send(SaveServerConfigPayload.ID, PacketByteBufs.create().writeString(ConfigLoader.toJson(config)));
    }

    public static void validate(ModConfig config) {
        ClientPlayNetworking.send(ValidateServerConfigPayload.ID,
                PacketByteBufs.create().writeString(ConfigLoader.toJson(config)));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput, int page) {
        ClientPlayNetworking.send(VanillaRecipeQueryPayload.ID,
                PacketByteBufs.create().writeString(GSON.toJson(new RecipeQuery(query, matchIngredients, matchOutput, page))));
    }

    public static void requestVanillaDetails(String recipeId) {
        ClientPlayNetworking.send(VanillaRecipeDetailsQueryPayload.ID, PacketByteBufs.create().writeString(recipeId));
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput, int page) {}

    private ClientServerConfigNetworking() {}
}
