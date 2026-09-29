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
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Local-save world assignment picker for the 1.21.8 world-list API. */
@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private final ConfigScreen config;
    private final CustomRecipesScreen returnScreen;
    private final int recipeIndex;
    private final List<LevelSummary> worlds = new ArrayList<>();
    private TextFieldWidget search;
    private int page;

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
        search = new TextFieldWidget(textRenderer, width / 2 - 100, 28, 200, 20, Text.literal("Search worlds"));
        search.setPlaceholder(Text.literal("Search..."));
        search.setChangedListener(value -> { page = 0; rebuild(); });
        addDrawableChild(search);
        client.getLevelStorage().loadSummaries(client.getLevelStorage().getLevelList()).thenAccept(loaded ->
                client.execute(() -> { worlds.clear(); worlds.addAll(loaded); page = 0; rebuild(); }));
        rebuild();
    }

    private List<LevelSummary> filtered() {
        String query = search == null ? "" : search.getText().trim().toLowerCase();
        return query.isEmpty() ? worlds : worlds.stream().filter(level ->
                level.getDisplayName().toLowerCase().contains(query)
                        || level.getName().toLowerCase().contains(query)).toList();
    }

    private void rebuild() {
        clearChildren();
        if (search != null) addDrawableChild(search);
        List<LevelSummary> levels = filtered();
        int first = page * 7;
        for (int row = 0; row < 7 && first + row < levels.size(); row++) {
            LevelSummary level = levels.get(first + row);
            String id = worldId(level);
            boolean enabled = id != null && recipe().world_ids != null && recipe().world_ids.contains(id);
            Text label = Text.literal((enabled ? "✓ " : "○ ") + level.getDisplayName());
            addDrawableChild(ButtonWidget.builder(label, b -> { toggle(level); rebuild(); })
                    .dimensions(width / 2 - 150, 56 + row * 24, 300, 20).build());
        }
        if (first > 0) addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuild(); })
                .dimensions(width / 2 - 150, height - 54, 40, 20).build());
        if (first + 7 < levels.size()) addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuild(); })
                .dimensions(width / 2 + 110, height - 54, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(returnScreen))
                .dimensions(width / 2 - 55, height - 28, 110, 20).build());
    }

    private void toggle(LevelSummary level) {
        String id = worldId(level);
        if (id == null) return;
        CustomRecipeEntry recipe = recipe();
        boolean enabled = recipe.world_ids == null || !recipe.world_ids.contains(id);
        config.setWorldAssigned(recipe, id, level.getDisplayName(), enabled);
        config.persistLocalWorldAssignments();
    }

    private String worldId(LevelSummary level) {
        if (level == null || level.getName() == null || level.getName().isBlank()) return null;
        try {
            Path saves = client.getLevelStorage().getSavesDirectory();
            return saves == null ? null : WorldRecipeAssignments.worldId(saves.resolve(level.getName()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private CustomRecipeEntry recipe() { return config.recipes.get(recipeIndex); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return true; }
    @Override public void close() { client.setScreen(returnScreen); }
}
