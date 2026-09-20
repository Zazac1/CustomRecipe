package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Central confirmation shown after a full backup has been read successfully. */
@Environment(EnvType.CLIENT)
final class ImportConfigurationScreen extends Screen {
    private final ConfigScreen returnTo;
    private final ModConfig imported;
    private final int recipeCount;
    private final int vanillaCount;

    ImportConfigurationScreen(ConfigScreen returnTo, ModConfig imported, int recipeCount, int vanillaCount) {
        super(Text.literal("Import backup"));
        this.returnTo = returnTo;
        this.imported = imported;
        this.recipeCount = recipeCount;
        this.vanillaCount = vanillaCount;
    }

    @Override
    protected void init() {
        int y = height / 2 + 34;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b -> returnTo.confirmImportedConfiguration(imported))
                .dimensions(width / 2 - 82, y, 76, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> client.setScreen(returnTo))
                .dimensions(width / 2 + 6, y, 76, 20).build());
    }

    @Override
    public void close() { client.setScreen(returnTo); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        returnTo.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 340;
        int panelHeight = 136;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        border(context, panelX, panelY, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Custom recipes: " + recipeCount, width / 2, panelY + 38, 0xFFE0E6E3);
        context.drawCenteredTextWithShadow(textRenderer, "Vanilla settings: " + vanillaCount, width / 2, panelY + 54, 0xFFE0E6E3);
        context.drawCenteredTextWithShadow(textRenderer, "Save will apply the import.", width / 2, panelY + 78, 0xFFB8C7C1);
    }

    private static void border(DrawContext context, int x, int y, int width, int height) {
        int color = 0xFF6E7678;
        context.drawHorizontalLine(x, x + width - 1, y, color);
        context.drawHorizontalLine(x, x + width - 1, y + height - 1, color);
        context.drawVerticalLine(x, y, y + height - 1, color);
        context.drawVerticalLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean shouldPause() { return true; }
}
