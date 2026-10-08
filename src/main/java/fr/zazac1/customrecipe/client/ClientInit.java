package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import fr.zazac1.customrecipe.ModNetworking;
import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static net.minecraft.commands.Commands.literal;

public final class ClientInit {
    private static String activeClientWorldId = "";
    /** Chat closes after a command completes, so the editor opens on the next tick. */
    private static boolean pendingSoloEditorOpen;
    /** Exact GUI-scale option value (including Auto = 0) before entering the editor flow. */
    private static Integer guiScaleBeforeCustomRecipe;
    private static final Gson GSON = new Gson();

    public static void initialize(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                // This extension is opened by the NeoForge/Cloth configuration
                // flow, not by the in-game pause-menu button. Keep the global
                // editor target selectable here.
                (IConfigScreenFactory) (mod, parent) -> ConfigScreen.fromConfigButton(parent));
        NeoForge.EVENT_BUS.addListener(ClientInit::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(ClientInit::onClientTick);
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                literal("customrecipe_solo")
                        .requires(source -> Minecraft.getInstance().getSingleplayerServer() != null)
                        .executes(context -> openSoloEditor())
        );
    }

    private static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (pendingSoloEditorOpen) {
                pendingSoloEditorOpen = false;
                if (client.getSingleplayerServer() != null) {
                    client.setScreen(ConfigScreen.fromPauseMenu(new PauseScreen(true)));
                }
            }
            updateGuiScaleForScreen(client, client.screen);
            if (ConfigLoader.get().preload_recipes_on_startup) {
                VanillaRecipesScreen.preloadAtStartup(client);
            }
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
            long day = client.getSingleplayerServer().overworld().getDayTime() / 24000L;
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
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/customrecipe_solo"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("customrecipe.chat.world_tip.hover"))));
            client.player.displayClientMessage(Component.translatable("customrecipe.chat.world_tip", editorLink), false);
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip sent to chat.");
    }

    public static void receiveServerConfig(String json) {
            Minecraft client = Minecraft.getInstance();
            var config = ConfigLoader.fromJson(json);
            if (config == null) {
                if (client.player != null) client.player.displayClientMessage(Component.translatable("customrecipe.chat.invalid_server_config"), false);
                return;
            }
            int imported = mergeLocalRecipes(config);
            if (imported > 0 && client.player != null) {
                client.player.displayClientMessage(Component.translatable(
                        "customrecipe.chat.local_recipes_ready", imported), false);
            }
            ClientServerConfigNetworking.validate(config);
    }

    public static void receiveValidatedServerConfig(String json) {
            Minecraft client = Minecraft.getInstance();
            var config = ConfigLoader.fromJson(json);
            if (config == null) {
                if (client.player != null) client.player.displayClientMessage(Component.translatable("customrecipe.chat.invalid_server_validation"), false);
                return;
            }
            // `/customrecipe` is an authoritative server command. Its payload
            // carries the active save ID, so it must not fall back to the
            // client's Mod Menu default target (often Global Library).
            client.setScreen(ConfigScreen.fromServerCommand(client.screen, config,
                    ClientServerConfigNetworking::save));
    }

    public static void receiveVanillaRecipePage(String json) {
            VanillaRecipePage page = GSON.fromJson(json, VanillaRecipePage.class);
            if (page != null && Minecraft.getInstance().screen instanceof VanillaRecipesScreen screen) {
                screen.applyResult(page);
            }
    }

    public static void receiveVanillaRecipeDetails(String json) {
            VanillaRecipeDetails details = GSON.fromJson(json, VanillaRecipeDetails.class);
            if (details != null && Minecraft.getInstance().screen instanceof VanillaRecipesScreen screen) {
                screen.applyDetails(details);
            } else if (details != null && Minecraft.getInstance().screen instanceof VanillaRecipeDetailsScreen screen) {
                screen.applyDetails(details);
            }
    }

    private static int openSoloEditor() {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() == null) return 0;
        pendingSoloEditorOpen = true;
        return 1;
    }

    /** Applies scale 3 only while a Custom Recipe screen is open and restores the saved value on exit. */
    private static void updateGuiScaleForScreen(Minecraft client, Screen screen) {
        boolean editorScreen = screen != null && screen.getClass().getPackageName().startsWith("fr.zazac1.customrecipe.client");
        if (!editorScreen) {
            restoreGuiScale(client);
            return;
        }
        if (!ConfigLoader.get().automatic_gui_scale) {
            restoreGuiScale(client);
            return;
        }
        if (guiScaleBeforeCustomRecipe == null) guiScaleBeforeCustomRecipe = client.options.guiScale().get();
        setGuiScale(client, 3);
    }

    private static void restoreGuiScale(Minecraft client) {
        if (guiScaleBeforeCustomRecipe == null) return;
        int scale = guiScaleBeforeCustomRecipe;
        guiScaleBeforeCustomRecipe = null;
        setGuiScale(client, scale);
    }

    private static void setGuiScale(Minecraft client, int scale) {
        if (client.options.guiScale().get() == scale) return;
        client.options.guiScale().set(scale);
        client.resizeDisplay();
    }

    /**
     * An integrated server can report its save root as ".". Resolve the level
     * directory through the client save list so two different solo worlds never
     * share the same first-entry message state.
     */
    /** Stable save-folder ID shared by the local-world picker and pause-menu editor. */
    static String currentLocalWorldId(Minecraft client) {
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

    /**
     * Stages local recipes in the server editor without writing to the server
     * until the OP explicitly presses Save.  Schema 2 stores reusable recipes
     * in global_library, not in the retained legacy custom_recipes field.
     */
    private static int mergeLocalRecipes(fr.zazac1.customrecipe.ModConfig serverConfig) {
        ModConfig localConfig = ConfigLoader.get();

        // Keep the server's existing library and add the client's reusable
        // templates to it.  The user can then use "Add from library" to copy
        // only the wanted recipes into this server's active world.
        int added = mergeDraftRecipes(localConfig.global_library.custom_recipes,
                serverConfig.global_library.custom_recipes);

        // Retain migration support for pre-target configurations.  These old
        // root entries are drafts for the active server world, never entries
        // in the server's global template library.
        if (localConfig.custom_recipes != null && !localConfig.custom_recipes.isEmpty()) {
            var activeTarget = serverConfig.getOrCreateWorldConfig(
                    serverConfig.editor_world_id, serverConfig.editor_world_name);
            added += mergeDraftRecipes(localConfig.custom_recipes, activeTarget.custom_recipes);
        }
        return added;
    }

    private static int mergeDraftRecipes(java.util.List<fr.zazac1.customrecipe.CustomRecipeEntry> source,
                                         java.util.List<fr.zazac1.customrecipe.CustomRecipeEntry> destination) {
        if (source == null || destination == null) return 0;
        int added = 0;
        for (var localRecipe : source) {
            if (localRecipe == null || destination.stream()
                    .anyMatch(serverRecipe -> ConfigLoader.sameRecipe(localRecipe, serverRecipe))) continue;
            destination.add(copyLocalRecipeAsDraft(localRecipe));
            added++;
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
        copy.world_ids = null;
        copy.world_names = null;
        copy.known_by_default = source.known_by_default;
        return copy;
    }

    /** Confirms a completed server save without assuming that a screen is still open. */
    public static void receiveSaveResult(String translationKey) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && translationKey != null && !translationKey.isBlank()) {
            client.player.displayClientMessage(Component.translatable(translationKey), false);
        }
    }
}
