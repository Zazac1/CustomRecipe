package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeTarget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Persistent, local preferences for the Custom Recipe editor. */
final class ModSettingsScreen extends Screen {
    private final ConfigScreen parent;
    private final ModConfig config;

    ModSettingsScreen(ConfigScreen parent) {
        super(Text.translatable("customrecipe.settings.title"));
        this.parent = parent;
        this.config = ConfigLoader.get();
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = height / 2 - 54;
        addDrawableChild(ButtonWidget.builder(autoScaleLabel(), button -> {
            config.automatic_gui_scale = !config.automatic_gui_scale;
            save(); button.setMessage(autoScaleLabel());
        }).tooltip(Tooltip.of(Text.translatable("customrecipe.settings.auto_scale.tooltip")))
                .dimensions(x, y, 276, 20).build());
        addResetButton(x + 280, y, () -> config.automatic_gui_scale = true);

        addDrawableChild(ButtonWidget.builder(targetLabel(), button -> client.setScreen(
                RecipeTargetSelectScreen.forSettings(this, target -> {
                    setDefaultTarget(target); return new ModSettingsScreen(parent);
                }))).tooltip(Tooltip.of(Text.translatable("customrecipe.settings.open_target.tooltip")))
                .dimensions(x, y + 28, 276, 20).build());
        addResetButton(x + 280, y + 28, () -> {
            config.default_editor_target_id = "global";
            config.default_editor_target_name = "Global Library";
        });

        addDrawableChild(ButtonWidget.builder(preloadLabel(), button -> {
            config.preload_recipes_on_startup = !config.preload_recipes_on_startup;
            save(); button.setMessage(preloadLabel());
        }).tooltip(Tooltip.of(Text.translatable("customrecipe.settings.preload.tooltip")))
                .dimensions(x, y + 56, 276, 20).build());
        addResetButton(x + 280, y + 56, () -> config.preload_recipes_on_startup = true);

        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.save"), button -> {
            save(); client.setScreen(parent);
        }).dimensions(width / 2 - 100, y + 96, 200, 22).build());
    }

    private Text autoScaleLabel() { return Text.translatable("customrecipe.settings.auto_scale", yesNo(config.automatic_gui_scale)); }
    private Text targetLabel() {
        String name = "global".equals(config.default_editor_target_id)
                ? Text.translatable("customrecipe.home.global").getString() : config.default_editor_target_name;
        return Text.translatable("customrecipe.settings.open_target", name);
    }
    private Text preloadLabel() { return Text.translatable("customrecipe.settings.preload", yesNo(config.preload_recipes_on_startup)); }
    private Text yesNo(boolean value) { return Text.translatable(value ? "customrecipe.settings.yes" : "customrecipe.settings.no")
            .formatted(value ? Formatting.GREEN : Formatting.RED); }
    private void addResetButton(int x, int y, Runnable reset) {
        addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            reset.run(); save(); client.setScreen(new ModSettingsScreen(parent));
        }).tooltip(Tooltip.of(Text.translatable("customrecipe.settings.reset"))).dimensions(x, y, 20, 20).build());
        addDrawable((ctx, mouseX, mouseY, delta) -> CustomRecipeSprites.draw(ctx, CustomRecipeSprites.RESET, x + 2, y + 2, 16, 16));
    }
    private void setDefaultTarget(RecipeTarget target) { config.default_editor_target_id = target.id(); config.default_editor_target_name = target.displayName(); save(); }
    private void save() { ConfigLoader.saveIntegrityState(config); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 86, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.settings.preload_hint"), width / 2, height / 2 + 26, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void close() { client.setScreen(parent); }
}
