package fr.zazac1.customrecipe.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chooses between the lossless mod share file and a vanilla datapack. */
@Environment(EnvType.CLIENT)
final class ExportFormatScreen extends Screen {
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        returnTo.extractRenderState(context, -10_000, -10_000, delta);
        context.nextStratum();
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 300;
        int panelHeight = 152;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - 72;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF8181B1E);
        border(context, panelX, panelY, panelWidth, panelHeight);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("customrecipe.export.choose"),
                width / 2, panelY + 26, 0xFFB8C7C1);
    }

    static void border(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int color = 0xFF6E7678;
        context.horizontalLine(x, x + width - 1, y, color);
        context.horizontalLine(x, x + width - 1, y + height - 1, color);
        context.verticalLine(x, y, y + height - 1, color);
        context.verticalLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}


