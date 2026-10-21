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
import fr.zazac1.customrecipe.ServerConfigSaveResultPayload;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class ClientInit implements ClientModInitializer {
    private static String activeClientWorldId = "";
    /** The exact option value (including Auto = 0) from before the editor flow. */
    private static Integer guiScaleBeforeCustomRecipe;
    private static final Gson GSON = new Gson();
    /** Opening an editor from a command must happen after the command/chat screen has finished closing. */
    private static Runnable deferredEditorOpen;
    private static int deferredEditorOpenTicks;

    @Override
    public void onInitializeClient() {
        // Used only by the clickable local-world tip; it never reaches a server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("customrecipe_solo")
                        .requires(source -> MinecraftClient.getInstance().getServer() != null)
                        .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    // This client-only helper is for the clickable new-world tip.
                    // Never expose the local editor while connected to a remote server.
                    if (client.getServer() == null) return 0;
                    deferEditorOpen(() -> {
                        if (client.getServer() != null) {
                            client.setScreen(ConfigScreen.fromPauseMenu(new GameMenuScreen(true)));
                        }
                    });
                    return 1;
                })
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            openDeferredEditorIfReady();
            updateGuiScaleForScreen(client, client.currentScreen);
            if (ConfigLoader.get().preload_recipes_on_startup) VanillaRecipesScreen.preloadAtStartup(client);
            if (client.getServer() == null) {
                activeClientWorldId = "";
                return;
            }
            if (client.player == null) return;
            String worldId = currentLocalWorldId(client);
            if (worldId.equals(activeClientWorldId)) return;
            activeClientWorldId = worldId;
            // Minecraft starts a freshly created save on day 0. Existing saves
            // are never candidates, even if they have no tip flag yet.
            String levelName = client.getServer().getSaveProperties().getLevelName();
            long day = client.getServer().getOverworld().getTimeOfDay() / 24000L;
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
            Text editorLink = Text.translatable("customrecipe.chat.world_tip.link")
                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withUnderline(true)
                            .withClickEvent(new ClickEvent.RunCommand("/customrecipe_solo"))
                            .withHoverEvent(new HoverEvent.ShowText(Text.translatable("customrecipe.chat.world_tip.hover"))));
            client.player.sendMessage(Text.translatable("customrecipe.chat.world_tip", editorLink), false);
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip sent to chat.");
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.ID, (payload, context) -> {
            // A server-management payload is ignored completely unless the
            // connected player is currently an administrator. In particular,
            // no validation or local data is ever sent back by a non-admin.
            if (!ClientServerConfigNetworking.canManageServer()) return;
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendMessage(net.minecraft.text.Text.translatable("customrecipe.chat.invalid_server_config"), false);
                return;
            }
            LocalMergeResult imported = mergeLocalContent(config);
            if (imported.recipes() > 0 || imported.customItems() > 0) {
                context.player().sendMessage(net.minecraft.text.Text.translatable(
                        "customrecipe.chat.local_content_ready", imported.recipes(), imported.customItems()), false);
            }
            ClientServerConfigNetworking.validate(config);
        });
        ClientPlayNetworking.registerGlobalReceiver(ValidatedServerConfigPayload.ID, (payload, context) -> {
            if (!ClientServerConfigNetworking.canManageServer()) return;
            var config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                context.player().sendMessage(net.minecraft.text.Text.translatable("customrecipe.chat.invalid_server_validation"), false);
                return;
            }
            deferEditorOpen(() -> context.client().setScreen(new ConfigScreen(context.client().currentScreen, config,
                    "Server Recipes (OP)", true, ClientServerConfigNetworking::save)));
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigSaveResultPayload.ID, (payload, context) -> {
            try {
                SaveResult result = GSON.fromJson(payload.json(), SaveResult.class);
                if (result == null) {
                    ClientServerConfigNetworking.completeSave(false, "The server returned an invalid save response.");
                } else {
                    ClientServerConfigNetworking.completeSave(result.saved(), result.message());
                }
            } catch (RuntimeException error) {
                ClientServerConfigNetworking.completeSave(false, "The server returned an invalid save response.");
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipePagePayload.ID, (payload, context) -> {
            VanillaRecipePage page = GSON.fromJson(payload.json(), VanillaRecipePage.class);
            if (page != null && context.client().currentScreen instanceof VanillaRecipesScreen screen) {
                screen.applyResult(page);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsPayload.ID, (payload, context) -> {
            VanillaRecipeDetails details = GSON.fromJson(payload.json(), VanillaRecipeDetails.class);
            if (details != null && context.client().currentScreen instanceof VanillaRecipesScreen screen) {
                screen.applyDetails(details);
            } else if (details != null && context.client().currentScreen instanceof VanillaRecipeDetailsScreen screen) {
                screen.applyDetails(details);
            }
        });
    }

    private record SaveResult(boolean saved, String code, String message) {}

    /** Defers the screen transition by one complete client tick. */
    private static void deferEditorOpen(Runnable open) {
        deferredEditorOpen = open;
        deferredEditorOpenTicks = 1;
    }

    private static void openDeferredEditorIfReady() {
        if (deferredEditorOpen == null) return;
        if (deferredEditorOpenTicks > 0) {
            deferredEditorOpenTicks--;
            return;
        }
        Runnable open = deferredEditorOpen;
        deferredEditorOpen = null;
        open.run();
    }

    /** Keeps the editor readable while preserving the player's exact original scale. */
    private static void updateGuiScaleForScreen(MinecraftClient client, Screen screen) {
        if (screen != null && screen.getClass().getPackageName().startsWith("fr.zazac1.customrecipe.client")) {
            if (!ConfigLoader.get().automatic_gui_scale) {
                restoreGuiScale(client);
                return;
            }
            if (guiScaleBeforeCustomRecipe == null) guiScaleBeforeCustomRecipe = client.options.getGuiScale().getValue();
            setGuiScale(client, 3);
            return;
        }
        restoreGuiScale(client);
    }

    private static void restoreGuiScale(MinecraftClient client) {
        if (guiScaleBeforeCustomRecipe == null) return;
        int originalScale = guiScaleBeforeCustomRecipe;
        guiScaleBeforeCustomRecipe = null;
        setGuiScale(client, originalScale);
    }

    private static void setGuiScale(MinecraftClient client, int scale) {
        if (client.options.getGuiScale().getValue() == scale) return;
        client.options.getGuiScale().setValue(scale);
        client.onResolutionChanged();
    }

    /**
     * An integrated server can report its save root as ".". Resolve the level
     * directory through the client save list so two different solo worlds never
     * share the same first-entry message state.
     */
    private static String currentLocalWorldId(MinecraftClient client) {
        return WorldRecipeAssignments.worldId(currentLocalWorldDirectory(client));
    }

    private static String currentLocalWorldInstanceId(MinecraftClient client) {
        Path worldDirectory = currentLocalWorldDirectory(client);
        try {
            long created = Files.readAttributes(worldDirectory, BasicFileAttributes.class)
                    .creationTime().toMillis();
            return "created-" + created;
        } catch (Exception ignored) {
            return "seed-" + client.getServer().getOverworld().getSeed();
        }
    }

    private static Path currentLocalWorldDirectory(MinecraftClient client) {
        String levelName = client.getServer().getSaveProperties().getLevelName();
        if (levelName != null && !levelName.isBlank()) {
            Path saves = client.getLevelStorage().getSavesDirectory();
            Path worldDirectory = saves.resolve(levelName);
            if (Files.isDirectory(worldDirectory)) return worldDirectory;
        }
        return client.getServer().getSavePath(WorldSavePath.ROOT);
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

        int addedRecipes = mergeDraftRecipes(localConfig.global_library.custom_recipes,
                serverConfig.global_library.custom_recipes, referenceRemap);
        if (localConfig.custom_recipes != null && !localConfig.custom_recipes.isEmpty()) {
            WorldRecipeConfig activeTarget = serverConfig.getOrCreateWorldConfig(
                    serverConfig.editor_world_id, serverConfig.editor_world_name);
            addedRecipes += mergeDraftRecipes(localConfig.custom_recipes, activeTarget.custom_recipes, referenceRemap);
        }
        return new LocalMergeResult(addedRecipes, addedItems);
    }

    private static int mergeDraftRecipes(java.util.List<CustomRecipeEntry> source,
                                         java.util.List<CustomRecipeEntry> destination,
                                         java.util.Map<String, String> referenceRemap) {
        if (source == null || destination == null) return 0;
        int added = 0;
        for (CustomRecipeEntry localRecipe : source) {
            if (localRecipe == null) continue;
            CustomRecipeEntry draft = copyLocalRecipeAsDraft(localRecipe, referenceRemap);
            if (destination.stream().anyMatch(serverRecipe -> ConfigLoader.sameRecipe(draft, serverRecipe))) continue;
            destination.add(draft);
            added++;
        }
        return added;
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
        copy.ingredient_counts = source.ingredient_counts == null ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(source.ingredient_counts);
        copy.pattern = source.pattern == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(source.pattern);
        copy.keys = new java.util.LinkedHashMap<>();
        if (source.keys != null) source.keys.forEach((key, value) -> copy.keys.put(key, remap(value, referenceRemap)));
        copy.key_counts = source.key_counts == null ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(source.key_counts);
        copy.result = remap(source.result, referenceRemap);
        copy.count = source.count;
        copy.enabled = null;
        copy.server_enabled = null;
        copy.world_ids = null;
        copy.world_names = null;
        copy.known_by_default = source.known_by_default;
        return copy;
    }

    private static String remap(String value, java.util.Map<String, String> referenceRemap) {
        return value == null ? null : referenceRemap.getOrDefault(value, value);
    }
}
