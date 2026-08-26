package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
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
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID, (payload, context) -> {
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendMessage(net.minecraft.text.Text.literal("[Custom Recipe] Invalid server config received."), false);
                return;
            }
            int imported = mergeLocalRecipes(config);
            if (imported > 0) {
                context.player().sendMessage(net.minecraft.text.Text.literal(
                        "[Custom Recipe] " + imported + " local recipe(s) ready to add to the server."), false);
            }
            context.client().setScreen(new ConfigScreen(context.client().currentScreen, config,
                    "Server Recipes (OP)", true, ClientServerConfigNetworking::save));
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipePagePayload.ID, (payload, context) -> {
            VanillaRecipePage page = GSON.fromJson(payload.json(), VanillaRecipePage.class);
            if (page != null && context.client().currentScreen instanceof VanillaRecipesScreen screen) {
                screen.applyResult(page);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsPayload.ID, (payload, context) -> {
            VanillaRecipeDetails details = GSON.fromJson(payload.json(), VanillaRecipeDetails.class);
            if (details != null && context.client().currentScreen instanceof VanillaRecipeDetailsScreen screen) {
                screen.applyDetails(details);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!shown && screen instanceof TitleScreen && !ConfigLoader.get().seen_welcome) {
                shown = true;
                client.execute(() -> client.setScreen(new WelcomeScreen(screen)));
            }
        });
    }

    /** Stages local ModMenu recipes in the server editor without duplicating existing ones. */
    private static int mergeLocalRecipes(fr.isaac.customrecipe.ModConfig serverConfig) {
        int added = 0;
        for (var localRecipe : ConfigLoader.get().custom_recipes) {
            boolean alreadyOnServer = serverConfig.custom_recipes.stream()
                    .anyMatch(serverRecipe -> ConfigLoader.sameRecipe(localRecipe, serverRecipe));
            if (!alreadyOnServer) {
                serverConfig.custom_recipes.add(localRecipe);
                added++;
            }
        }
        return added;
    }
}
