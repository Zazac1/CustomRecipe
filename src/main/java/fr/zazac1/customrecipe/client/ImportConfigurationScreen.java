package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Central confirmation shown after a full backup has been read successfully. */
@Environment(EnvType.CLIENT)
final class ImportConfigurationScreen extends Screen {
    private final ConfigScreen returnTo;
    private final ModConfig imported;
    private final int recipeCount;
    private final int vanillaCount;

    ImportConfigurationScreen(ConfigScreen returnTo, ModConfig imported, int recipeCount, int vanillaCount) {
        super(Component.literal("Import backup"));
        this.returnTo = returnTo;
        this.imported = imported;
        this.recipeCount = recipeCount;
        this.vanillaCount = vanillaCount;
    }

    @Override
    protected void init() {
        int y = height / 2 + 34;
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> returnTo.confirmImportedConfiguration(imported))
                .bounds(width / 2 - 82, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.gui.setScreen(returnTo))
                .bounds(width / 2 + 6, y, 76, 20).build());
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(returnTo); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        returnTo.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 340;
        int panelHeight = 136;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        border(context, panelX, panelY, panelWidth, panelHeight);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.centeredText(font, "Custom recipes: " + recipeCount, width / 2, panelY + 38, 0xFFE0E6E3);
        context.centeredText(font, "Vanilla settings: " + vanillaCount, width / 2, panelY + 54, 0xFFE0E6E3);
        context.centeredText(font, "Save will apply the import.", width / 2, panelY + 78, 0xFFB8C7C1);
    }

    private static void border(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int color = 0xFF6E7678;
        context.horizontalLine(x, x + width - 1, y, color);
        context.horizontalLine(x, x + width - 1, y + height - 1, color);
        context.verticalLine(x, y, y + height - 1, color);
        context.verticalLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
