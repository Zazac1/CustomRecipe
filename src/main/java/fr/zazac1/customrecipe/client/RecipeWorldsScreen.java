package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Explicit, non-launching local-world assignment picker for one custom recipe. */
@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
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
            addDrawableChild(ButtonWidget.builder(Text.literal("Toggle current server world"), b -> {
                toggle(WorldRecipeAssignments.activeWorldId(), WorldRecipeAssignments.activeWorldName());
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
            try (var entries = Files.list(saves)) {
                entries.filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                        .map(path -> new LocalWorld(WorldRecipeAssignments.worldId(path), path.getFileName().toString()))
                        .sorted(Comparator.comparing(LocalWorld::name, String.CASE_INSENSITIVE_ORDER))
                        .forEach(worlds::add);
            }
        } catch (IOException | RuntimeException ignored) {
            // A locked or unreadable save is simply omitted from this management list.
        }
    }

    private List<LocalWorld> visibleWorlds() {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return query.isBlank() ? worlds : worlds.stream()
                .filter(world -> world.name.toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int listTop() { return 56; }
    private int listBottom() { return height - 38; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT); }
    private int maxScroll() { return Math.max(0, visibleWorlds().size() - visibleRows()); }
    private CustomRecipeEntry recipe() { return config.recipes.get(recipeIndex); }

    private boolean assigned(LocalWorld world) {
        return recipe().world_ids != null && recipe().world_ids.contains(world.id);
    }

    private void toggle(String worldId, String worldName) {
        if (worldId == null || worldId.isBlank()) return;
        CustomRecipeEntry recipe = recipe();
        config.setWorldAssigned(recipe, worldId, worldName, recipe.world_ids == null || !recipe.world_ids.contains(worldId));
        config.persistLocalWorldAssignments();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!config.isServerManaged() && mouseY >= listTop() && mouseY < listBottom()) {
            int index = scroll + (int) ((mouseY - listTop()) / ROW_HEIGHT);
            List<LocalWorld> shown = visibleWorlds();
            if (index >= 0 && index < shown.size()) {
                LocalWorld world = shown.get(index);
                toggle(world.id, world.name);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!config.isServerManaged() && mouseY >= listTop() && mouseY < listBottom()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        if (!config.isServerManaged()) {
            List<LocalWorld> shown = visibleWorlds();
            for (int row = 0; row < visibleRows(); row++) {
                int index = scroll + row;
                if (index >= shown.size()) break;
                LocalWorld world = shown.get(index);
                int y = listTop() + row * ROW_HEIGHT;
                boolean active = assigned(world);
                context.fill(width / 2 - 160, y, width / 2 + 160, y + ROW_HEIGHT - 2,
                        active ? 0x80356B4A : 0x80303030);
                context.drawTextWithShadow(textRenderer, active ? "✓" : "□", width / 2 - 150, y + 9,
                        active ? 0xFF7CFF9B : 0xFFD0D0D0);
                context.drawTextWithShadow(textRenderer, world.name, width / 2 - 126, y + 9, 0xFFFFFFFF);
                context.drawTextWithShadow(textRenderer, active ? "Assigned" : "Click to assign", width / 2 + 70, y + 9,
                        active ? 0xFF7CFF9B : 0xFFB8B8B8);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return true; }
    @Override public void close() { client.setScreen(returnScreen); }

    private record LocalWorld(String id, String name) {}
}
