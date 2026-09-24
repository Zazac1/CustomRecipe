package fr.zazac1.customrecipe.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Small confirmation shown only when Recipe Creator has unsaved edits. */
final class SaveChangesScreen extends Screen {
    private final Screen returnTo;
    private final Runnable save;
    private final Runnable discard;

    SaveChangesScreen(Screen returnTo, Runnable save, Runnable discard) {
        super(Text.translatable("customrecipe.save.title"));
        this.returnTo = returnTo;
        this.save = save;
        this.discard = discard;
    }

    @Override
    protected void init() {
        int y = height / 2 + 16;
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.save"), b -> save.run())
                .dimensions(width / 2 - 102, y, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.discard"), b -> discard.run())
                .dimensions(width / 2 - 32, y, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.cancel"), b -> client.setScreen(returnTo))
                .dimensions(width / 2 + 38, y, 64, 20).build());
    }

    @Override
    public void close() {
        client.setScreen(returnTo);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF000000);

        int panelWidth = 250;
        int panelHeight = 92;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.drawHorizontalLine(panelX + 8, panelX + panelWidth - 9, panelY + 52, 0xFF3E4749);
        super.render(context, mouseX, mouseY, delta);

        // Draw copy last so widgets or the underlying screen can never cover it.
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.save.title"), width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.save.line1"), width / 2, panelY + 28, 0xFFE0E6E3);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.save.line2"), width / 2, panelY + 40, 0xFFB8C7C1);
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
