package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.ServerConfigPayload;
import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipePagePayload;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import fr.zazac1.customrecipe.VanillaRecipeDetailsPayload;
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
        markExistingLocalRecipesAsDrafts();
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID, (payload, context) -> {
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendMessage(net.minecraft.text.Text.literal("[Custom Recipe] Invalid server config received."), false);
                return;
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

    /** Migrates pre-publication local recipes without touching dedicated-server files. */
    private static void markExistingLocalRecipesAsDrafts() {
        ModConfig localConfig = ConfigLoader.get();
        boolean changed = false;
        for (var recipe : localConfig.custom_recipes) {
            if (recipe.server_enabled == null) {
                recipe.server_enabled = Boolean.FALSE;
                changed = true;
            }
        }
        if (changed) ConfigLoader.saveAndInvalidate(localConfig);
    }
}
