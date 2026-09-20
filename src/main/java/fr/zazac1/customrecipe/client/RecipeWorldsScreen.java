package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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

/** World assignment picker. 1.21.8 has no WorldListWidget builder, so this mirrors its visible behaviour. */
@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private static final int ROW = 56;
    private static final DateTimeFormatter LAST_PLAYED_FORMAT =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US).withZone(ZoneId.systemDefault());

    private final ConfigScreen config;
    private final CustomRecipesScreen returnScreen;
    private final int recipeIndex;
    private final List<LocalWorld> worlds = new ArrayList<>();
    private TextFieldWidget search;
    private int scroll;

    RecipeWorldsScreen(ConfigScreen config, CustomRecipesScreen returnScreen, int recipeIndex) {
        super(Text.literal("Select worlds"));
        this.config = config;
        this.returnScreen = returnScreen;
        this.recipeIndex = recipeIndex;
    }

    @Override
    protected void init() {
        if (recipeIndex < 0 || recipeIndex >= config.recipes.size()) {
            client.setScreen(returnScreen);
            return;
        }
        if (config.isServerManaged()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Add current server world"), b -> {
                config.addToCurrentWorld(recipe());
                client.setScreen(returnScreen);
            }).dimensions(width / 2 - 110, height / 2 - 12, 220, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(returnScreen))
                    .dimensions(width / 2 - 55, height / 2 + 16, 110, 20).build());
            return;
        }

        scanWorlds();
        search = new TextFieldWidget(textRenderer, width / 2 - 100, 28, 200, 20, Text.literal("Search worlds"));
        search.setPlaceholder(Text.literal("Search..."));
        search.setChangedListener(value -> scroll = 0);
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(returnScreen))
                .dimensions(width / 2 - 55, height - 28, 110, 20).build());
    }

    private void scanWorlds() {
        worlds.clear();
        try {
            Path saves = client.getLevelStorage().getSavesDirectory();
            if (saves == null || !Files.isDirectory(saves)) return;
            try (Stream<Path> entries = Files.list(saves)) {
                entries.filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> worlds.add(createWorld(path)));
            }
        } catch (IOException | RuntimeException ignored) {
            // A locked or unreadable save is simply omitted, like vanilla's unavailable entries.
        }
    }

    private LocalWorld createWorld(Path directory) {
        String id = WorldRecipeAssignments.worldId(directory);
        String folderName = directory.getFileName().toString();
        WorldDetails details = readWorldDetails(directory, folderName);
        String name = id.equals(WorldRecipeAssignments.activeWorldId())
                && !WorldRecipeAssignments.activeWorldName().isBlank()
                ? WorldRecipeAssignments.activeWorldName() : details.name;
        return new LocalWorld(id, name, details.lastPlayed, details.description, loadIcon(directory, id));
    }

    private WorldDetails readWorldDetails(Path directory, String fallbackName) {
        try {
            NbtCompound data = NbtIo.readCompressed(directory.resolve("level.dat"), NbtSizeTracker.of(104_857_600L))
                    .getCompoundOrEmpty("Data");
            String name = data.getString("LevelName", fallbackName);
            long lastPlayed = data.getLong("LastPlayed", 0L);
            int gameType = data.getInt("GameType", 0);
            String mode = switch (gameType) {
                case 1 -> Text.translatable("customrecipe.world.creative").getString();
                case 2 -> Text.translatable("customrecipe.world.adventure").getString();
                case 3 -> Text.translatable("customrecipe.world.spectator").getString();
                default -> Text.translatable("customrecipe.world.survival").getString();
            };
            String commands = data.getBoolean("allowCommands", false)
                    ? Text.translatable("customrecipe.world.commands").getString() : "";
            String version = data.getCompoundOrEmpty("Version")
                    .getString("Name", Text.translatable("customrecipe.world.unknown_version").getString());
            String played = lastPlayed > 0 ? name + " (" + LAST_PLAYED_FORMAT.format(Instant.ofEpochMilli(lastPlayed)) + ")" : name;
            return new WorldDetails(name, played,
                    mode + commands + Text.translatable("customrecipe.world.version", version).getString());
        } catch (IOException | RuntimeException ignored) {
            return new WorldDetails(fallbackName, fallbackName, Text.translatable("customrecipe.world.local").getString());
        }
    }

    private WorldIcon loadIcon(Path directory, String id) {
        Path iconPath = directory.resolve("icon.png");
        if (!Files.isRegularFile(iconPath)) return null;
        try (InputStream input = Files.newInputStream(iconPath)) {
            NativeImage image = NativeImage.read(input);
            Identifier textureId = Identifier.of(CustomRecipeMod.MOD_ID,
                    "dynamic/recipe_worlds/" + id.replaceAll("[^a-z0-9_./-]", "_"));
            WorldIcon icon = new WorldIcon(textureId, image.getWidth(), image.getHeight());
            client.getTextureManager().registerTexture(textureId,
                    new NativeImageBackedTexture(() -> "Custom Recipe world icon", image));
            return icon;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private CustomRecipeEntry recipe() { return config.recipes.get(recipeIndex); }

    private List<LocalWorld> visibleWorlds() {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? worlds : worlds.stream()
                .filter(world -> world.name.toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int rowsTop() { return 52; }
    private int rowsBottom() { return height - 32; }
    private int visibleRows() { return Math.max(1, (rowsBottom() - rowsTop()) / ROW); }
    private int shownRows() { return Math.min(visibleRows(), visibleWorlds().size()); }
    private int listX() { return width / 2 - 160; }
    private int listW() { return 320; }
    private int maxScroll() { return Math.max(0, visibleWorlds().size() - visibleRows()); }
    private boolean hasScrollBar() { return maxScroll() > 0; }
    private int scrollBarX() { return listX() + listW() + 4; }
    private int checkboxX() { return listX() + listW() - 24; }

    private void toggleCurrentRecipe(LocalWorld world) {
        boolean enabled = recipe().world_ids == null || !recipe().world_ids.contains(world.id);
        config.setWorldAssigned(recipe(), world.id, world.name, enabled);
        config.persistLocalWorldAssignments();
    }

    private void setScrollFromMouse(double mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        int trackHeight = rowsBottom() - rowsTop();
        int thumbHeight = Math.max(12, trackHeight * visibleRows() / visibleWorlds().size());
        int travel = trackHeight - thumbHeight;
        scroll = Math.max(0, Math.min(max,
                (int) Math.round((mouseY - rowsTop() - thumbHeight / 2.0) * max / travel)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            if (mouseX < listX() || mouseX >= listX() + listW() || mouseY < y || mouseY >= y + ROW) continue;
            LocalWorld world = shown.get(index);
            if (mouseX >= checkboxX() - 2) {
                toggleCurrentRecipe(world);
                return true;
            }
            client.setScreen(new WorldRecipesScreen(config, this, world.id, world.name));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY < rowsTop() || mouseY >= rowsBottom())
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        scroll = Math.max(0, Math.min(scroll - (int) verticalAmount, maxScroll()));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        List<LocalWorld> shown = visibleWorlds();
        for (int row = 0; row < shownRows(); row++) {
            int index = scroll + row;
            if (index >= shown.size()) break;
            LocalWorld world = shown.get(index);
            int y = rowsTop() + row * ROW;
            boolean hovered = mouseX >= listX() && mouseX < listX() + listW() && mouseY >= y && mouseY < y + ROW;
            context.fill(listX(), y, listX() + listW(), y + ROW - 1, hovered ? 0x77335A42 : 0x66101010);
            drawBox(context, listX(), y, listW(), ROW, hovered ? 0xFFFFFFFF : 0xFF505050);
            if (world.icon != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, world.icon.id, listX() + 4, y + 12, 0, 0,
                        32, 32, world.icon.width, world.icon.height);
            } else {
                context.drawItem(new ItemStack(Items.GRASS_BLOCK), listX() + 12, y + 20);
            }
            context.drawText(textRenderer, world.name, listX() + 42, y + 8, 0xFFFFFFFF, true);
            context.drawText(textRenderer, world.lastPlayed, listX() + 42, y + 20, 0xFFAAAAAA, false);
            context.drawText(textRenderer, world.description, listX() + 42, y + 32, 0xFFAAAAAA, false);
            boolean enabled = recipe().world_ids != null && recipe().world_ids.contains(world.id);
            CustomRecipeSprites.draw(context, CustomRecipeSprites.SLOT, checkboxX() - 1, y + 17, 20, 20);
            CustomRecipeSprites.draw(context, enabled ? CustomRecipeSprites.ACCEPT : CustomRecipeSprites.REJECT,
                    checkboxX(), y + 18, 18, 18);
        }
        if (hasScrollBar()) drawScrollBar(context);
        if (shown.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customrecipe.screen.no_worlds"), width / 2, rowsTop() + 12, 0xAAAAAA);
    }

    private void drawScrollBar(DrawContext context) {
        int trackTop = rowsTop();
        int trackHeight = rowsBottom() - trackTop;
        int thumbHeight = Math.max(12, trackHeight * visibleRows() / visibleWorlds().size());
        int travel = trackHeight - thumbHeight;
        int thumbY = trackTop + (maxScroll() == 0 ? 0 : travel * scroll / maxScroll());
        context.fill(scrollBarX(), trackTop, scrollBarX() + 6, rowsBottom(), 0x88000000);
        context.fill(scrollBarX(), thumbY, scrollBarX() + 6, thumbY + thumbHeight, 0xFFAAAAAA);
        drawBox(context, scrollBarX(), thumbY, 6, thumbHeight, 0xFFEEEEEE);
    }

    private void drawBox(DrawContext context, int x, int y, int w, int h, int color) {
        context.drawHorizontalLine(x, x + w - 1, y, color);
        context.drawHorizontalLine(x, x + w - 1, y + h - 1, color);
        context.drawVerticalLine(x, y, y + h - 1, color);
        context.drawVerticalLine(x + w - 1, y, y + h - 1, color);
    }

    @Override public boolean shouldPause() { return true; }
    @Override public void close() { client.setScreen(returnScreen); }

    private record WorldIcon(Identifier id, int width, int height) {}
    private record WorldDetails(String name, String lastPlayed, String description) {}
    private record LocalWorld(String id, String name, String lastPlayed, String description, WorldIcon icon) {}
}
