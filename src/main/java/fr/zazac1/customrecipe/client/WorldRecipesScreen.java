package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Manage every available custom recipe from the perspective of one local world. */
@Environment(EnvType.CLIENT)
final class WorldRecipesScreen extends Screen {
    private final ConfigScreen config;
    private final RecipeWorldsScreen parent;
    private final LevelSummary world;
    private String worldId;
    private int scroll;

    WorldRecipesScreen(ConfigScreen config, RecipeWorldsScreen parent, LevelSummary world) {
        super(Text.translatable("customrecipe.world.recipes_in", world == null ? Text.translatable("customrecipe.world.default_name") : world.getDisplayName()));
        this.config = config;
        this.parent = parent;
        this.world = world;
    }

    @Override
    protected void init() {
        worldId = worldId(world);
        int x = width / 2 - 170;
        int y = 40;
        List<CustomRecipeEntry> recipes = orderedRecipes();
        int visible = Math.max(1, (height - 92) / 22);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, recipes.size() - visible)));

        boolean wroteEnabledHeader = false;
        boolean wroteDisabledHeader = false;
        for (int i = scroll; i < Math.min(recipes.size(), scroll + visible); i++) {
            CustomRecipeEntry recipe = recipes.get(i);
            boolean enabled = isEnabled(recipe);
            if (enabled && !wroteEnabledHeader) {
                addDrawableChild(new net.minecraft.client.gui.widget.MultilineTextWidget(x, y,
                        Text.translatable("customrecipe.world.enabled_recipes").withColor(0x55FF55), textRenderer));
                y += 16;
                wroteEnabledHeader = true;
            }
            if (!enabled && !wroteDisabledHeader) {
                addDrawableChild(new net.minecraft.client.gui.widget.MultilineTextWidget(x, y,
                        Text.translatable("customrecipe.world.available_recipes").withColor(0xAAAAAA), textRenderer));
                y += 16;
                wroteDisabledHeader = true;
            }
            final CustomRecipeEntry target = recipe;
            final int rowY = y;
            addDrawableChild(ButtonWidget.builder(Text.literal(recipeName(recipe)), b -> {
                toggle(target);
                clearAndInit();
            }).dimensions(x + 24, rowY, 316, 18).build());
            addDrawable((ctx, mouseX, mouseY, delta) -> CustomRecipeSprites.draw(ctx,
                    isEnabled(target) ? CustomRecipeSprites.ACCEPT : CustomRecipeSprites.REJECT,
                    x, rowY, 18, 18));
            y += 22;
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.back"), b -> client.setScreen(parent))
                .dimensions(width / 2 - 55, height - 28, 110, 20).build());
    }

    private List<CustomRecipeEntry> orderedRecipes() {
        List<CustomRecipeEntry> recipes = new ArrayList<>();
        for (CustomRecipeEntry recipe : config.recipes) {
            if (recipe != null && !Boolean.TRUE.equals(recipe.corrupted)) recipes.add(recipe);
        }
        recipes.sort(Comparator.comparing(this::isEnabled).reversed().thenComparing(this::recipeName, String.CASE_INSENSITIVE_ORDER));
        return recipes;
    }

    private boolean isEnabled(CustomRecipeEntry recipe) {
        return worldId != null && recipe.world_ids != null && recipe.world_ids.contains(worldId);
    }

    private void toggle(CustomRecipeEntry recipe) {
        if (worldId == null) return;
        config.setWorldAssigned(recipe, worldId, world.getDisplayName(), !isEnabled(recipe));
        config.persistLocalWorldAssignments();
    }

    private String worldId(LevelSummary summary) {
        if (summary == null || summary.getName() == null || summary.getName().isBlank()) return null;
        try {
            Path saves = client == null ? null : client.getLevelStorage().getSavesDirectory();
            return saves == null ? null : WorldRecipeAssignments.worldId(saves.resolve(summary.getName()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String recipeName(CustomRecipeEntry recipe) {
        String id = recipe.result == null || recipe.result.isBlank() ? recipe.id : recipe.result;
        if (id == null || id.isBlank()) return "Unnamed recipe";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (!word.isEmpty()) name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return name.toString().trim();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, orderedRecipes().size() - Math.max(1, (height - 92) / 22));
        int next = Math.max(0, Math.min(max, scroll - (int) verticalAmount));
        if (next != scroll) {
            scroll = next;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public void close() { client.setScreen(parent); }
}
