package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RecipeWorldsScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private final ConfigScreen config;
    private final CustomRecipesScreen returnScreen;
    private final int recipeIndex;
    private final List<LocalWorld> worlds = new ArrayList<>();
    private EditBox searchBox;
    private int scroll;

    RecipeWorldsScreen(ConfigScreen config, CustomRecipesScreen returnScreen, int recipeIndex) {
        super(Component.translatable("customrecipe.screen.select_target"));
        this.config = config;
        this.returnScreen = returnScreen;
        this.recipeIndex = recipeIndex;
    }

    @Override
    protected void init() {
        if (recipeIndex < 0 || recipeIndex >= config.recipes.size()) {
            minecraft.setScreen(returnScreen);
            return;
        }
        if (config.isServerManaged()) {
            addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.add_current_world"), b -> {
                config.addToCurrentWorld(recipe());
                minecraft.setScreen(returnScreen);
            }).bounds(width / 2 - 110, height / 2 - 12, 220, 20).build());
            addBackButton(height / 2 + 16);
            return;
        }
        scanWorlds();
        searchBox = new EditBox(font, width / 2 - 100, 28, 200, 20,
                Component.translatable("customrecipe.screen.search_worlds"));
        searchBox.setHint(Component.translatable("customrecipe.screen.search"));
        searchBox.setResponder(value -> {
            scroll = 0;
            rebuildWidgets();
        });
        addRenderableWidget(searchBox);
        List<LocalWorld> shown = visibleWorlds();
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - visible)));
        int rowY = 56;
        for (int i = scroll; i < Math.min(shown.size(), scroll + visible); i++) {
            LocalWorld world = shown.get(i);
            boolean enabled = isEnabled(world);
            addRenderableWidget(Button.builder(Component.literal(world.name), b -> toggle(world))
                    .bounds(width / 2 - 160, rowY, 238, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(enabled
                            ? "customrecipe.state.enabled" : "customrecipe.state.disabled"), b -> toggle(world))
                    .bounds(width / 2 + 82, rowY, 78, 20).build());
            rowY += ROW_HEIGHT;
        }
        addBackButton(height - 28);
    }

    private void addBackButton(int y) {
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.back"), b -> minecraft.setScreen(returnScreen))
                .bounds(width / 2 - 55, y, 110, 20).build());
    }

    private void scanWorlds() {
        worlds.clear();
        try {
            Path saves = minecraft.gameDirectory.toPath().resolve("saves");
            if (saves == null || !Files.isDirectory(saves)) return;
            try (var entries = Files.list(saves)) {
                entries.filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                        .map(path -> new LocalWorld(WorldRecipeAssignments.worldId(path), path.getFileName().toString()))
                        .sorted(Comparator.comparing(LocalWorld::name, String.CASE_INSENSITIVE_ORDER))
                        .forEach(worlds::add);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private List<LocalWorld> visibleWorlds() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? worlds : worlds.stream()
                .filter(world -> world.name.toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int visibleRows() {
        return Math.max(1, (height - 96) / ROW_HEIGHT);
    }

    private CustomRecipeEntry recipe() {
        return config.recipes.get(recipeIndex);
    }

    private boolean isEnabled(LocalWorld world) {
        return recipe().world_ids != null && recipe().world_ids.contains(world.id);
    }

    private void toggle(LocalWorld world) {
        config.setWorldAssigned(recipe(), world.id, world.name, !isEnabled(world));
        config.persistLocalWorldAssignments();
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, visibleWorlds().size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnScreen);
    }

    private record LocalWorld(String id, String name) {
    }
}
