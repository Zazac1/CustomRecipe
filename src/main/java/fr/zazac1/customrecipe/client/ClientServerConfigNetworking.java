package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.SaveServerConfigPayload;
import fr.zazac1.customrecipe.VanillaRecipeQueryPayload;
import fr.zazac1.customrecipe.VanillaRecipeDetailsQueryPayload;
import fr.zazac1.customrecipe.ValidateServerConfigPayload;
import fr.zazac1.customrecipe.ModNetworking;
import com.google.gson.Gson;

import java.util.List;
import java.util.function.BiConsumer;

/** Client-only sender for the OP server editor. */
public final class ClientServerConfigNetworking {
    private static final int MAX_CONFIG_CHARS = 500_000;
    private static final Gson GSON = new Gson();
    private static BiConsumer<Boolean, String> pendingSave;

    public static void save(ModConfig config) {
        ModNetworking.sendSave(ConfigLoader.toJson(config));
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
        ModNetworking.sendSave(json);
        return true;
    }

    static void completeSave(boolean saved, String message) {
        BiConsumer<Boolean, String> completion = pendingSave;
        pendingSave = null;
        if (completion != null) completion.accept(saved, message == null ? "" : message);
    }

    public static void validate(ModConfig config) {
        ModNetworking.sendValidate(ConfigLoader.toJson(config));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput,
                                     String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {
        ModNetworking.sendVanillaQuery(GSON.toJson(new RecipeQuery(query, matchIngredients,
                matchOutput, statusFilter, sourceFilter, disabledRecipeIds, page)));
    }

    public static void requestVanillaDetails(String recipeId) {
        ModNetworking.sendVanillaDetailsQuery(recipeId);
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ClientServerConfigNetworking() {}
}
