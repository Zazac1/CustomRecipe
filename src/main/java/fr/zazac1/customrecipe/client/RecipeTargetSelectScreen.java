package fr.zazac1.customrecipe.client;

import org.lwjgl.glfw.GLFW;

import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.GlobalRecipeTarget;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import fr.zazac1.customrecipe.WorldRecipeTarget;
import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import net.minecraft.client.gui.screen.Screen;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.EditBox;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.function.Function;

/** Direct save-folder scan: no asynchronous vanilla world-list omissions. */
final class RecipeTargetSelectScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    // Vanilla world entries are compact: 32px thumbnail and three 12px text lines.
    private static final int ROW = 42;
    private static final DateTimeFormatter LAST_PLAYED_FORMAT =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US).withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final Function<fr.zazac1.customrecipe.RecipeTarget, Screen> selectedScreen;
    private final List<LocalWorld> worlds = new ArrayList<>();
    private EditBox search;
    private int scroll;

    RecipeTargetSelectScreen(ConfigScreen parent) {
        this(parent, parent::createTargetScreen, true);
    }

    RecipeTargetSelectScreen(ConfigScreen parent, Function<ConfigScreen, Screen> selectedScreen) {
        this(parent, target -> selectedScreen.apply(parent.createTargetScreen(target)), true);
    }

    static RecipeTargetSelectScreen forSettings(Screen parent,
                                                 Function<fr.zazac1.customrecipe.RecipeTarget, Screen> selectedScreen) {
        return new RecipeTargetSelectScreen(parent, selectedScreen, true);
    }

    private RecipeTargetSelectScreen(Screen parent, Function<fr.zazac1.customrecipe.RecipeTarget, Screen> selectedScreen,
                                     boolean settingsSelection) {
        super(fr.zazac1.customrecipe.Texts.translatable("customrecipe.screen.select_target"));
        this.parent = parent;
        this.selectedScreen = selectedScreen;
    }

    private void selectTarget(fr.zazac1.customrecipe.RecipeTarget target) {
        minecraft.setScreen(selectedScreen.apply(target));
    }

    @Override
    protected void init() {
        scanWorlds();
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.screen.global_library"),
                b -> selectTarget(GlobalRecipeTarget.INSTANCE))
                .bounds(width / 2 - 110, 26, 220, 20).build());
        search = new EditBox(font, width / 2 - 100, 52, 200, 18,
                fr.zazac1.customrecipe.Texts.translatable("customrecipe.screen.search_worlds"));
        search.setHint(fr.zazac1.customrecipe.Texts.translatable("customrecipe.screen.search"));
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.back"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 - 55, height - 28, 110, 20).build());
    }

    private void scanWorlds() {
        worlds.clear();
        try {
            Path saves = minecraft.gameDirectory.toPath().resolve("saves");
            if (saves == null || !Files.isDirectory(saves)) return;
            try (Stream<Path> entries = Files.list(saves)) {
                entries.filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> worlds.add(createWorld(path)));
            }
        } catch (IOException | RuntimeException ignored) {
            // A save can be in use; all other valid folders still remain visible.
        }
    }

    private LocalWorld createWorld(Path directory) {
        String id = WorldRecipeAssignments.worldId(directory);
        String folderName = directory.getFileName().toString();
        WorldDetails details = readWorldDetails(directory, folderName);
        String name = id.equals(WorldRecipeAssignments.activeWorldId())
                && !WorldRecipeAssignments.activeWorldName().trim().isEmpty()
                ? WorldRecipeAssignments.activeWorldName() : details.name;
        return new LocalWorld(id, name, details.lastPlayed, details.description, loadIcon(directory, id));
    }

    /** Reads only the save metadata, never opens or locks a world. */
    private WorldDetails readWorldDetails(Path directory, String fallbackName) {
        try {
            CompoundNBT data = CompressedStreamTools.readCompressed(directory.resolve("level.dat").toFile())
                    .getCompound("Data");
            String name = data.contains("LevelName") ? data.getString("LevelName") : fallbackName;
            long lastPlayed = data.contains("LastPlayed") ? data.getLong("LastPlayed") : 0L;
            int gameType = data.contains("GameType") ? data.getInt("GameType") : 0;
            String mode = switch (gameType) {
                case 1 -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.creative").getString();
                case 2 -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.adventure").getString();
                case 3 -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.spectator").getString();
                default -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.survival").getString();
            };
            String commands = data.contains("allowCommands") && data.getBoolean("allowCommands") ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.commands").getString() : "";
            CompoundNBT versionData = data.contains("Version") ? data.getCompound("Version") : new CompoundNBT();
            String version = versionData.contains("Name") ? versionData.getString("Name") : fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.unknown_version").getString();
            String played = lastPlayed > 0 ? name + " (" + LAST_PLAYED_FORMAT.format(Instant.ofEpochMilli(lastPlayed)) + ")" : name;
            return new WorldDetails(name, played, mode + commands + fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.version", version).getString());
        } catch (IOException | RuntimeException ignored) {
            return new WorldDetails(fallbackName, fallbackName, fr.zazac1.customrecipe.Texts.translatable("customrecipe.world.local").getString());
        }
    }

    private WorldIcon loadIcon(Path directory, String id) {
        Path iconPath = directory.resolve("icon.png");
        if (!Files.isRegularFile(iconPath)) return null;
        try (InputStream input = Files.newInputStream(iconPath)) {
            NativeImage image = NativeImage.read(input);
            ResourceLocation textureId = new ResourceLocation(CustomRecipeMod.MOD_ID,
                    "dynamic/target_worlds/" + id.replaceAll("[^a-z0-9_./-]", "_"));
            WorldIcon icon = new WorldIcon(textureId, image.getWidth(), image.getHeight());
            minecraft.getTextureManager().register(textureId,
                    new DynamicTexture(image));
            return icon;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private List<LocalWorld> visibleWorlds() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? worlds : worlds.stream()
                .filter(world -> world.name.toLowerCase(Locale.ROOT).contains(query)).collect(java.util.stream.Collectors.toList());
    }

    private int rowsAreaTop() { return 78; }
    private int rowsBottom() { return height - 36; }
    private int visibleRows() { return Math.max(1, (rowsBottom() - rowsAreaTop()) / ROW); }
    private int shownRows() { return Math.min(visibleRows(), visibleWorlds().size()); }
    private int rowsTop() { return rowsAreaTop(); }
    private int listX() { return width / 2 - 160; }
    private int listW() { return 320; }
    private int maxScroll() { return Math.max(0, visibleWorlds().size() - visibleRows()); }
    private boolean hasScrollBar() { return maxScroll() > 0; }
    private int scrollBarX() { return listX() + listW() + 4; }

    private void setScrollFromMouse(double mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        int trackHeight = rowsBottom() - rowsTop();
        int thumbHeight = Math.max(12, trackHeight * visibleRows() / visibleWorlds().size());
        int travel = trackHeight - thumbHeight;
        scroll = Math.max(0, Math.min(max, (int) Math.round((mouseY - rowsTop() - thumbHeight / 2.0) * max / travel)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (hasScrollBar() && mouseX >= scrollBarX() - 2 && mouseX <= scrollBarX() + 8
                && mouseY >= rowsTop() && mouseY < rowsBottom()) {
            setScrollFromMouse(mouseY);
            return true;
        }
        List<LocalWorld> shown = visibleWorlds();
        for (int row = 0; row < shownRows(); row++) {
            int index = scroll + row;
            if (index >= shown.size()) break;
            int y = rowsTop() + row * ROW;
            if (mouseX >= listX() && mouseX < listX() + listW()
                    && mouseY >= y && mouseY < y + ROW) {
                LocalWorld world = shown.get(index);
                selectTarget(new WorldRecipeTarget(world.id, world.name));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (mouseY < rowsTop() || mouseY >= rowsBottom())
            return super.mouseScrolled(mouseX, mouseY, verticalAmount);
        scroll = Math.max(0, Math.min(scroll - (int) verticalAmount, maxScroll()));
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        List<LocalWorld> shown = visibleWorlds();
        for (int row = 0; row < shownRows(); row++) {
            int index = scroll + row;
            if (index >= shown.size()) break;
            LocalWorld world = shown.get(index);
            int y = rowsTop() + row * ROW;
            boolean hovered = mouseX >= listX() && mouseX < listX() + listW()
                    && mouseY >= y && mouseY < y + ROW;
            context.fill(listX(), y, listX() + listW(), y + ROW - 1, hovered ? 0x77335A42 : 0x66101010);
            drawBox(context, listX(), y, listW(), ROW, hovered ? 0xFFFFFFFF : 0xFF505050);
            if (world.icon != null) {
                context.blit(world.icon.id, listX() + 4, y + 5, 0, 0,
                        32, 32, world.icon.width, world.icon.height);
            } else {
                context.renderItem(new ItemStack(Items.GRASS_BLOCK), listX() + 12, y + 13);
            }
            context.drawString(font, world.name, listX() + 42, y + 4, 0xFFFFFFFF, true);
            context.drawString(font, world.lastPlayed, listX() + 42, y + 16, 0xFFAAAAAA, false);
            context.drawString(font, world.description, listX() + 42, y + 28, 0xFFAAAAAA, false);
        }
        if (hasScrollBar()) drawScrollBar(context);
        if (shown.isEmpty()) context.drawCenteredString(font,
                fr.zazac1.customrecipe.Texts.translatable("customrecipe.screen.no_worlds"), width / 2, rowsTop() + 12, 0xAAAAAA);
    }

    private void drawScrollBar(GuiGraphics context) {
        int trackTop = rowsTop();
        int trackHeight = rowsBottom() - trackTop;
        int thumbHeight = Math.max(12, trackHeight * visibleRows() / visibleWorlds().size());
        int travel = trackHeight - thumbHeight;
        int thumbY = trackTop + (maxScroll() == 0 ? 0 : travel * scroll / maxScroll());
        context.fill(scrollBarX(), trackTop, scrollBarX() + 6, rowsBottom(), 0xFF5A5A5A);
        context.fill(scrollBarX(), thumbY, scrollBarX() + 6, thumbY + thumbHeight, 0xFFAAAAAA);
        drawBox(context, scrollBarX(), thumbY, 6, thumbHeight, 0xFFEEEEEE);
    }

    private void drawBox(GuiGraphics context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() { return true; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private static final class WorldIcon {
        private final ResourceLocation id;
        private final int width;
        private final int height;
        public WorldIcon(ResourceLocation id, int width, int height) { this.id=id; this.width=width; this.height=height; }
        public ResourceLocation id() { return id; }
        public int width() { return width; }
        public int height() { return height; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; WorldIcon other=(WorldIcon)obj; return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(width, other.width) && java.util.Objects.equals(height, other.height); }
        @Override public int hashCode() { return java.util.Objects.hash(id, width, height); }
}
    private static final class WorldDetails {
        private final String name;
        private final String lastPlayed;
        private final String description;
        public WorldDetails(String name, String lastPlayed, String description) { this.name=name; this.lastPlayed=lastPlayed; this.description=description; }
        public String name() { return name; }
        public String lastPlayed() { return lastPlayed; }
        public String description() { return description; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; WorldDetails other=(WorldDetails)obj; return java.util.Objects.equals(name, other.name) && java.util.Objects.equals(lastPlayed, other.lastPlayed) && java.util.Objects.equals(description, other.description); }
        @Override public int hashCode() { return java.util.Objects.hash(name, lastPlayed, description); }
}
    private static final class LocalWorld {
        private final String id;
        private final String name;
        private final String lastPlayed;
        private final String description;
        private final WorldIcon icon;
        public LocalWorld(String id, String name, String lastPlayed, String description, WorldIcon icon) { this.id=id; this.name=name; this.lastPlayed=lastPlayed; this.description=description; this.icon=icon; }
        public String id() { return id; }
        public String name() { return name; }
        public String lastPlayed() { return lastPlayed; }
        public String description() { return description; }
        public WorldIcon icon() { return icon; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; LocalWorld other=(LocalWorld)obj; return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(name, other.name) && java.util.Objects.equals(lastPlayed, other.lastPlayed) && java.util.Objects.equals(description, other.description) && java.util.Objects.equals(icon, other.icon); }
        @Override public int hashCode() { return java.util.Objects.hash(id, name, lastPlayed, description, icon); }
}
}
