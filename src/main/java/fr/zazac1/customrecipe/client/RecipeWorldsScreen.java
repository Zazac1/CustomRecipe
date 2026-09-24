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

@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private final ConfigScreen config;
    private final CustomRecipesScreen returnScreen;
    private final int recipeIndex;
    private final List<LocalWorld> worlds = new ArrayList<>();
    private TextFieldWidget searchBox;
    private int scroll;

    RecipeWorldsScreen(ConfigScreen config, CustomRecipesScreen returnScreen, int recipeIndex) {
        super(Text.translatable("customrecipe.screen.select_target"));
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
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.add_current_world"), b -> {
                config.addToCurrentWorld(recipe());
                client.setScreen(returnScreen);
            }).dimensions(width / 2 - 110, height / 2 - 12, 220, 20).build());
            addBackButton(height / 2 + 16);
            return;
        }
        scanWorlds();
        searchBox = new TextFieldWidget(textRenderer, width / 2 - 100, 28, 200, 20,
                Text.translatable("customrecipe.screen.search_worlds"));
        searchBox.setPlaceholder(Text.translatable("customrecipe.screen.search"));
        searchBox.setChangedListener(value -> {
            scroll = 0;
            clearAndInit();
        });
        addDrawableChild(searchBox);
        List<LocalWorld> shown = visibleWorlds();
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - visible)));
        int rowY = 56;
        for (int i = scroll; i < Math.min(shown.size(), scroll + visible); i++) {
            LocalWorld world = shown.get(i);
            boolean enabled = isEnabled(world);
            addDrawableChild(ButtonWidget.builder(Text.literal(world.name), b -> toggle(world))
                    .dimensions(width / 2 - 160, rowY, 238, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable(enabled
                            ? "customrecipe.state.enabled" : "customrecipe.state.disabled"), b -> toggle(world))
                    .dimensions(width / 2 + 82, rowY, 78, 20).build());
            rowY += ROW_HEIGHT;
        }
        addBackButton(height - 28);
    }

    private void addBackButton(int y) {
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.back"), b -> client.setScreen(returnScreen))
                .dimensions(width / 2 - 55, y, 110, 20).build());
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
        }
    }

    private List<LocalWorld> visibleWorlds() {
        String query = searchBox == null ? "" : searchBox.getText().trim().toLowerCase(Locale.ROOT);
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
        clearAndInit();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, visibleWorlds().size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
        if (next != scroll) {
            scroll = next;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void close() {
        client.setScreen(returnScreen);
    }

    private record LocalWorld(String id, String name) {
    }
}
