package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/** Persistent local preferences for the Custom Recipe editor. */
final class ModSettingsScreen extends Screen {
    private final ConfigScreen parent;
    private final ModConfig config;
    ModSettingsScreen(ConfigScreen parent) { super(Component.translatable("customrecipe.settings.title")); this.parent = parent; this.config = ConfigLoader.get(); }
    @Override protected void init() {
        int x = width / 2 - 150, y = height / 2 - 54;
        addRenderableWidget(Button.builder(autoLabel(), b -> { config.automatic_gui_scale = !config.automatic_gui_scale; save(); b.setMessage(autoLabel()); })
                .tooltip(Tooltip.create(Component.translatable("customrecipe.settings.auto_scale.tooltip"))).bounds(x, y, 276, 20).build());
        reset(x + 280, y, () -> config.automatic_gui_scale = true);
        addRenderableWidget(Button.builder(targetLabel(), b -> minecraft.setScreen(RecipeTargetSelectScreen.forSettings(this, target -> { setTarget(target); minecraft.setScreen(new ModSettingsScreen(parent)); })))
                .tooltip(Tooltip.create(Component.translatable("customrecipe.settings.open_target.tooltip"))).bounds(x, y + 28, 276, 20).build());
        reset(x + 280, y + 28, () -> { config.default_editor_target_id = "global"; config.default_editor_target_name = "Global Library"; });
        addRenderableWidget(Button.builder(preloadLabel(), b -> { config.preload_recipes_on_startup = !config.preload_recipes_on_startup; save(); b.setMessage(preloadLabel()); })
                .tooltip(Tooltip.create(Component.translatable("customrecipe.settings.preload.tooltip"))).bounds(x, y + 56, 276, 20).build());
        reset(x + 280, y + 56, () -> config.preload_recipes_on_startup = true);
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.save"), b -> { save(); minecraft.setScreen(parent); }).bounds(width / 2 - 100, y + 96, 200, 22).build());
    }
    private void reset(int x, int y, Runnable action) { addRenderableWidget(Button.builder(Component.empty(), b -> { action.run(); save(); minecraft.setScreen(new ModSettingsScreen(parent)); }).tooltip(Tooltip.create(Component.translatable("customrecipe.settings.reset"))).bounds(x,y,20,20).build()); addRenderableOnly((c,mx,my,d) -> CustomRecipeSprites.draw(c, CustomRecipeSprites.RESET, x+2,y+2,16,16)); }
    private Component yes(boolean v) { return Component.translatable(v ? "customrecipe.settings.yes" : "customrecipe.settings.no").withStyle(v ? ChatFormatting.GREEN : ChatFormatting.RED); }
    private Component autoLabel() { return Component.translatable("customrecipe.settings.auto_scale", yes(config.automatic_gui_scale)); }
    private Component preloadLabel() { return Component.translatable("customrecipe.settings.preload", yes(config.preload_recipes_on_startup)); }
    private Component targetLabel() { String name = "global".equals(config.default_editor_target_id) ? Component.translatable("customrecipe.home.global").getString() : config.default_editor_target_name; return Component.translatable("customrecipe.settings.open_target", name); }
    private void setTarget(RecipeTarget target) { config.default_editor_target_id = target.id(); config.default_editor_target_name = target.displayName(); save(); }
    private void save() { ConfigLoader.saveIntegrityState(config); }
    @Override
    public void render(GuiGraphics c, int mx, int my, float d) {
        // Screen#render draws the blurred in-world background. It must happen
        // before our labels, otherwise the labels themselves are blurred.
        super.render(c, mx, my, d);
        c.drawCenteredString(font, title, width / 2, height / 2 - 86, 0xFFFFFF);
        c.drawCenteredString(font, Component.translatable("customrecipe.settings.preload_hint"),
                width / 2, height / 2 + 26, 0xAAAAAA);
    }
    public void onClose() { minecraft.setScreen(parent); }
}
