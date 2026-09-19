package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import fr.zazac1.customrecipe.ServerConfigPayload;
import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipePagePayload;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import fr.zazac1.customrecipe.VanillaRecipeDetailsPayload;
import fr.zazac1.customrecipe.ValidatedServerConfigPayload;
import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

@Environment(EnvType.CLIENT)
public class ClientInit implements ClientModInitializer {

    private static String activeClientWorldId = "";
    private static final Gson GSON = new Gson();

    @Override
    public void onInitializeClient() {
        // Used only by the clickable local-world tip; it never reaches a server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("customrecipe_open_local").executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    // This client-only helper is for the clickable new-world tip.
                    // Never expose the local editor while connected to a remote server.
                    if (client.getSingleplayerServer() == null) return 0;
                    client.execute(() -> client.gui.setScreen(ConfigScreen.fromPauseMenu(new PauseScreen(true))));
                    return 1;
                })
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getSingleplayerServer() == null) {
                activeClientWorldId = "";
                return;
            }
            if (client.player == null) return;
            String worldId = currentLocalWorldId(client);
            if (worldId.equals(activeClientWorldId)) return;
            activeClientWorldId = worldId;
            // Minecraft starts a freshly created save on day 0. Existing saves
            // are never candidates, even if they have no tip flag yet.
            String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
            long day = client.getSingleplayerServer().overworld().getOverworldClockTime() / 24000L;
            String worldInstanceId = currentLocalWorldInstanceId(client);
            WorldRecipeConfig savedWorldConfig = ConfigLoader.get().findWorldConfig(worldId);
            boolean alreadyShown = savedWorldConfig != null && savedWorldConfig.shown_editor_tip
                    && worldInstanceId.equals(savedWorldConfig.editor_tip_world_instance);
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip check: world='{}', id='{}', instance='{}', day={}, alreadyShown={}",
                    levelName, worldId, worldInstanceId, day, alreadyShown);
            if (day != 0L) {
                CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip skipped: world is no longer on day 0.");
                return;
            }
            if (!WorldRecipeAssignments.markEditorTipShown(worldId, levelName, worldInstanceId)) {
                CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip skipped: this world is already marked as shown.");
                return;
            }
            Component editorLink = Component.translatable("customrecipe.chat.world_tip.link")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand("/customrecipe_open_local"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("customrecipe.chat.world_tip.hover"))));
            client.player.sendSystemMessage(Component.translatable("customrecipe.chat.world_tip", editorLink));
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip sent to chat.");
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID, (payload, context) -> {
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendSystemMessage(Component.translatable("customrecipe.chat.invalid_server_config"));
                return;
            }
            int imported = mergeLocalRecipes(config);
            if (imported > 0) {
                context.player().sendSystemMessage(Component.translatable(
                        "customrecipe.chat.local_recipes_ready", imported));
            }
            ClientServerConfigNetworking.validate(config);
        });
        ClientPlayNetworking.registerGlobalReceiver(ValidatedServerConfigPayload.ID, (payload, context) -> {
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendSystemMessage(Component.translatable("customrecipe.chat.invalid_server_validation"));
                return;
            }
            context.client().gui.setScreen(new ConfigScreen(context.client().gui.screen(), config,
                    "Server Recipes (OP)", true, ClientServerConfigNetworking::save));
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipePagePayload.ID, (payload, context) -> {
            VanillaRecipePage page = GSON.fromJson(payload.json(), VanillaRecipePage.class);
            if (page != null && context.client().gui.screen() instanceof VanillaRecipesScreen screen) {
                screen.applyResult(page);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsPayload.ID, (payload, context) -> {
            VanillaRecipeDetails details = GSON.fromJson(payload.json(), VanillaRecipeDetails.class);
            if (details != null && context.client().gui.screen() instanceof VanillaRecipeDetailsScreen screen) {
                screen.applyDetails(details);
            }
        });
    }

    /**
     * An integrated server can report its save root as ".". Resolve the level
     * directory through the client save list so two different solo worlds never
     * share the same first-entry message state.
     */
    private static String currentLocalWorldId(Minecraft client) {
        return WorldRecipeAssignments.worldId(currentLocalWorldDirectory(client));
    }

    private static String currentLocalWorldInstanceId(Minecraft client) {
        Path worldDirectory = currentLocalWorldDirectory(client);
        try {
            long created = Files.readAttributes(worldDirectory, BasicFileAttributes.class)
                    .creationTime().toMillis();
            return "created-" + created;
        } catch (Exception ignored) {
            return "seed-" + client.getSingleplayerServer().overworld().getSeed();
        }
    }

    private static Path currentLocalWorldDirectory(Minecraft client) {
        String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
        if (levelName != null && !levelName.isBlank()) {
            Path saves = client.getLevelSource().getBaseDir();
            Path worldDirectory = saves.resolve(levelName);
            if (Files.isDirectory(worldDirectory)) return worldDirectory;
        }
        return client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
    }

    /** Stages local ModMenu recipes in the server editor without sending them automatically. */
    private static int mergeLocalRecipes(fr.zazac1.customrecipe.ModConfig serverConfig) {
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

    private static fr.zazac1.customrecipe.CustomRecipeEntry copyLocalRecipeAsDraft(
            fr.zazac1.customrecipe.CustomRecipeEntry source) {
        var copy = new fr.zazac1.customrecipe.CustomRecipeEntry();
        copy.id = source.id;
        copy.type = source.type;
        copy.ingredients = new java.util.ArrayList<>(source.ingredients);
        copy.pattern = new java.util.ArrayList<>(source.pattern);
        copy.keys = new java.util.LinkedHashMap<>(source.keys);
        copy.result = source.result;
        copy.count = source.count;
        copy.enabled = null;
        copy.server_enabled = null;
        copy.world_ids = new java.util.ArrayList<>();
        copy.world_names = new java.util.LinkedHashMap<>();
        copy.known_by_default = source.known_by_default;
        return copy;
    }
}
