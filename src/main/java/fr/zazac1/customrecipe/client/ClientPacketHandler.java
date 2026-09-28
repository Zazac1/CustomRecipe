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
        ModConfig config = ConfigLoader.fromJson(json);
        if (config == null) { if (client.player != null) client.player.sendSystemMessage(Component.translatable("customrecipe.chat.invalid_server_config")); return; }
        int imported = ClientInit.mergeLocalRecipes(config);
        if (imported > 0 && client.player != null) client.player.sendSystemMessage(Component.translatable("customrecipe.chat.local_recipes_ready", imported));
        ClientServerConfigNetworking.validate(config);
    }
    public static void validatedConfig(String json) {
        Minecraft client = Minecraft.getInstance();
        ModConfig config = ConfigLoader.fromJson(json);
        if (config == null) { if (client.player != null) client.player.sendSystemMessage(Component.translatable("customrecipe.chat.invalid_server_validation")); return; }
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
        if (details != null && client.screen instanceof VanillaRecipeDetailsScreen screen) screen.applyDetails(details);
    }
    private ClientPacketHandler() {}
}
