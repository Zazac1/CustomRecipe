package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Modal editor for a custom item's visible name and internal identifier. */
final class RenameCustomItemScreen extends Screen {
    private final CustomItemsScreen parent;
    private final CustomItemEntry entry;
    private EditBox nameField;
    private EditBox idField;
    private Button saveButton;

    RenameCustomItemScreen(CustomItemsScreen parent, CustomItemEntry entry) {
        super(Component.translatable("customrecipe.custom_items.rename"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int panelY = height / 2 - 74;
        int fieldWidth = Math.min(328, width - 56);
        nameField = addRenderableWidget(new EditBox(font, width / 2 - fieldWidth / 2,
                panelY + 38, fieldWidth, 20, Component.translatable("customrecipe.custom_items.name")));
        nameField.setMaxLength(128);
        nameField.setValue(entry.name == null ? "" : entry.name);

        idField = addRenderableWidget(new EditBox(font, width / 2 - fieldWidth / 2,
                panelY + 77, fieldWidth, 20, Component.translatable("customrecipe.custom_items.id")));
        idField.setMaxLength(64);
        idField.setValue(entry.id == null ? "" : entry.id);

        saveButton = addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.save"),
                button -> save()).bounds(width / 2 - 104, panelY + 116, 100, 20).build());
        updateSaveState();
        nameField.setResponder(value -> updateSaveState());
        idField.setResponder(value -> updateSaveState());

        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.cancel"),
                button -> minecraft.gui.setScreen(parent)).bounds(width / 2 + 4, panelY + 116, 100, 20).build());
        setFocused(nameField);
        nameField.setFocused(true);
    }

    private void updateSaveState() {
        saveButton.active = !nameField.getValue().isBlank() && parent.canUseId(entry, idField.getValue());
    }

    private void save() {
        String name = nameField.getValue().strip();
        if (parent.rename(entry, name, idField.getValue())) minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        parent.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = Math.min(360, width - 32);
        int panelHeight = 148;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        super.extractRenderState(context, mouseX, mouseY, delta);

        context.centeredText(font, title, width / 2, panelY + 11, 0xFFFFFFFF);
        context.text(font, Component.translatable("customrecipe.custom_items.name"), panelX + 16, panelY + 27, 0xFFCCCCCC, true);
        context.text(font, Component.translatable("customrecipe.custom_items.id"), panelX + 16, panelY + 66, 0xFFCCCCCC, true);
        if (!idField.getValue().isBlank() && !parent.canUseId(entry, idField.getValue())) {
            context.centeredText(font, Component.translatable("customrecipe.custom_items.id_invalid"), width / 2, panelY + 101, 0xFFFF7777);
        }
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
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

