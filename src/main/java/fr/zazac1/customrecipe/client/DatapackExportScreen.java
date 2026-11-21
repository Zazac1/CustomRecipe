package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Datapack name/version customisation and compatibility preview. */
@Environment(EnvType.CLIENT)
final class DatapackExportScreen extends Screen {
    private final ConfigScreen config;
    private final Screen returnTo;
    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;
    private TextFieldWidget versionField;
    private ButtonWidget exportButton;
    private DatapackExporter.Preview preview = new DatapackExporter.Preview(0, 0, java.util.List.of());
    private String errorMessage = "";

    DatapackExportScreen(ConfigScreen config, Screen returnTo) {
        super(Text.translatable("customrecipe.export.datapack.title"));
        this.config = config;
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        int panelX = width / 2 - 220;
        int panelY = height / 2 - 132;
        int fieldX = panelX + 132;
        int fieldWidth = 280;

        nameField = addDrawableChild(new TextFieldWidget(textRenderer, fieldX, panelY + 42, fieldWidth, 20,
                Text.translatable("customrecipe.export.datapack.name")));
        nameField.setMaxLength(80);
        nameField.setText(defaultPackName());
        nameField.setChangedListener(ignored -> refreshPreview());

        descriptionField = addDrawableChild(new TextFieldWidget(textRenderer, fieldX, panelY + 72, fieldWidth, 20,
                Text.translatable("customrecipe.export.datapack.description")));
        descriptionField.setMaxLength(160);
        descriptionField.setText(Text.translatable("customrecipe.export.datapack.default_description").getString());
        descriptionField.setChangedListener(ignored -> refreshPreview());

        versionField = addDrawableChild(new TextFieldWidget(textRenderer, fieldX, panelY + 102, 164, 20,
                Text.translatable("customrecipe.export.datapack.version")));
        versionField.setMaxLength(16);
        versionField.setText(clientVersion());
        versionField.setChangedListener(ignored -> refreshPreview());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.export.datapack.use_client"), b -> {
            versionField.setText(clientVersion());
            refreshPreview();
        }).dimensions(fieldX + 170, panelY + 102, 110, 20).build());

        exportButton = addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.export"),
                b -> export()).dimensions(width / 2 - 104, panelY + 226, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"),
                b -> client.setScreen(returnTo))
                .dimensions(width / 2 + 4, panelY + 226, 100, 20).build());
        refreshPreview();
    }

    private String defaultPackName() {
        String target = config.target().displayName().replaceFirst("^Current\\s+", "").trim();
        return target.isBlank() || target.equalsIgnoreCase("Global Library")
                ? "Custom Recipe Datapack" : target + " Recipes";
    }

    private String clientVersion() {
        return SharedConstants.getGameVersion().getName();
    }

    private void refreshPreview() {
        if (versionField == null) return;
        preview = DatapackExporter.preview(config.recipes, versionField.getText());
        if (exportButton != null) {
            exportButton.active = !nameField.getText().isBlank()
                    && !descriptionField.getText().isBlank()
                    && DatapackExporter.supportsVersion(versionField.getText())
                    && preview.exportable() > 0;
        }
        errorMessage = "";
    }

    private void export() {
        String packName = nameField.getText().trim();
        String version = versionField.getText().trim();
        String description = descriptionField.getText().trim();
        String filename = DatapackExporter.safeFileStem(packName) + "-" + version.replaceAll("[^0-9A-Za-z._-]", "_") + ".zip";
        WindowsFileDialogs.saveZip(filename, path -> {
            try {
                DatapackExporter.export(path, packName, description, version, config.recipes,
                        config.target().displayName());
                if (client.player != null) client.player.sendMessage(Text.translatable(
                        "customrecipe.export.datapack.success", path.getFileName().toString(), preview.exportable()), false);
                client.setScreen(config);
            } catch (java.io.IOException error) {
                reportError(error.getMessage());
            }
        }, this::reportError);
    }

    private void reportError(String message) {
        errorMessage = message == null || message.isBlank() ? "Export failed." : message;
        CustomRecipeMod.LOGGER.error("[Custom Recipe] Datapack export failed: {}", errorMessage);
    }

    @Override
    public void close() {
        client.setScreen(returnTo);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        config.render(context, -10_000, -10_000, delta);
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 400.0F);
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 440;
        int panelHeight = 264;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - 132;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF8181B1E);
        ExportFormatScreen.border(context, panelX, panelY, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawText(textRenderer, Text.translatable("customrecipe.export.datapack.name"), panelX + 18, panelY + 48,
                0xFFE0E6E3, false);
        context.drawText(textRenderer, Text.translatable("customrecipe.export.datapack.description"), panelX + 18, panelY + 78,
                0xFFE0E6E3, false);
        context.drawText(textRenderer, Text.translatable("customrecipe.export.datapack.version"), panelX + 18, panelY + 108,
                0xFFE0E6E3, false);

        boolean versionSupported = DatapackExporter.supportsVersion(versionField == null ? "" : versionField.getText());
        int statusColor = versionSupported && preview.exportable() > 0 ? 0xFF77DD88 : 0xFFFF7777;
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.export.datapack.preview",
                        preview.exportable(), preview.skipped()), width / 2, panelY + 140, statusColor);
        if (!versionSupported) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.export.datapack.unsupported"),
                    width / 2, panelY + 157, 0xFFFF7777);
        } else if (!preview.reasons().isEmpty()) {
            String reason = textRenderer.trimToWidth(preview.reasons().get(0), panelWidth - 44);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.export.datapack.skipped_reason", reason),
                    width / 2, panelY + 157, 0xFFFFCC66);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.export.datapack.only_custom"),
                width / 2, panelY + 178, 0xFFB8C7C1);
        String versions = textRenderer.trimToWidth(Text.translatable("customrecipe.export.datapack.supported",
                DatapackExporter.supportedVersions()).getString(), panelWidth - 60);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(versions),
                width / 2, panelY + 194, 0xFF8FC7E8);
        if (!errorMessage.isBlank()) {
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(errorMessage, panelWidth - 40),
                    width / 2, panelY + 210, 0xFFFF7777);
        }
        context.getMatrices().pop();
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}


