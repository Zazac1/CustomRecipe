package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.Tooltip;
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
    /** Defers command-originated GUI opening until the following client tick. */
    private static int pendingSoloEditorTicks;
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
        event.getDispatcher().register(Commands.literal("customrecipe_solo")
                .requires(source -> Minecraft.getInstance().getSingleplayerServer() != null)
                .executes(context -> {
            Minecraft client = Minecraft.getInstance();
            // This editor writes the local config, never a remote server config.
            if (client.getSingleplayerServer() == null) return 0;
            // Chat command processing can restore its old screen after this
            // callback. Opening on the next tick keeps the editor visible.
            pendingSoloEditorTicks = 1;
            return 1;
        }));
    }

    /** Forge-native pause-menu hook; reliably runs after the 1.20.1 menu is built. */
    private static void onPauseScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || Minecraft.getInstance().getSingleplayerServer() == null) return;
        int x = event.getScreen().width - 28;
        int y = event.getScreen().height - 28;
        event.addListener(Button.builder(fr.zazac1.customrecipe.Texts.empty(), button ->
                        Minecraft.getInstance().setScreen(ConfigScreen.fromPauseMenu(event.getScreen())))
                .tooltip(Tooltip.create(fr.zazac1.customrecipe.Texts.translatable("customrecipe.tooltip.open")))
                .bounds(x, y, 20, 20).build());
    }

    private static void onPauseScreenRender(ScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getScreen() instanceof PauseScreen) || Minecraft.getInstance().getSingleplayerServer() == null) return;
        int x = event.getScreen().width - 28;
        int y = event.getScreen().height - 28;
        new fr.zazac1.customrecipe.client.compat.GuiGraphics(event.getPoseStack()).blit(PAUSE_BUTTON_ICON, x + 2, y + 2, 0, 0, 16, 16, 16, 16);
    }

    /**
     * The editor was designed around the vanilla GUI scale of 3. Keep that scale
     * for every Custom Recipe sub-screen, then put the player's exact setting
     * back as soon as they leave the editor.
     */
    private static void onScreenOpening(net.minecraftforge.client.event.ScreenOpenEvent event) {
        updateGuiScaleForScreen(Minecraft.getInstance(), event.getScreen());
    }

    /** Re-check on tick too: some Forge configuration entry points bypass the opening event. */
    private static void updateGuiScaleForScreen(Minecraft client, Screen screen) {
        if (isCustomRecipeScreen(screen)) {
            if (!ConfigLoader.get().automatic_gui_scale) {
                restoreGuiScale(client);
                return;
            }
            if (guiScaleBeforeCustomRecipe == null) {
                guiScaleBeforeCustomRecipe = client.options.guiScale;
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
        if (client.options.guiScale == scale) return;
        client.options.guiScale = scale;
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
        if (pendingSoloEditorTicks > 0 && --pendingSoloEditorTicks == 0
                && client.getSingleplayerServer() != null) {
            client.setScreen(ConfigScreen.fromPauseMenu(new PauseScreen(true)));
        }
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
        Component link = fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.world_tip.link").setStyle(
                Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/customrecipe_solo"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.world_tip.hover"))));
        fr.zazac1.customrecipe.Texts.send(client.player, fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.world_tip", link));
        CustomRecipeMod.LOGGER.info("[Custom Recipe] Editor tip sent for world '{}'.", levelName);
    }

    private static String worldInstanceId(Path directory, long seed) {
        try { return "created-" + Files.readAttributes(directory, BasicFileAttributes.class).creationTime().toMillis(); }
        catch (Exception ignored) { return "seed-" + seed; }
    }

    record LocalMergeResult(int recipes, int customItems) {}

    /** Stages only an administrator's local recipes and full-stack items in the server editor. */
    static LocalMergeResult mergeLocalContent(ModConfig serverConfig) {
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
            serverConfig.custom_items.add(copyLocalCustomItem(localItem, importedId));
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
        CustomRecipeEntry copy = new CustomRecipeEntry();
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
    private ClientInit() {}
}
