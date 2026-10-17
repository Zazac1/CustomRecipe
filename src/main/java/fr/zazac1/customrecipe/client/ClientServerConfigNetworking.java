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
import net.minecraft.client.Minecraft;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/** Client-only sender for the OP server editor. */
@Environment(EnvType.CLIENT)
public final class ClientServerConfigNetworking {
    private static final Gson GSON = new Gson();

    /** Never uploads server-management data from a non-administrator client. */
    public static boolean canManageServer() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return false;
        if (client.getSingleplayerServer() != null) return true;
        return client.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void save(ModConfig config) {
        if (!canManageServer() || !ClientPlayNetworking.canSend(SaveServerConfigPayload.ID)) return;
        ClientPlayNetworking.send(new SaveServerConfigPayload(ConfigLoader.toJson(config)));
    }

    /** Requests a server-only integrity and conflict check without saving anything. */
    public static void validate(ModConfig config) {
        if (!canManageServer() || !ClientPlayNetworking.canSend(ValidateServerConfigPayload.ID)) return;
        ClientPlayNetworking.send(new ValidateServerConfigPayload(ConfigLoader.toJson(config)));
    }

    public static void searchVanilla(String query, boolean matchIngredients, boolean matchOutput,
                                     String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {
        if (!canManageServer() || !ClientPlayNetworking.canSend(VanillaRecipeQueryPayload.ID)) return;
        ClientPlayNetworking.send(new VanillaRecipeQueryPayload(
                GSON.toJson(new RecipeQuery(query, matchIngredients, matchOutput,
                        statusFilter, sourceFilter, disabledRecipeIds, page))));
    }

    public static void requestVanillaDetails(String recipeId) {
        if (!canManageServer() || !ClientPlayNetworking.canSend(VanillaRecipeDetailsQueryPayload.ID)) return;
        ClientPlayNetworking.send(new VanillaRecipeDetailsQueryPayload(recipeId));
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ClientServerConfigNetworking() {}
}
