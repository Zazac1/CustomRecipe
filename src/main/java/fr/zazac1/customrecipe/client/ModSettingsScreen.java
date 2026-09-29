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

/** Persistent local preferences for the Custom Recipe editor. */
final class ModSettingsScreen extends Screen {
    private final ConfigScreen parent;
    private final ModConfig config;
    ModSettingsScreen(ConfigScreen parent) { super(Text.translatable("customrecipe.settings.title")); this.parent = parent; this.config = ConfigLoader.get(); }
    @Override protected void init() {
        int x = width / 2 - 150, y = height / 2 - 54;
        addDrawableChild(ButtonWidget.builder(autoLabel(), b -> { config.automatic_gui_scale = !config.automatic_gui_scale; save(); b.setMessage(autoLabel()); })
                .tooltip(Tooltip.of(Text.translatable("customrecipe.settings.auto_scale.tooltip"))).dimensions(x, y, 276, 20).build());
        reset(x + 280, y, () -> config.automatic_gui_scale = true);
        addDrawableChild(ButtonWidget.builder(targetLabel(), b -> client.setScreen(RecipeTargetSelectScreen.forSettings(this, target -> { setTarget(target); client.setScreen(new ModSettingsScreen(parent)); })))
                .tooltip(Tooltip.of(Text.translatable("customrecipe.settings.open_target.tooltip"))).dimensions(x, y + 28, 276, 20).build());
        reset(x + 280, y + 28, () -> { config.default_editor_target_id = "global"; config.default_editor_target_name = "Global Library"; });
        addDrawableChild(ButtonWidget.builder(preloadLabel(), b -> { config.preload_recipes_on_startup = !config.preload_recipes_on_startup; save(); b.setMessage(preloadLabel()); })
                .tooltip(Tooltip.of(Text.translatable("customrecipe.settings.preload.tooltip"))).dimensions(x, y + 56, 276, 20).build());
        reset(x + 280, y + 56, () -> config.preload_recipes_on_startup = true);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.save"), b -> { save(); client.setScreen(parent); }).dimensions(width / 2 - 100, y + 96, 200, 22).build());
    }
    private void reset(int x, int y, Runnable action) { addDrawableChild(ButtonWidget.builder(Text.empty(), b -> { action.run(); save(); client.setScreen(new ModSettingsScreen(parent)); }).tooltip(Tooltip.of(Text.translatable("customrecipe.settings.reset"))).dimensions(x,y,20,20).build()); addDrawable((c,mx,my,d) -> CustomRecipeSprites.draw(c, CustomRecipeSprites.RESET, x+2,y+2,16,16)); }
    private Text yes(boolean v) { return Text.translatable(v ? "customrecipe.settings.yes" : "customrecipe.settings.no").formatted(v ? Formatting.GREEN : Formatting.RED); }
    private Text autoLabel() { return Text.translatable("customrecipe.settings.auto_scale", yes(config.automatic_gui_scale)); }
    private Text preloadLabel() { return Text.translatable("customrecipe.settings.preload", yes(config.preload_recipes_on_startup)); }
    private Text targetLabel() { String name = "global".equals(config.default_editor_target_id) ? Text.translatable("customrecipe.home.global").getString() : config.default_editor_target_name; return Text.translatable("customrecipe.settings.open_target", name); }
    private void setTarget(RecipeTarget target) { config.default_editor_target_id = target.id(); config.default_editor_target_name = target.displayName(); save(); }
    private void save() { ConfigLoader.saveIntegrityState(config); }
    @Override public void render(DrawContext c,int mx,int my,float d) { c.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-86,0xFFFFFF); c.drawCenteredTextWithShadow(textRenderer,Text.translatable("customrecipe.settings.preload_hint"),width/2,height/2+26,0xAAAAAA); super.render(c,mx,my,d); }
    @Override public void close() { client.setScreen(parent); }
}
