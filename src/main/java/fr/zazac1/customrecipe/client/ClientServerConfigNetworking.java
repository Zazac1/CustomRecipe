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

import java.util.List;
import java.util.function.BiConsumer;

/** Client-only sender for the OP server editor. */
@Environment(EnvType.CLIENT)
public final class ClientServerConfigNetworking {
    private static final int MAX_CONFIG_CHARS = 500_000;
    private static final Gson GSON = new Gson();
    private static BiConsumer<Boolean, String> pendingSave;

    public static void save(ModConfig config) {
        ClientPlayNetworking.send(SaveServerConfigPayload.ID,
                PacketByteBufs.create().writeString(ConfigLoader.toJson(config), MAX_CONFIG_CHARS));
    }

    /** Sends one save request and waits for the server's durable-write acknowledgement. */
    public static boolean save(ModConfig config, BiConsumer<Boolean, String> completion) {
        if (pendingSave != null) return false;
        String json = ConfigLoader.toJson(config);
        if (json.length() > MAX_CONFIG_CHARS) {
            completion.accept(false, "The configuration is too large to send to the server.");
            return false;
        }
        pendingSave = completion;
        ClientPlayNetworking.send(SaveServerConfigPayload.ID, PacketByteBufs.create().writeString(json, MAX_CONFIG_CHARS));
        return true;
    }

    static void completeSave(boolean saved, String message) {
        BiConsumer<Boolean, String> completion = pendingSave;
        pendingSave = null;
        if (completion != null) completion.accept(saved, message == null ? "" : message);
    }

    public static void validate(ModConfig config) {
        ClientPlayNetworking.send(ValidateServerConfigPayload.ID,
                PacketByteBufs.create().writeString(ConfigLoader.toJson(config), MAX_CONFIG_CHARS));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput,
                                     String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {
        ClientPlayNetworking.send(VanillaRecipeQueryPayload.ID,
                PacketByteBufs.create().writeString(GSON.toJson(new RecipeQuery(query, matchIngredients, matchOutput,
                        statusFilter, sourceFilter, disabledRecipeIds, page))));
    }

    public static void requestVanillaDetails(String recipeId) {
        ClientPlayNetworking.send(VanillaRecipeDetailsQueryPayload.ID, PacketByteBufs.create().writeString(recipeId));
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ClientServerConfigNetworking() {}
}
