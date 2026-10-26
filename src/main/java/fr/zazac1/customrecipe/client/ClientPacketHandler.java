package fr.zazac1.customrecipe.client;

import com.google.gson.Gson;
import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import fr.zazac1.customrecipe.VanillaRecipePage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-only endpoints invoked by the common channel through DistExecutor. */
public final class ClientPacketHandler {
    private static final Gson GSON = new Gson();
    public static void serverConfig(String json) {
        Minecraft client = Minecraft.getInstance();
        if (!ClientServerConfigNetworking.canManageServer()) return;
        ModConfig config = ConfigLoader.fromJson(json);
        if (config == null) { if (client.player != null) fr.zazac1.customrecipe.Texts.send(client.player, fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.invalid_server_config")); return; }
        ClientInit.LocalMergeResult imported = ClientInit.mergeLocalContent(config);
        if ((imported.recipes() > 0 || imported.customItems() > 0) && client.player != null) {
            fr.zazac1.customrecipe.Texts.send(client.player, fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.local_content_ready",
                    imported.recipes(), imported.customItems()));
        }
        ClientServerConfigNetworking.validate(config);
    }
    public static void validatedConfig(String json) {
        Minecraft client = Minecraft.getInstance();
        if (!ClientServerConfigNetworking.canManageServer()) return;
        ModConfig config = ConfigLoader.fromJson(json);
        if (config == null) { if (client.player != null) fr.zazac1.customrecipe.Texts.send(client.player, fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.invalid_server_validation")); return; }
        client.setScreen(new ConfigScreen(client.screen, config, "Server Recipes (OP)", true, ClientServerConfigNetworking::save));
    }
    public static void saveResult(boolean saved, String reason) { ClientServerConfigNetworking.completeSave(saved, reason); }
    public static void vanillaPage(String json) {
        Minecraft client = Minecraft.getInstance();
        VanillaRecipePage page = GSON.fromJson(json, VanillaRecipePage.class);
        if (page != null && client.screen instanceof VanillaRecipesScreen screen) screen.applyResult(page);
    }
    public static void vanillaDetails(String json) {
        Minecraft client = Minecraft.getInstance();
        VanillaRecipeDetails details = GSON.fromJson(json, VanillaRecipeDetails.class);
        if (details == null) return;
        if (client.screen instanceof VanillaRecipesScreen screen) screen.applyDetails(details);
        // Retain the legacy route for an already-open details screen.
        else if (client.screen instanceof VanillaRecipeDetailsScreen screen) screen.applyDetails(details);
    }
    private ClientPacketHandler() {}
}
