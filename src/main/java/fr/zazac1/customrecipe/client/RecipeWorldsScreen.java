package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Path;

/** World assignment picker. Solo reuses Minecraft's own world list and metadata. */
@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private final ConfigScreen config;
    private final CustomRecipesScreen returnScreen;
    private final int recipeIndex;
    private WorldListWidget worldList;
    private TextFieldWidget searchBox;

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
            // A dedicated server cannot inspect a player's local saves; keep its current-world assignment flow.
            addDrawableChild(ButtonWidget.builder(Text.literal("Add current server world"), b -> {
                config.addToCurrentWorld(recipe());
                client.setScreen(returnScreen);
            }).dimensions(width / 2 - 110, height / 2 - 12, 220, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(returnScreen))
                    .dimensions(width / 2 - 55, height / 2 + 16, 110, 20).build());
            return;
        }

        worldList = new WorldListWidget.Builder(client, this)
                .width(width)
                .height(Math.max(36, height - 84))
                .search("")
                // Selection remains vanilla-only. Our click handling owns the recipe actions.
                .selectionCallback(summary -> {})
                // This is a management list, not a launcher: vanilla does not render its Play overlay in this mode.
                .uploadWorld()
                .toWidget();
        // position(width, height, x, y) — the previous order made this a zero-width list.
        worldList.position(width, Math.max(36, height - 84), 0, 52);
        addDrawableChild(worldList);
        addDrawable((context, mouseX, mouseY, delta) -> drawRecipeStates(context));
        worldList.load();

        searchBox = new TextFieldWidget(textRenderer, width / 2 - 100, 28, 200, 20, Text.literal("Search worlds"));
        searchBox.setPlaceholder(Text.literal("Search...").setStyle(TextFieldWidget.SEARCH_STYLE));
        searchBox.setChangedListener(worldList::setSearch);
        addDrawableChild(searchBox);

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(returnScreen))
                .dimensions(width / 2 - 55, height - 28, 110, 20).build());
    }

    private void toggleCurrentRecipe(LevelSummary summary) {
        String worldId = worldId(summary);
        if (summary == null || worldId == null) return;
        CustomRecipeEntry recipe = recipe();
        boolean enabled = recipe.world_ids == null || !recipe.world_ids.contains(worldId);
        config.setWorldAssigned(recipe, worldId, summary.getDisplayName(), enabled);
        config.persistLocalWorldAssignments();
    }

    /** Resolving a path never opens a level session or takes a save lock. */
    private String worldId(LevelSummary summary) {
        if (summary == null || summary.getName() == null || summary.getName().isBlank()) return null;
        try {
            Path saves = client.getLevelStorage().getSavesDirectory();
            return saves == null ? null : WorldRecipeAssignments.worldId(saves.resolve(summary.getName()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private CustomRecipeEntry recipe() {
        return config.recipes.get(recipeIndex);
    }

    private void drawRecipeStates(DrawContext context) {
        if (worldList == null) return;
        var entries = worldList.children();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!(entry instanceof WorldListWidget.WorldEntry worldEntry)) continue;
            int y = worldList.getRowTop(i);
            if (y < 52 || y > height - 84) continue;
            LevelSummary summary = worldEntry.getLevel();
            String worldId = worldId(summary);
            if (worldId == null) continue;
            boolean enabled = recipe().world_ids != null && recipe().world_ids.contains(worldId);
            // Reuse the recipe editor slot backing so the state icon is not floating over the vanilla row.
            CustomRecipeSprites.draw(context, CustomRecipeSprites.SLOT, checkboxX() - 1, y + 17, 20, 20);
            CustomRecipeSprites.draw(context, enabled ? CustomRecipeSprites.ACCEPT : CustomRecipeSprites.REJECT,
                    checkboxX(), y + 18, 18, 18);
        }
    }

    private int checkboxX() {
        return worldList.getRowRight() - 24;
    }

    @Override
    public boolean mouseClicked(Click click, boolean focused) {
        if (worldList != null) {
            double mouseX = click.x();
            double mouseY = click.y();
            var entries = worldList.children();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                if (!(entry instanceof WorldListWidget.WorldEntry worldEntry)) continue;
                int y = worldList.getRowTop(i);
                if (mouseY < y || mouseY >= y + 56 || mouseX < worldList.getRowLeft() || mouseX >= worldList.getRowRight()) continue;
                LevelSummary summary = worldEntry.getLevel();
                if (worldId(summary) == null) return true;
                if (mouseX >= checkboxX() - 2) {
                    toggleCurrentRecipe(summary);
                    return true;
                }
                client.setScreen(new WorldRecipesScreen(config, this, summary));
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public void close() { client.setScreen(returnScreen); }
}
