package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import fr.zazac1.customrecipe.client.compat.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chooses between the lossless mod share file and a vanilla datapack. */
final class ExportFormatScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    private final ConfigScreen returnTo;

    ExportFormatScreen(ConfigScreen returnTo) {
        super(Component.translatable("customrecipe.export.title"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        int panelY = height / 2 - 72;
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.export.share"), b -> {
            minecraft.setScreen(returnTo);
            returnTo.exportShareFile();
        }).bounds(width / 2 - 130, panelY + 42, 260, 24).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.export.datapack"),
                b -> minecraft.setScreen(new DatapackExportScreen(returnTo, this)))
                .bounds(width / 2 - 130, panelY + 72, 260, 24).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                b -> minecraft.setScreen(returnTo))
                .bounds(width / 2 - 50, panelY + 112, 100, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnTo);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        returnTo.render(context, -10_000, -10_000, delta);
        context.pose().pushPose();
        context.pose().translate(0.0F, 0.0F, 400.0F);
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 300;
        int panelHeight = 152;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - 72;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF8181B1E);
        border(context, panelX, panelY, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredString(font, Component.translatable("customrecipe.export.choose"),
                width / 2, panelY + 26, 0xFFB8C7C1);
        context.pose().popPose();
    }

    static void border(GuiGraphics context, int x, int y, int width, int height) {
        int color = 0xFF6E7678;
        context.hLine(x, x + width - 1, y, color);
        context.hLine(x, x + width - 1, y + height - 1, color);
        context.vLine(x, y, y + height - 1, color);
        context.vLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}


