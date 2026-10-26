package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeTarget;
import net.minecraft.util.text.TextFormatting;
import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/** Persistent client preferences for the Custom Recipe editor. */
final class ModSettingsScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    private final ConfigScreen parent;
    private final ModConfig config;

    ModSettingsScreen(ConfigScreen parent) {
        super(fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.title"));
        this.parent = parent;
        this.config = ConfigLoader.get();
    }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = height / 2 - 54;
        addRenderableWidget(Button.builder(autoScaleLabel(), button -> {
            config.automatic_gui_scale = !config.automatic_gui_scale;
            save();
            button.setMessage(autoScaleLabel());
        }).tooltip(Tooltip.create(fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.auto_scale.tooltip")))
                .bounds(x, y, 276, 20).build());
        addResetButton(x + 280, y, () -> {
            config.automatic_gui_scale = true;
            save();
        });

        addRenderableWidget(Button.builder(directoryLabel(), button -> minecraft.setScreen(
                RecipeTargetSelectScreen.forSettings(this, target -> {
                    setDefaultTarget(target);
                    return new ModSettingsScreen(parent);
                }))).tooltip(Tooltip.create(fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.open_target.tooltip")))
                .bounds(x, y + 28, 276, 20).build());
        addResetButton(x + 280, y + 28, () -> {
            config.default_editor_target_id = "global";
            config.default_editor_target_name = "Global Library";
            save();
        });

        addRenderableWidget(Button.builder(preloadLabel(), button -> {
            config.preload_recipes_on_startup = !config.preload_recipes_on_startup;
            save();
            button.setMessage(preloadLabel());
        }).tooltip(Tooltip.create(fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.preload.tooltip")))
                .bounds(x, y + 56, 276, 20).build());
        addResetButton(x + 280, y + 56, () -> {
            config.preload_recipes_on_startup = true;
            save();
        });

        String saveLabel = fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.save").getString();
        int saveY = y + 96;
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.empty(), button -> {
            save();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, saveY, 200, 22).build());
        addLegacyRenderable((graphics, mouseX, mouseY, delta) -> {
            int iconX = width / 2 - font.width(saveLabel) / 2 - 20;
            CustomRecipeSprites.draw(graphics, CustomRecipeSprites.SAVE, iconX, saveY + 3, 16, 16);
            graphics.drawCenteredString(font, saveLabel, width / 2, saveY + 7, 0xFFFFFFFF);
        });
    }

    private ITextComponent autoScaleLabel() {
        return fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.auto_scale", yesNo(config.automatic_gui_scale));
    }

    private ITextComponent directoryLabel() {
        String name = "global".equals(config.default_editor_target_id)
                ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.home.global").getString() : config.default_editor_target_name;
        return fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.open_target", name);
    }

    private ITextComponent preloadLabel() {
        return fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.preload", yesNo(config.preload_recipes_on_startup));
    }

    private ITextComponent yesNo(boolean value) {
        return fr.zazac1.customrecipe.Texts.translatable(value ? "customrecipe.settings.yes" : "customrecipe.settings.no")
                .withStyle(value ? TextFormatting.GREEN : TextFormatting.RED);
    }

    private void addResetButton(int x, int y, Runnable reset) {
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.empty(), button -> {
            reset.run();
            minecraft.setScreen(new ModSettingsScreen(parent));
        }).tooltip(Tooltip.create(fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.reset")))
                .bounds(x, y, 20, 20).build());
        addLegacyRenderable((graphics, mouseX, mouseY, delta) ->
                CustomRecipeSprites.draw(graphics, CustomRecipeSprites.RESET, x + 2, y + 2, 16, 16));
    }

    private void setDefaultTarget(RecipeTarget target) {
        config.default_editor_target_id = target.id();
        config.default_editor_target_name = target.displayName();
        save();
    }

    private void save() {
        ConfigLoader.saveIntegrityState(config);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 86, 0xFFFFFF);
        graphics.drawCenteredString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.settings.preload_hint"),
                width / 2, height / 2 + 26, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
