package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Forge client lifecycle setup. Network endpoints live in {@link ClientPacketHandler}. */
public final class ClientInit {
    private static String activeClientWorldId = "";
    /** Original GUI scale saved for the complete lifetime of a Custom Recipe screen flow. */
    private static Integer guiScaleBeforeCustomRecipe;
    private static final ResourceLocation PAUSE_BUTTON_ICON = new ResourceLocation(CustomRecipeMod.MOD_ID,
            "textures/gui/pause_button.png");

    public static void initialize() {
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::registerClientCommands);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onPauseScreenInit);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onPauseScreenRender);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::onScreenOpening);
        MinecraftForge.registerConfigScreen(ClothConfigIntegration::createScreen);
    }

    /** Registers the local-only command used by the clickable new-world hint. */
    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("customrecipe_open_local").executes(context -> {
            Minecraft client = Minecraft.getInstance();
            // This editor writes the local config, never a remote server config.
            if (client.getSingleplayerServer() == null) return 0;
            client.execute(() -> client.setScreen(ConfigScreen.fromPauseMenu(new PauseScreen(true))));
            return 1;
        }));
    }

    /** Forge-native pause-menu hook; reliably runs after the 1.20.1 menu is built. */
    private static void onPauseScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || Minecraft.getInstance().getSingleplayerServer() == null) return;
        int x = event.getScreen().width - 28;
        int y = event.getScreen().height - 28;
        event.addListener(Button.builder(Component.empty(), button ->
                        Minecraft.getInstance().setScreen(ConfigScreen.fromPauseMenu(event.getScreen())))
                .tooltip(Tooltip.create(Component.translatable("customrecipe.tooltip.open")))
                .bounds(x, y, 20, 20).build());
    }

    private static void onPauseScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || Minecraft.getInstance().getSingleplayerServer() == null) return;
        int x = event.getScreen().width - 28;
        int y = event.getScreen().height - 28;
        event.getGuiGraphics().blit(PAUSE_BUTTON_ICON, x + 2, y + 2, 0, 0, 16, 16, 16, 16);
    }

    /**
     * The editor was designed around the vanilla GUI scale of 3. Keep that scale
     * for every Custom Recipe sub-screen, then put the player's exact setting
     * back as soon as they leave the editor.
     */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        updateGuiScaleForScreen(Minecraft.getInstance(), event.getNewScreen());
    }

    /** Re-check on tick too: some Forge configuration entry points bypass the opening event. */
    private static void updateGuiScaleForScreen(Minecraft client, Screen screen) {
        if (isCustomRecipeScreen(screen)) {
            if (!ConfigLoader.get().automatic_gui_scale) {
                restoreGuiScale(client);
                return;
            }
            if (guiScaleBeforeCustomRecipe == null) {
                guiScaleBeforeCustomRecipe = client.options.guiScale().get();
            }
            setGuiScale(client, 3);
            return;
        }

        restoreGuiScale(client);
    }

    private static void restoreGuiScale(Minecraft client) {
        if (guiScaleBeforeCustomRecipe != null) {
            int originalScale = guiScaleBeforeCustomRecipe;
            guiScaleBeforeCustomRecipe = null;
            setGuiScale(client, originalScale);
        }
    }

    private static void setGuiScale(Minecraft client, int scale) {
        if (client.options.guiScale().get() == scale) return;
        client.options.guiScale().set(scale);
        // OptionInstance updates the stored value. Resize explicitly so the
        // currently open editor is immediately rebuilt at its new scale.
        client.resizeDisplay();
    }

    private static boolean isCustomRecipeScreen(Screen screen) {
        return screen != null && screen.getClass().getPackageName().startsWith("fr.zazac1.customrecipe.client");
    }

    /** Same END tick used on Fabric: checks a local world once and sends its editor hint. */
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        updateGuiScaleForScreen(client, client.screen);
        if (ConfigLoader.get().preload_recipes_on_startup) VanillaRecipesScreen.preloadAtStartup(client);
        var server = client.getSingleplayerServer();
        if (server == null) {
            activeClientWorldId = "";
            return;
        }
        if (client.player == null) return;
        Path worldDirectory = server.getWorldPath(LevelResource.ROOT);
        String worldId = WorldRecipeAssignments.worldId(worldDirectory);
        if (worldId.equals(activeClientWorldId)) return;
        activeClientWorldId = worldId;
        String levelName = server.getWorldData().getLevelName();
        long day = server.overworld().getDayTime() / 24000L;
        String worldInstanceId = worldInstanceId(worldDirectory, server.overworld().getSeed());
        WorldRecipeConfig config = ConfigLoader.get().findWorldConfig(worldId);
        boolean alreadyShown = config != null && config.shown_editor_tip
                && worldInstanceId.equals(config.editor_tip_world_instance);
        if (day != 0L || alreadyShown || !WorldRecipeAssignments.markEditorTipShown(worldId, levelName, worldInstanceId)) return;
        Component link = Component.translatable("customrecipe.chat.world_tip.link").setStyle(
                Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/customrecipe_open_local"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("customrecipe.chat.world_tip.hover"))));
        client.player.sendSystemMessage(Component.translatable("customrecipe.chat.world_tip", link));
        CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip sent for world '{}'.", levelName);
    }

    private static String worldInstanceId(Path directory, long seed) {
        try { return "created-" + Files.readAttributes(directory, BasicFileAttributes.class).creationTime().toMillis(); }
        catch (Exception ignored) { return "seed-" + seed; }
    }

    /** Stages local recipes in the server editor without automatically publishing them. */
    public static int mergeLocalRecipes(ModConfig serverConfig) {
        int added = 0;
        for (CustomRecipeEntry localRecipe : ConfigLoader.get().custom_recipes) {
            if (serverConfig.custom_recipes.stream().anyMatch(serverRecipe -> ConfigLoader.sameRecipe(localRecipe, serverRecipe))) continue;
            serverConfig.custom_recipes.add(copyLocalRecipeAsDraft(localRecipe));
            added++;
        }
        return added;
    }

    private static CustomRecipeEntry copyLocalRecipeAsDraft(CustomRecipeEntry source) {
        CustomRecipeEntry copy = new CustomRecipeEntry();
        copy.id = source.id; copy.type = source.type; copy.ingredients = new java.util.ArrayList<>(source.ingredients);
        copy.pattern = new java.util.ArrayList<>(source.pattern); copy.keys = new java.util.LinkedHashMap<>(source.keys);
        copy.result = source.result; copy.count = source.count; copy.enabled = null; copy.server_enabled = null;
        copy.world_ids = new java.util.ArrayList<>(); copy.world_names = new java.util.LinkedHashMap<>();
        copy.known_by_default = source.known_by_default;
        return copy;
    }
    private ClientInit() {}
}
