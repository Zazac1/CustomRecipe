package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.EditBox;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/** Modal editor for a custom item's visible name and internal identifier. */
final class RenameCustomItemScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    private final CustomItemsScreen parent;
    private final CustomItemEntry entry;
    private EditBox nameField;
    private EditBox idField;
    private Button saveButton;

    RenameCustomItemScreen(CustomItemsScreen parent, CustomItemEntry entry) {
        super(fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.rename"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int panelY = height / 2 - 74;
        int fieldWidth = Math.min(328, width - 56);
        nameField = addRenderableWidget(new EditBox(font, width / 2 - fieldWidth / 2,
                panelY + 38, fieldWidth, 20, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.name")));
        nameField.setMaxLength(128);
        nameField.setValue(entry.name == null ? "" : entry.name);

        idField = addRenderableWidget(new EditBox(font, width / 2 - fieldWidth / 2,
                panelY + 77, fieldWidth, 20, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.id")));
        idField.setMaxLength(64);
        idField.setValue(entry.id == null ? "" : entry.id);

        saveButton = addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.save"),
                button -> save()).bounds(width / 2 - 104, panelY + 116, 100, 20).build());
        updateSaveState();
        nameField.setResponder(value -> updateSaveState());
        idField.setResponder(value -> updateSaveState());

        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.cancel"),
                button -> minecraft.setScreen(parent)).bounds(width / 2 + 4, panelY + 116, 100, 20).build());
        setFocused(nameField);
        nameField.setFocused(true);
    }

    private void updateSaveState() {
        saveButton.active = !nameField.getValue().trim().isEmpty() && parent.canUseId(entry, idField.getValue());
    }

    private void save() {
        String name = nameField.getValue().trim();
        if (parent.rename(entry, name, idField.getValue())) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        parent.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = Math.min(360, width - 32);
        int panelHeight = 148;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(font, title, width / 2, panelY + 11, 0xFFFFFFFF);
        context.drawString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.name"), panelX + 16, panelY + 27, 0xFFCCCCCC);
        context.drawString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.id"), panelX + 16, panelY + 66, 0xFFCCCCCC);
        if (!idField.getValue().trim().isEmpty() && !parent.canUseId(entry, idField.getValue())) {
            context.drawCenteredString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.id_invalid"), width / 2, panelY + 101, 0xFFFF7777);
        }
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

