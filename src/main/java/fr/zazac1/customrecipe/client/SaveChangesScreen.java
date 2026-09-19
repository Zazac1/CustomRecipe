package fr.zazac1.customrecipe.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small confirmation shown only when Recipe Creator has unsaved edits. */
final class SaveChangesScreen extends Screen {
    private final Screen returnTo;
    private final Runnable save;
    private final Runnable discard;

    SaveChangesScreen(Screen returnTo, Runnable save, Runnable discard) {
        super(Component.translatable("customrecipe.save.title"));
        this.returnTo = returnTo;
        this.save = save;
        this.discard = discard;
    }

    @Override
    protected void init() {
        int y = height / 2 + 16;
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.save"), b -> save.run())
                .bounds(width / 2 - 102, y, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.discard"), b -> discard.run())
                .bounds(width / 2 - 32, y, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.cancel"), b -> minecraft.gui.setScreen(returnTo))
                .bounds(width / 2 + 38, y, 64, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Keep the editor visible: this screen behaves as a modal confirmation, not a new menu.
        returnTo.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = 250;
        int panelHeight = 92;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.horizontalLine(panelX + 8, panelX + panelWidth - 9, panelY + 52, 0xFF3E4749);
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Draw copy last so widgets or the underlying screen can never cover it.
        context.centeredText(font, Component.translatable("customrecipe.save.title"), width / 2, panelY + 12, 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("customrecipe.save.line1"), width / 2, panelY + 28, 0xFFE0E6E3);
        context.centeredText(font, Component.translatable("customrecipe.save.line2"), width / 2, panelY + 40, 0xFFB8C7C1);
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.horizontalLine(x, x + width - 1, y, color);
        context.horizontalLine(x, x + width - 1, y + height - 1, color);
        context.verticalLine(x, y, y + height - 1, color);
        context.verticalLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
