package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Path;

/** World assignment picker. Solo reuses Minecraft's own world list and metadata. */
@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private final ConfigScreen config;
    private final CustomRecipesScreen returnScreen;
    private final int recipeIndex;
    private WorldSelectionList worldList;
    private EditBox searchBox;

    RecipeWorldsScreen(ConfigScreen config, CustomRecipesScreen returnScreen, int recipeIndex) {
        super(Component.literal("Select worlds"));
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
            // A dedicated server cannot inspect a player's local saves; keep its current-world assignment flow.
            addRenderableWidget(Button.builder(Component.literal("Add current server world"), b -> {
                config.addToCurrentWorld(recipe());
                minecraft.setScreen(returnScreen);
            }).bounds(width / 2 - 110, height / 2 - 12, 220, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(returnScreen))
                    .bounds(width / 2 - 55, height / 2 + 16, 110, 20).build());
            return;
        }

        worldList = new WorldSelectionList.Builder(minecraft, this)
                .width(width)
                .height(Math.max(36, height - 84))
                .filter("")
                // Selection remains vanilla-only. Our click handling owns the recipe actions.
                .onEntrySelect(summary -> {})
                // This is a management list, not a launcher: vanilla does not render its Play overlay in this mode.
                .uploadWorld()
                .build();
        // position(width, height, x, y) — the previous order made this a zero-width list.
        worldList.updateSizeAndPosition(width, Math.max(36, height - 84), 0, 52);
        addRenderableWidget(worldList);
        addRenderableOnly((context, mouseX, mouseY, delta) -> drawRecipeStates(context));
        worldList.reloadWorldList();

        searchBox = new EditBox(font, width / 2 - 100, 28, 200, 20, Component.literal("Search worlds"));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(worldList::updateFilter);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(returnScreen))
                .bounds(width / 2 - 55, height - 28, 110, 20).build());
    }

    private void toggleCurrentRecipe(LevelSummary summary) {
        String worldId = worldId(summary);
        if (summary == null || worldId == null) return;
        CustomRecipeEntry recipe = recipe();
        boolean enabled = recipe.world_ids == null || !recipe.world_ids.contains(worldId);
        config.setWorldAssigned(recipe, worldId, summary.getLevelName(), enabled);
        config.persistLocalWorldAssignments();
    }

    /** Resolving a path never opens a level session or takes a save lock. */
    private String worldId(LevelSummary summary) {
        if (summary == null || summary.getLevelId() == null || summary.getLevelId().isBlank()) return null;
        try {
            Path saves = minecraft.getLevelSource().getBaseDir();
            return saves == null ? null : WorldRecipeAssignments.worldId(saves.resolve(summary.getLevelId()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private CustomRecipeEntry recipe() {
        return config.recipes.get(recipeIndex);
    }

    private void drawRecipeStates(GuiGraphicsExtractor context) {
        if (worldList == null) return;
        var entries = worldList.children();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!(entry instanceof WorldSelectionList.WorldListEntry worldEntry)) continue;
            int y = worldList.getRowTop(i);
            if (y < 52 || y > height - 84) continue;
            LevelSummary summary = worldEntry.getLevelSummary();
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
    public boolean mouseClicked(MouseButtonEvent click, boolean focused) {
        if (worldList != null) {
            double mouseX = click.x();
            double mouseY = click.y();
            var entries = worldList.children();
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                if (!(entry instanceof WorldSelectionList.WorldListEntry worldEntry)) continue;
                int y = worldList.getRowTop(i);
                if (mouseY < y || mouseY >= y + 56 || mouseX < worldList.getRowLeft() || mouseX >= worldList.getRowRight()) continue;
                LevelSummary summary = worldEntry.getLevelSummary();
                if (worldId(summary) == null) return true;
                if (mouseX >= checkboxX() - 2) {
                    toggleCurrentRecipe(summary);
                    return true;
                }
                minecraft.setScreen(new WorldRecipesScreen(config, this, summary));
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.centeredText(font, title, width / 2, 8, 0xFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return true; }

    @Override
    public void onClose() { minecraft.setScreen(returnScreen); }
}
