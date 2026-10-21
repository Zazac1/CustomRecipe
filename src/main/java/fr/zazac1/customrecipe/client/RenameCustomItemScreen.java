package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Modal editor for a custom item's visible name and internal identifier. */
@Environment(EnvType.CLIENT)
final class RenameCustomItemScreen extends Screen {
    private final CustomItemsScreen parent;
    private final CustomItemEntry entry;
    private TextFieldWidget nameField;
    private TextFieldWidget idField;
    private ButtonWidget saveButton;

    RenameCustomItemScreen(CustomItemsScreen parent, CustomItemEntry entry) {
        super(Text.translatable("customrecipe.custom_items.rename"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int panelY = height / 2 - 74;
        int fieldWidth = Math.min(328, width - 56);
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - fieldWidth / 2,
                panelY + 38, fieldWidth, 20, Text.translatable("customrecipe.custom_items.name")));
        nameField.setMaxLength(128);
        nameField.setText(entry.name == null ? "" : entry.name);

        idField = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - fieldWidth / 2,
                panelY + 77, fieldWidth, 20, Text.translatable("customrecipe.custom_items.id")));
        idField.setMaxLength(64);
        idField.setText(entry.id == null ? "" : entry.id);

        saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.save"),
                button -> save()).dimensions(width / 2 - 104, panelY + 116, 100, 20).build());
        updateSaveState();
        nameField.setChangedListener(value -> updateSaveState());
        idField.setChangedListener(value -> updateSaveState());

        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.cancel"),
                button -> client.setScreen(parent)).dimensions(width / 2 + 4, panelY + 116, 100, 20).build());
        setFocused(nameField);
        nameField.setFocused(true);
    }

    private void updateSaveState() {
        saveButton.active = !nameField.getText().isBlank() && parent.canUseId(entry, idField.getText());
    }

    private void save() {
        String name = nameField.getText().strip();
        if (parent.rename(entry, name, idField.getText())) client.setScreen(parent);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        parent.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = Math.min(360, width - 32);
        int panelHeight = 148;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 11, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("customrecipe.custom_items.name"), panelX + 16, panelY + 27, 0xFFCCCCCC);
        context.drawTextWithShadow(textRenderer, Text.translatable("customrecipe.custom_items.id"), panelX + 16, panelY + 66, 0xFFCCCCCC);
        if (!idField.getText().isBlank() && !parent.canUseId(entry, idField.getText())) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.custom_items.id_invalid"), width / 2, panelY + 101, 0xFFFF7777);
        }
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawHorizontalLine(x, x + width - 1, y, color);
        context.drawHorizontalLine(x, x + width - 1, y + height - 1, color);
        context.drawVerticalLine(x, y, y + height - 1, color);
        context.drawVerticalLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}

