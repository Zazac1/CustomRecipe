package fr.zazac1.customrecipe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
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
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.cancel"), b -> minecraft.setScreen(returnTo))
                .bounds(width / 2 + 38, y, 64, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnTo);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF000000);

        int panelWidth = 250;
        int panelHeight = 92;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.fill(panelX + 8, panelY + 52, panelX + panelWidth - 8, panelY + 53, 0xFF3E4749);
        super.render(context, mouseX, mouseY, delta);

        // Draw copy last so widgets or the underlying screen can never cover it.
        context.drawCenteredString(font, Component.translatable("customrecipe.save.title"), width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredString(font, Component.translatable("customrecipe.save.line1"), width / 2, panelY + 28, 0xFFE0E6E3);
        context.drawCenteredString(font, Component.translatable("customrecipe.save.line2"), width / 2, panelY + 40, 0xFFB8C7C1);
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
