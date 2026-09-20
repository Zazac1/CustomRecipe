package fr.zazac1.customrecipe.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Confirmation for the non-destructive bulk import into a selected world. */
@Environment(EnvType.CLIENT)
final class ImportGlobalLibraryScreen extends Screen {
    private final ConfigScreen config;
    private final CustomRecipesScreen returnTo;
    private final int recipeCount;

    ImportGlobalLibraryScreen(ConfigScreen config, CustomRecipesScreen returnTo, int recipeCount) {
        super(Text.translatable("customrecipe.import.title"));
        this.config = config;
        this.returnTo = returnTo;
        this.recipeCount = recipeCount;
    }

    @Override
    protected void init() {
        int y = height / 2 + 22;
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.add"), b -> apply())
                .dimensions(width / 2 - 82, y, 76, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.cancel"), b -> client.setScreen(returnTo))
                .dimensions(width / 2 + 6, y, 76, 20).build());
    }

    private void apply() {
        config.addAllFromGlobalLibrary();
        client.setScreen(new CustomRecipesScreen(config));
    }

    @Override
    public void close() {
        client.setScreen(returnTo);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        returnTo.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = 340;
        int panelHeight = 112;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.drawHorizontalLine(panelX + 8, panelX + panelWidth - 9, panelY + 62, 0xFF3E4749);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customrecipe.import.confirm", recipeCount, config.target().displayName()),
                width / 2, panelY + 31, 0xFFE0E6E3);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.import.safe"),
                width / 2, panelY + 45, 0xFFB8C7C1);
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawHorizontalLine(x, x + width - 1, y, color);
        context.drawHorizontalLine(x, x + width - 1, y + height - 1, color);
        context.drawVerticalLine(x, y, y + height - 1, color);
        context.drawVerticalLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean shouldPause() { return true; }
}
