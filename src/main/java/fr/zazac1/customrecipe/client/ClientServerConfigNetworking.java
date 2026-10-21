package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.ModNetworking;
import com.google.gson.Gson;

import java.util.List;
import java.util.function.BiConsumer;

/** Client-only sender for the OP server editor. */
public final class ClientServerConfigNetworking {
    private static final int MAX_CONFIG_CHARS = 1_000_000;
    private static final Gson GSON = new Gson();
    private static BiConsumer<Boolean, String> pendingSave;
    public static void save(ModConfig config) {
        ModNetworking.save(ConfigLoader.toJson(config));
    }

    public static boolean save(ModConfig config, BiConsumer<Boolean, String> completion) {
        if (!canManageServer()) {
            completion.accept(false, "The server editor is not available for this connection.");
            return false;
        }
        if (pendingSave != null) {
            completion.accept(false, "A server save is already in progress.");
            return false;
        }
        String json = ConfigLoader.toJson(config);
        if (json.length() > MAX_CONFIG_CHARS) {
            completion.accept(false, "The configuration is too large to send to the server.");
            return false;
        }
        pendingSave = completion;
        ModNetworking.save(json);
        return true;
    }

    static void completeSave(boolean saved, String message) {
        BiConsumer<Boolean, String> completion = pendingSave;
        pendingSave = null;
        if (completion != null) completion.accept(saved, message == null ? "" : message);
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

    public static boolean canManageServer() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.hasPermissions(2);
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ClientServerConfigNetworking() {}
}
