package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Central confirmation shown after a full backup has been read successfully. */
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
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(returnTo))
                .bounds(width / 2 + 6, y, 76, 20).build());
    }

    @Override
    public void onClose() { minecraft.setScreen(returnTo); }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // The parent is rendered explicitly below. Running Screen's background
        // pass again would blur that already-rendered menu and its text.
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Keep the imported configuration's menu visible as the contextual
        // background, just as the Fabric editor does; the overlay below keeps
        // it non-interactive while the confirmation is pending.
        returnTo.render(context, mouseX, mouseY, delta);
        // ConfigScreen has deferred labels/widgets.  Draw the complete modal
        // above that batch, otherwise those labels bleed through its panel.
        context.pose().pushPose();
        context.pose().translate(0.0f, 0.0f, 200.0f);
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 340;
        int panelHeight = 136;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        border(context, panelX, panelY, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredString(font, "Custom recipes: " + recipeCount, width / 2, panelY + 38, 0xFFE0E6E3);
        context.drawCenteredString(font, "Vanilla settings: " + vanillaCount, width / 2, panelY + 54, 0xFFE0E6E3);
        context.drawCenteredString(font, "Save will apply the import.", width / 2, panelY + 78, 0xFFB8C7C1);
        context.pose().popPose();
    }

    private static void border(GuiGraphics context, int x, int y, int width, int height) {
        int color = 0xFF6E7678;
        context.hLine(x, x + width - 1, y, color);
        context.hLine(x, x + width - 1, y + height - 1, color);
        context.vLine(x, y, y + height - 1, color);
        context.vLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
