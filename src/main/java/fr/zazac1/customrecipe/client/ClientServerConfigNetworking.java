package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.SaveServerConfigPayload;
import fr.zazac1.customrecipe.VanillaRecipeQueryPayload;
import fr.zazac1.customrecipe.VanillaRecipeDetailsQueryPayload;
import fr.zazac1.customrecipe.ValidateServerConfigPayload;
import fr.zazac1.customrecipe.ModNetworking;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.BiConsumer;

/** Client-only sender for the OP server editor. */
public final class ClientServerConfigNetworking {
    private static final int MAX_CONFIG_CHARS = 1_000_000;
    private static final Gson GSON = new Gson();
    private static BiConsumer<Boolean, String> pendingSave;

    /** Never uploads server-management data from a non-administrator client. */
    public static boolean canManageServer() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return false;
        if (client.getSingleplayerServer() != null) return true;
        return client.player.hasPermissions(2);
    }

    public static void save(ModConfig config) {
        ModNetworking.sendSave(ConfigLoader.toJson(config));
    }

    /** Sends one save request and waits for the server's durable-write acknowledgement. */
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
        ModNetworking.sendSave(json);
        return true;
    }

    static void completeSave(boolean saved, String message) {
        BiConsumer<Boolean, String> completion = pendingSave;
        pendingSave = null;
        if (completion != null) completion.accept(saved, message == null ? "" : message);
    }

    public static void validate(ModConfig config) {
        if (!canManageServer()) return;
        ModNetworking.sendValidate(ConfigLoader.toJson(config));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput,
                                     String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {
        if (!canManageServer()) return;
        ModNetworking.sendVanillaQuery(GSON.toJson(new RecipeQuery(query, matchIngredients,
                matchOutput, statusFilter, sourceFilter, disabledRecipeIds, page)));
    }

    public static void requestVanillaDetails(String recipeId) {
        if (!canManageServer()) return;
        ModNetworking.sendVanillaDetailsQuery(recipeId);
    }

    private static final class RecipeQuery {
        private final String query;
        private final boolean matchIngredients;
        private final boolean matchOutput;
        private final String statusFilter;
        private final String sourceFilter;
        private final List<String> disabledRecipeIds;
        private final int page;
        public RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) { this.query=query; this.matchIngredients=matchIngredients; this.matchOutput=matchOutput; this.statusFilter=statusFilter; this.sourceFilter=sourceFilter; this.disabledRecipeIds=disabledRecipeIds; this.page=page; }
        public String query() { return query; }
        public boolean matchIngredients() { return matchIngredients; }
        public boolean matchOutput() { return matchOutput; }
        public String statusFilter() { return statusFilter; }
        public String sourceFilter() { return sourceFilter; }
        public List<String> disabledRecipeIds() { return disabledRecipeIds; }
        public int page() { return page; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; RecipeQuery other=(RecipeQuery)obj; return java.util.Objects.equals(query, other.query) && java.util.Objects.equals(matchIngredients, other.matchIngredients) && java.util.Objects.equals(matchOutput, other.matchOutput) && java.util.Objects.equals(statusFilter, other.statusFilter) && java.util.Objects.equals(sourceFilter, other.sourceFilter) && java.util.Objects.equals(disabledRecipeIds, other.disabledRecipeIds) && java.util.Objects.equals(page, other.page); }
        @Override public int hashCode() { return java.util.Objects.hash(query, matchIngredients, matchOutput, statusFilter, sourceFilter, disabledRecipeIds, page); }
}

    private ClientServerConfigNetworking() {}
}
