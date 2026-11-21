package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Datapack name/version customisation and compatibility preview. */
@Environment(EnvType.CLIENT)
final class DatapackExportScreen extends Screen {
    private final ConfigScreen config;
    private final Screen returnTo;
    private EditBox nameField;
    private EditBox descriptionField;
    private EditBox versionField;
    private Button exportButton;
    private DatapackExporter.Preview preview = new DatapackExporter.Preview(0, 0, java.util.List.of());
    private String errorMessage = "";

    DatapackExportScreen(ConfigScreen config, Screen returnTo) {
        super(Component.translatable("customrecipe.export.datapack.title"));
        this.config = config;
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        int panelX = width / 2 - 220;
        int panelY = height / 2 - 132;
        int fieldX = panelX + 132;
        int fieldWidth = 280;

        nameField = addRenderableWidget(new EditBox(font, fieldX, panelY + 42, fieldWidth, 20,
                Component.translatable("customrecipe.export.datapack.name")));
        nameField.setMaxLength(80);
        nameField.setValue(defaultPackName());
        nameField.setResponder(ignored -> refreshPreview());

        descriptionField = addRenderableWidget(new EditBox(font, fieldX, panelY + 72, fieldWidth, 20,
                Component.translatable("customrecipe.export.datapack.description")));
        descriptionField.setMaxLength(160);
        descriptionField.setValue(Component.translatable("customrecipe.export.datapack.default_description").getString());
        descriptionField.setResponder(ignored -> refreshPreview());

        versionField = addRenderableWidget(new EditBox(font, fieldX, panelY + 102, 164, 20,
                Component.translatable("customrecipe.export.datapack.version")));
        versionField.setMaxLength(16);
        versionField.setValue(clientVersion());
        versionField.setResponder(ignored -> refreshPreview());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.export.datapack.use_client"), b -> {
            versionField.setValue(clientVersion());
            refreshPreview();
        }).bounds(fieldX + 170, panelY + 102, 110, 20).build());

        exportButton = addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.export"),
                b -> export()).bounds(width / 2 - 104, panelY + 226, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                b -> minecraft.gui.setScreen(returnTo))
                .bounds(width / 2 + 4, panelY + 226, 100, 20).build());
        refreshPreview();
    }

    private String defaultPackName() {
        String target = config.target().displayName().replaceFirst("^Current\\s+", "").trim();
        return target.isBlank() || target.equalsIgnoreCase("Global Library")
                ? "Custom Recipe Datapack" : target + " Recipes";
    }

    private String clientVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    private void refreshPreview() {
        if (versionField == null) return;
        preview = DatapackExporter.preview(config.recipes, versionField.getValue());
        if (exportButton != null) {
            exportButton.active = !nameField.getValue().isBlank()
                    && !descriptionField.getValue().isBlank()
                    && DatapackExporter.supportsVersion(versionField.getValue())
                    && preview.exportable() > 0;
        }
        errorMessage = "";
    }

    private void export() {
        String packName = nameField.getValue().trim();
        String version = versionField.getValue().trim();
        String description = descriptionField.getValue().trim();
        String filename = DatapackExporter.safeFileStem(packName) + "-" + version.replaceAll("[^0-9A-Za-z._-]", "_") + ".zip";
        WindowsFileDialogs.saveZip(filename, path -> {
            try {
                DatapackExporter.export(path, packName, description, version, config.recipes,
                        config.target().displayName());
                if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatable(
                        "customrecipe.export.datapack.success", path.getFileName().toString(), preview.exportable()));
                minecraft.gui.setScreen(config);
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
    public void onClose() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        config.extractRenderState(context, -10_000, -10_000, delta);
        context.nextStratum();
        context.fill(0, 0, width, height, 0x98000000);
        int panelWidth = 440;
        int panelHeight = 264;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - 132;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF8181B1E);
        ExportFormatScreen.border(context, panelX, panelY, panelWidth, panelHeight);
        super.extractRenderState(context, mouseX, mouseY, delta);

        context.centeredText(font, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.text(font, Component.translatable("customrecipe.export.datapack.name"), panelX + 18, panelY + 48,
                0xFFE0E6E3, false);
        context.text(font, Component.translatable("customrecipe.export.datapack.description"), panelX + 18, panelY + 78,
                0xFFE0E6E3, false);
        context.text(font, Component.translatable("customrecipe.export.datapack.version"), panelX + 18, panelY + 108,
                0xFFE0E6E3, false);

        boolean versionSupported = DatapackExporter.supportsVersion(versionField == null ? "" : versionField.getValue());
        int statusColor = versionSupported && preview.exportable() > 0 ? 0xFF77DD88 : 0xFFFF7777;
        context.centeredText(font, Component.translatable("customrecipe.export.datapack.preview",
                        preview.exportable(), preview.skipped()), width / 2, panelY + 140, statusColor);
        if (!versionSupported) {
            context.centeredText(font, Component.translatable("customrecipe.export.datapack.unsupported"),
                    width / 2, panelY + 157, 0xFFFF7777);
        } else if (!preview.reasons().isEmpty()) {
            String reason = font.plainSubstrByWidth(preview.reasons().getFirst(), panelWidth - 44);
            context.centeredText(font, Component.translatable("customrecipe.export.datapack.skipped_reason", reason),
                    width / 2, panelY + 157, 0xFFFFCC66);
        }
        context.centeredText(font, Component.translatable("customrecipe.export.datapack.only_custom"),
                width / 2, panelY + 178, 0xFFB8C7C1);
        String versions = font.plainSubstrByWidth(Component.translatable("customrecipe.export.datapack.supported",
                DatapackExporter.supportedVersions()).getString(), panelWidth - 60);
        context.centeredText(font, Component.literal(versions),
                width / 2, panelY + 194, 0xFF8FC7E8);
        if (!errorMessage.isBlank()) {
            context.centeredText(font, font.plainSubstrByWidth(errorMessage, panelWidth - 40),
                    width / 2, panelY + 210, 0xFFFF7777);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}


