package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomRecipeEntry;
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
import net.minecraft.client.gui.screens.Screen;
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
    /** Exact GUI-scale option (including Auto = 0) from before the editor flow. */
    private static Integer guiScaleBeforeCustomRecipe;
    private static final Gson GSON = new Gson();

    @Override
    public void onInitializeClient() {
        // Used only by the clickable local-world tip; it never reaches a server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("customrecipe_solo")
                        // Do not merely reject the command after parsing: keep it out of
                        // the client dispatcher entirely while connected to multiplayer.
                        .requires(source -> Minecraft.getInstance().getSingleplayerServer() != null)
                        .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    // This client-only helper is for the clickable new-world tip.
                    // The requirement above hides it remotely; retain this guard for a
                    // disconnect occurring between parsing and execution.
                    if (client.getSingleplayerServer() == null) return 0;
                    client.execute(() -> client.gui.setScreen(ConfigScreen.fromPauseMenu(new PauseScreen(true))));
                    return 1;
                })
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateGuiScaleForScreen(client, client.gui.screen());
            if (ConfigLoader.get().preload_recipes_on_startup) VanillaRecipesScreen.preloadAtStartup(client);
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
                            .withClickEvent(new ClickEvent.RunCommand("/customrecipe_solo"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("customrecipe.chat.world_tip.hover"))));
            client.player.sendSystemMessage(Component.translatable("customrecipe.chat.world_tip", editorLink));
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip sent to chat.");
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID, (payload, context) -> {
            // A server-management payload is ignored completely unless the
            // connected player is currently an administrator. In particular,
            // no validation or local data is ever sent back by a non-admin.
            if (!ClientServerConfigNetworking.canManageServer()) return;
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendSystemMessage(Component.translatable("customrecipe.chat.invalid_server_config"));
                return;
            }
            LocalMergeResult imported = mergeLocalContent(config);
            if (imported.recipes() > 0 || imported.customItems() > 0) {
                context.player().sendSystemMessage(Component.translatable(
                        "customrecipe.chat.local_content_ready", imported.recipes(), imported.customItems()));
            }
            ClientServerConfigNetworking.validate(config);
        });
        ClientPlayNetworking.registerGlobalReceiver(ValidatedServerConfigPayload.ID, (payload, context) -> {
            if (!ClientServerConfigNetworking.canManageServer()) return;
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
            if (details == null) return;
            if (context.client().gui.screen() instanceof VanillaRecipesScreen screen) {
                screen.applyDetails(details);
            } else if (context.client().gui.screen() instanceof VanillaRecipeDetailsScreen screen) {
                screen.applyDetails(details);
            }
        });
    }

    /** Keeps the editor readable while preserving the player's exact original scale. */
    private static void updateGuiScaleForScreen(Minecraft client, Screen screen) {
        if (screen != null && screen.getClass().getPackageName().startsWith("fr.zazac1.customrecipe.client")) {
            if (!ConfigLoader.get().automatic_gui_scale) { restoreGuiScale(client); return; }
            if (guiScaleBeforeCustomRecipe == null) guiScaleBeforeCustomRecipe = client.options.guiScale().get();
            setGuiScale(client, 3);
            return;
        }
        restoreGuiScale(client);
    }

    private static void restoreGuiScale(Minecraft client) {
        if (guiScaleBeforeCustomRecipe == null) return;
        int originalScale = guiScaleBeforeCustomRecipe;
        guiScaleBeforeCustomRecipe = null;
        setGuiScale(client, originalScale);
    }

    private static void setGuiScale(Minecraft client, int scale) {
        if (client.options.guiScale().get() == scale) return;
        client.options.guiScale().set(scale);
        client.resizeGui();
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

    private record LocalMergeResult(int recipes, int customItems) {}

    /** Stages the admin's local recipes and full-stack items in the server editor. */
    private static LocalMergeResult mergeLocalContent(ModConfig serverConfig) {
        ModConfig localConfig = ConfigLoader.get();
        java.util.Map<String, String> referenceRemap = new java.util.LinkedHashMap<>();
        int addedItems = 0;
        for (CustomItemEntry localItem : localConfig.custom_items) {
            if (localItem == null) continue;
            String oldReference = CustomItemLibrary.reference(localItem.id);
            CustomItemEntry serverItem = CustomItemLibrary.find(serverConfig.custom_items, localItem.id);
            if (serverItem != null && java.util.Objects.equals(serverItem.stack_data, localItem.stack_data)) {
                referenceRemap.put(oldReference, CustomItemLibrary.reference(serverItem.id));
                continue;
            }

            String importedId = serverItem == null ? localItem.id
                    : CustomItemLibrary.uniqueId(serverConfig.custom_items, localItem.id + "_local");
            CustomItemEntry copy = copyLocalCustomItem(localItem, importedId);
            serverConfig.custom_items.add(copy);
            referenceRemap.put(oldReference, CustomItemLibrary.reference(importedId));
            addedItems++;
        }

        int addedRecipes = 0;
        for (CustomRecipeEntry localRecipe : localConfig.custom_recipes) {
            CustomRecipeEntry draft = copyLocalRecipeAsDraft(localRecipe, referenceRemap);
            boolean alreadyOnServer = serverConfig.custom_recipes.stream()
                    .anyMatch(serverRecipe -> ConfigLoader.sameRecipe(draft, serverRecipe));
            if (!alreadyOnServer) {
                serverConfig.custom_recipes.add(draft);
                addedRecipes++;
            }
        }
        return new LocalMergeResult(addedRecipes, addedItems);
    }

    private static CustomItemEntry copyLocalCustomItem(CustomItemEntry source, String id) {
        CustomItemEntry copy = new CustomItemEntry();
        copy.id = id;
        copy.name = source.name;
        copy.item_id = source.item_id;
        copy.stack_data = source.stack_data;
        return copy;
    }

    private static CustomRecipeEntry copyLocalRecipeAsDraft(
            CustomRecipeEntry source, java.util.Map<String, String> referenceRemap) {
        var copy = new CustomRecipeEntry();
        copy.id = source.id;
        copy.type = source.type;
        copy.ingredients = source.ingredients == null ? new java.util.ArrayList<>()
                : source.ingredients.stream().map(value -> remap(value, referenceRemap))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        copy.pattern = source.pattern == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(source.pattern);
        copy.keys = new java.util.LinkedHashMap<>();
        if (source.keys != null) source.keys.forEach((key, value) -> copy.keys.put(key, remap(value, referenceRemap)));
        copy.result = remap(source.result, referenceRemap);
        copy.count = source.count;
        copy.enabled = null;
        copy.server_enabled = null;
        copy.world_ids = new java.util.ArrayList<>();
        copy.world_names = new java.util.LinkedHashMap<>();
        copy.known_by_default = source.known_by_default;
        return copy;
    }

    private static String remap(String value, java.util.Map<String, String> referenceRemap) {
        return value == null ? null : referenceRemap.getOrDefault(value, value);
    }
}
