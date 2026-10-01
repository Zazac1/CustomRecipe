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
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // The editor is rendered explicitly below; a second background pass
        // would blur it after its panels and labels have been drawn.
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // renderWithTooltipAndSubtitles only renders this modal's background.
        // Recreate the parent background before its widgets; otherwise the
        // confirmation darkens the previous world frame instead of the
        // editor's in-world menu texture and custom dark gradient.
        returnTo.renderBackground(context, mouseX, mouseY, delta);
        // Keep the editor visible: this screen behaves as a modal confirmation, not a new menu.
        returnTo.render(context, mouseX, mouseY, delta);
        // Parent screens use deferred drawables for their labels. Put every modal layer
        // above that batch so a home-screen caption cannot bleed through the panel.
        context.pose().pushMatrix();
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = 250;
        int panelHeight = 92;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.hLine(panelX + 8, panelX + panelWidth - 9, panelY + 52, 0xFF3E4749);
        super.render(context, mouseX, mouseY, delta);

        // Draw copy last so widgets or the underlying screen can never cover it.
        context.drawCenteredString(font, Component.translatable("customrecipe.save.title"), width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredString(font, Component.translatable("customrecipe.save.line1"), width / 2, panelY + 28, 0xFFE0E6E3);
        context.drawCenteredString(font, Component.translatable("customrecipe.save.line2"), width / 2, panelY + 40, 0xFFB8C7C1);
        context.pose().popMatrix();
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.hLine(x, x + width - 1, y, color);
        context.hLine(x, x + width - 1, y + height - 1, color);
        context.vLine(x, y, y + height - 1, color);
        context.vLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
