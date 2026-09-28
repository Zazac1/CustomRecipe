package fr.zazac1.customrecipe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Confirmation for the non-destructive bulk import into a selected world. */
final class ImportGlobalLibraryScreen extends Screen {
    private final ConfigScreen config;
    private final CustomRecipesScreen returnTo;
    private final int recipeCount;

    ImportGlobalLibraryScreen(ConfigScreen config, CustomRecipesScreen returnTo, int recipeCount) {
        super(Component.translatable("customrecipe.import.title"));
        this.config = config;
        this.returnTo = returnTo;
        this.recipeCount = recipeCount;
    }

    @Override
    protected void init() {
        int y = height / 2 + 22;
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.add"), b -> apply())
                .bounds(width / 2 - 82, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.cancel"), b -> minecraft.setScreen(returnTo))
                .bounds(width / 2 + 6, y, 76, 20).build());
    }

    private void apply() {
        config.addAllFromGlobalLibrary();
        minecraft.setScreen(new CustomRecipesScreen(config));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnTo);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        returnTo.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = 340;
        int panelHeight = 112;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.fill(panelX + 8, panelY + 62, panelX + panelWidth - 8, panelY + 63, 0xFF3E4749);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(font, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredString(font,
                Component.translatable("customrecipe.import.confirm", recipeCount, config.target().displayName()),
                width / 2, panelY + 31, 0xFFE0E6E3);
        context.drawCenteredString(font, Component.translatable("customrecipe.import.safe"),
                width / 2, panelY + 45, 0xFFB8C7C1);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
