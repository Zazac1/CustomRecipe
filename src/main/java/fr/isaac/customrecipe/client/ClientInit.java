package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.ModConfig;
import fr.isaac.customrecipe.ServerConfigPayload;
import fr.isaac.customrecipe.VanillaRecipePage;
import fr.isaac.customrecipe.VanillaRecipePagePayload;
import fr.isaac.customrecipe.VanillaRecipeDetails;
import fr.isaac.customrecipe.VanillaRecipeDetailsPayload;
import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.TitleScreen;

@Environment(EnvType.CLIENT)
public class ClientInit implements ClientModInitializer {

    private static boolean shown = false;
    private static final Gson GSON = new Gson();

    @Override
    public void onInitializeClient() {
        restoreLocalRecipeStates();
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID, (client, handler, buffer, responseSender) -> {
            String json = buffer.readString();
            client.execute(() -> {
            var config = ConfigLoader.fromJson(json);
            if (config == null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("[Custom Recipe] Invalid server config received."), false);
                return;
            }
            int imported = mergeLocalRecipes(config);
            if (imported > 0) {
                client.player.sendMessage(net.minecraft.text.Text.literal(
                        "[Custom Recipe] " + imported + " local recipe(s) ready to add to the server."), false);
            }
            client.setScreen(new ConfigScreen(client.currentScreen, config,
                    "Server Recipes (OP)", true, ClientServerConfigNetworking::save));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipePagePayload.ID, (client, handler, buffer, responseSender) -> {
            VanillaRecipePage page = GSON.fromJson(buffer.readString(), VanillaRecipePage.class);
            client.execute(() -> { if (page != null && client.currentScreen instanceof VanillaRecipesScreen screen) {
                screen.applyResult(page);
            }});
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsPayload.ID, (client, handler, buffer, responseSender) -> {
            VanillaRecipeDetails details = GSON.fromJson(buffer.readString(), VanillaRecipeDetails.class);
            client.execute(() -> { if (details != null && client.currentScreen instanceof VanillaRecipeDetailsScreen screen) {
                screen.applyDetails(details);
            }});
        });
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!shown && screen instanceof TitleScreen && !ConfigLoader.get().seen_welcome) {
                shown = true;
                client.execute(() -> client.setScreen(new WelcomeScreen(screen)));
            }
        });
    }

    /**
     * Local ModMenu recipes are active in singleplayer.  The false value is
     * reserved for a draft temporarily shown by the server editor, so older
     * client configs created by the first 1.20.1 build must be restored.
     */
    private static void restoreLocalRecipeStates() {
        ModConfig localConfig = ConfigLoader.get();
        boolean changed = false;
        for (var recipe : localConfig.custom_recipes) {
            if (Boolean.FALSE.equals(recipe.server_enabled)) {
                recipe.server_enabled = null;
                changed = true;
            }
        }
        if (changed) ConfigLoader.saveAndInvalidate(localConfig);
    }

    /** Stages local ModMenu recipes in the server editor without duplicating existing ones. */
    private static int mergeLocalRecipes(fr.isaac.customrecipe.ModConfig serverConfig) {
        int added = 0;
        for (var localRecipe : ConfigLoader.get().custom_recipes) {
            boolean alreadyOnServer = serverConfig.custom_recipes.stream()
                    .anyMatch(serverRecipe -> ConfigLoader.sameRecipe(localRecipe, serverRecipe));
            if (!alreadyOnServer) {
                serverConfig.custom_recipes.add(copyLocalRecipeAsDraft(localRecipe));
                added++;
            }
        }
        return added;
    }

    private static fr.isaac.customrecipe.CustomRecipeEntry copyLocalRecipeAsDraft(
            fr.isaac.customrecipe.CustomRecipeEntry source) {
        var copy = new fr.isaac.customrecipe.CustomRecipeEntry();
        copy.id = source.id;
        copy.type = source.type;
        copy.ingredients = new java.util.ArrayList<>(source.ingredients);
        copy.pattern = new java.util.ArrayList<>(source.pattern);
        copy.keys = new java.util.LinkedHashMap<>(source.keys);
        copy.result = source.result;
        copy.count = source.count;
        copy.enabled = source.enabled;
        copy.server_enabled = Boolean.FALSE;
        return copy;
    }
}
