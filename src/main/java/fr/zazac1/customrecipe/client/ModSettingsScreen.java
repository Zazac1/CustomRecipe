package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Persistent, local preferences for the Custom Recipe editor. */
final class ModSettingsScreen extends Screen {
    private final ConfigScreen parent;
    private final ModConfig config;

    ModSettingsScreen(ConfigScreen parent) {
        super(Component.translatable("customrecipe.settings.title"));
        this.parent = parent;
        this.config = ConfigLoader.get();
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = height / 2 - 54;
        addRenderableWidget(Button.builder(autoScaleLabel(), button -> {
            config.automatic_gui_scale = !config.automatic_gui_scale;
            save(); button.setMessage(autoScaleLabel());
        }).tooltip(Tooltip.create(Component.translatable("customrecipe.settings.auto_scale.tooltip")))
                .bounds(x, y, 276, 20).build());
        addResetButton(x + 280, y, () -> config.automatic_gui_scale = true);

        addRenderableWidget(Button.builder(targetLabel(), button -> minecraft.gui.setScreen(
                RecipeTargetSelectScreen.forSettings(this, target -> {
                    setDefaultTarget(target); return new ModSettingsScreen(parent);
                }))).tooltip(Tooltip.create(Component.translatable("customrecipe.settings.open_target.tooltip")))
                .bounds(x, y + 28, 276, 20).build());
        addResetButton(x + 280, y + 28, () -> {
            config.default_editor_target_id = "global";
            config.default_editor_target_name = "Global Library";
        });

        addRenderableWidget(Button.builder(preloadLabel(), button -> {
            config.preload_recipes_on_startup = !config.preload_recipes_on_startup;
            save(); button.setMessage(preloadLabel());
        }).tooltip(Tooltip.create(Component.translatable("customrecipe.settings.preload.tooltip")))
                .bounds(x, y + 56, 276, 20).build());
        addResetButton(x + 280, y + 56, () -> config.preload_recipes_on_startup = true);

        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.save"), button -> {
            save(); minecraft.gui.setScreen(parent);
        }).bounds(width / 2 - 100, y + 96, 200, 22).build());
    }

    private Component autoScaleLabel() { return Component.translatable("customrecipe.settings.auto_scale", yesNo(config.automatic_gui_scale)); }
    private Component targetLabel() {
        String name = "global".equals(config.default_editor_target_id)
                ? Component.translatable("customrecipe.home.global").getString() : config.default_editor_target_name;
        return Component.translatable("customrecipe.settings.open_target", name);
    }
    private Component preloadLabel() { return Component.translatable("customrecipe.settings.preload", yesNo(config.preload_recipes_on_startup)); }
    private Component yesNo(boolean value) { return Component.translatable(value ? "customrecipe.settings.yes" : "customrecipe.settings.no")
            .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED); }
    private void addResetButton(int x, int y, Runnable reset) {
        addRenderableWidget(Button.builder(Component.empty(), button -> {
            reset.run(); save(); minecraft.gui.setScreen(new ModSettingsScreen(parent));
        }).tooltip(Tooltip.create(Component.translatable("customrecipe.settings.reset"))).bounds(x, y, 20, 20).build());
        addRenderableOnly((ctx, mouseX, mouseY, delta) -> CustomRecipeSprites.draw(ctx, CustomRecipeSprites.RESET, x + 2, y + 2, 16, 16));
    }
    private void setDefaultTarget(RecipeTarget target) { config.default_editor_target_id = target.id(); config.default_editor_target_name = target.displayName(); save(); }
    private void save() { ConfigLoader.saveIntegrityState(config); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.centeredText(font, title, width / 2, height / 2 - 86, 0xFFFFFF);
        context.centeredText(font, Component.translatable("customrecipe.settings.preload_hint"), width / 2, height / 2 + 26, 0xAAAAAA);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
