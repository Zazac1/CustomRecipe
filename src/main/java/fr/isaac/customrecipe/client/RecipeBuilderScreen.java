package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.CustomRecipeEntry;
import fr.isaac.customrecipe.RecipeIntegrity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.*;

@Environment(EnvType.CLIENT)
public class RecipeBuilderScreen extends Screen {
    private static final int PAD = 20;
    private static final int SLOT = 20;
    private static final int ITEM_BATCH_SIZE = 20;
    private static final int VISIBLE_S = 10;
    private static final int SUGG_H = 16;

    private final ConfigScreen parent;
    private final String[] slotItems = new String[9];
    private String resultItemId = "";
    private int resultCount = 1;
    private boolean shaped;
    private boolean knownByDefault;
    /** -2 = no slot, -1 = result, 0-8 = ingredient slot. */
    private int selectedSlot = -2;
    /** null = no selection, empty string = the permanent Empty tile. */
    private String heldItemId;
    private String itemFieldText = "";
    /** IDs only: stacks are created just for visible rows. */
    private List<String> itemMatches = new ArrayList<>();
    private int suggestionScroll;
    private int loadedSuggestionLimit = ITEM_BATCH_SIZE;
    private boolean draggingSuggestionScrollbar;
    private int restoreFocus;
    private EditBox itemField;

    public RecipeBuilderScreen(ConfigScreen parent) {
        super(Component.literal("Create a Recipe"));
        this.parent = parent;
        Arrays.fill(slotItems, "");
    }

    private int leftW() { return Math.min(280, Math.max(210, width / 3)); }
    private int leftX() { return PAD; }
    private int rightX() { return leftX() + leftW() + PAD; }
    private int rightW() { return width - rightX() - PAD; }
    private int panelY() { return 20; }
    private int workspaceH() { return paletteY() + paletteH() - panelY() + 12; }
    private int gridX() { return rightX() + (rightW() - 3 * SLOT - 12 - SLOT) / 2; }
    private int gridY() { return panelY() + 30; }
    private int resX() { return gridX() + 3 * SLOT + 12; }
    private int resY() { return gridY() + SLOT; }
    private int settingsX() { return gridX() - 12; }
    private int slotLabelY() { return panelY() + 8; }
    private int fieldY() { return slotLabelY() + 14; }
    private int suggY() { return fieldY() + 16; }
    private int paletteY() { return gridY() + 3 * SLOT + 82; }
    private int loadedSuggestions() { return Math.min(loadedSuggestionLimit, itemMatches.size()); }
    private int visibleSuggestions() { return Math.min(VISIBLE_S, Math.max(0, loadedSuggestions() - suggestionScroll)); }
    private int suggestionScrollbarX() { return leftX() + leftW() - 7; }
    private int paletteColumns() { return Math.max(8, Math.min(16, (rightW() - 16) / 24)); }
    private int paletteRows() { return Math.max(1, (usedItems().size() + 1 + paletteColumns() - 1) / paletteColumns()); }
    private int paletteH() { return 22 + paletteRows() * 24 + 6; }

    @Override
    protected void init() {
        if (itemFieldText.isBlank() && itemMatches.isEmpty()) itemMatches = findItemMatches("");
        addRenderableOnly((ctx, mx, my, delta) -> renderFills(ctx, mx, my));
        addRenderableWidget(makeRightLabel(rightX() + 8, panelY() + 8, "Recipe preview", 0xFFEECC77));

        String slotLabel = switch (selectedSlot) {
            case -2 -> "Click a slot...";
            case -1 -> "Result item:";
            default -> "Slot " + (selectedSlot + 1) + ":";
        };
        addRenderableWidget(makeLabel(leftX() + 8, slotLabelY(), slotLabel, 0xCCCCCC));

        itemField = addRenderableWidget(new EditBox(font, leftX(), fieldY(), leftW() - 14, 14, Component.literal("item")));
        itemField.setHint(Component.literal("Search item..."));
        itemField.setMaxLength(100);
        itemField.setValue(itemFieldText);
        itemField.setResponder(s -> { itemFieldText = s; restoreFocus = 1; onItemTyped(s); });
        addRenderableWidget(Button.builder(Component.literal("x"), b -> { itemField.setValue(""); setFocused(itemField); })
                .bounds(leftX() + leftW() - 14, fieldY(), 14, 14).build());

        for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
            int rowY = suggY() + (i - suggestionScroll) * SUGG_H;
            addRenderableWidget(makeLabel(leftX() + 22, rowY + 4, itemMatches.get(i), 0xCCCCCC));
        }
        String heldLabel = heldItemId == null ? "Pick an item, then click a slot"
                : heldItemId.isEmpty() ? "Holding: Empty (removes an item)" : "Holding: " + shortId(heldItemId);
        addRenderableWidget(makeRightLabel(rightX() + 16, paletteY() + 6, heldLabel, 0xFF88BBDD));

        MultiLineTextWidget arrow = new MultiLineTextWidget(gridX() + 3 * SLOT + 2,
                gridY() + SLOT + (SLOT - 8) / 2, Component.literal("->"), font);
        arrow.setMaxWidth(10);
        arrow.setMaxRows(1);
        addRenderableWidget(arrow);

        int countY = gridY() + 3 * SLOT + 10;
        addRenderableWidget(makeRightLabel(settingsX(), countY, "Result x" + resultCount + ":", 0xCCCCCC));
        addRenderableWidget(Button.builder(Component.literal("-"), b -> { if (resultCount > 1) { resultCount--; rebuildWidgets(); } })
                .bounds(settingsX() + 78, countY - 1, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { if (resultCount < 64) { resultCount++; rebuildWidgets(); } })
                .bounds(settingsX() + 94, countY - 1, 14, 14).build());
        int modeY = countY + 18;
        addRenderableWidget(Button.builder(shaped ? Component.literal("Mode: Shaped").withColor(0xFFD700)
                        : Component.literal("Mode: Shapeless").withColor(0x88FFFF), b -> { shaped = !shaped; rebuildWidgets(); })
                .bounds(settingsX(), modeY, 110, 14).build());
        addRenderableWidget(Button.builder(knownByDefault ? Component.literal("Known by default: ON").withColor(0x55FF55)
                        : Component.literal("Known by default: OFF").withColor(0xFFCC55), b -> { knownByDefault = !knownByDefault; rebuildWidgets(); })
                .bounds(settingsX(), modeY + 18, 140, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 102, height - 22, 98, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ Add Recipe"), b -> confirm())
                .bounds(width / 2 + 4, height - 22, 98, 18).build());
        if (restoreFocus == 1 && itemField != null) { setFocused(itemField); restoreFocus = 0; }
    }

    private void onItemTyped(String query) {
        itemMatches = findItemMatches(query);
        suggestionScroll = 0;
        loadedSuggestionLimit = ITEM_BATCH_SIZE;
        rebuildWidgets();
    }

    private List<String> findItemMatches(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            if (entry.getValue() == Items.AIR) continue;
            String id = entry.getKey().identifier().toString();
            String path = entry.getKey().identifier().getPath();
            if (id.contains(q) || path.contains(q)) matches.add(id);
        }
        matches.sort(String::compareTo);
        return matches;
    }

    private void selectItem(String itemId) {
        heldItemId = itemId;
        itemFieldText = shortId(itemId);
        itemMatches = new ArrayList<>();
        suggestionScroll = 0;
        applyNewHeldItemToSelectedSlot();
        rebuildWidgets();
    }

    private void selectEmptyItem() {
        heldItemId = "";
        itemFieldText = "";
        itemMatches = new ArrayList<>();
        suggestionScroll = 0;
        applyNewHeldItemToSelectedSlot();
        rebuildWidgets();
    }

    private void applyNewHeldItemToSelectedSlot() {
        if (selectedSlot == -2) return;
        if (selectedSlotIsEmpty()) applyHeldItemToSelectedSlot(); else selectedSlot = -2;
    }

    private boolean selectedSlotIsEmpty() {
        if (selectedSlot == -1) return resultItemId.isEmpty();
        return selectedSlot >= 0 && selectedSlot < 9 && slotItems[selectedSlot].isEmpty();
    }

    private void applyHeldItemToSelectedSlot() {
        if (heldItemId == null) return;
        if (selectedSlot == -1) resultItemId = heldItemId;
        else if (selectedSlot >= 0 && selectedSlot < 9) slotItems[selectedSlot] = heldItemId;
    }

    private void selectSlot(int slot) {
        selectedSlot = slot;
        applyHeldItemToSelectedSlot();
        restoreFocus = 1;
        rebuildWidgets();
    }

    private List<String> usedItems() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (resultItemId != null && !resultItemId.isBlank()) items.add(resultItemId);
        for (String item : slotItems) if (item != null && !item.isBlank()) items.add(item);
        return new ArrayList<>(items);
    }

    private void renderFills(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int suggestionsH = itemMatches.isEmpty() ? 0 : visibleSuggestions() * SUGG_H + 6;
        int leftPanelH = Math.max(70, suggY() + suggestionsH - panelY());
        drawPanel(ctx, leftX(), panelY(), leftW(), leftPanelH, 0xAA141A24, 0xFF40506A);
        drawPanel(ctx, rightX(), panelY(), rightW(), workspaceH(), 0xAA141A24, 0xFF5B5A45);
        int gridX = gridX(), gridY = gridY();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            int slot = row * 3 + column;
            int x = gridX + column * SLOT, y = gridY + row * SLOT;
            boolean selected = selectedSlot == slot;
            ctx.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, selected ? 0xFF1A3A6A : 0xFF3A3A3A);
            drawBox(ctx, x, y, SLOT, SLOT, selected ? 0xFF4488FF : 0xFF555555);
            drawItem(ctx, slotItems[slot], x + 2, y + 2);
        }
        int resultX = resX(), resultY = resY();
        boolean selectedResult = selectedSlot == -1;
        ctx.fill(resultX + 1, resultY + 1, resultX + SLOT - 1, resultY + SLOT - 1,
                selectedResult ? 0xFF1A3A2A : 0xFF3A3A3A);
        drawBox(ctx, resultX, resultY, SLOT, SLOT, selectedResult ? 0xFF44BB44 : 0xFF908830);
        drawItem(ctx, resultItemId, resultX + 2, resultY + 2);

        if (!itemMatches.isEmpty()) {
            int y = suggY(), h = visibleSuggestions() * SUGG_H;
            ctx.fill(leftX(), y, leftX() + leftW(), y + h, 0xFF1A1C28);
            drawBox(ctx, leftX(), y, leftW(), h, 0xFF4A5578);
            for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
                int rowY = y + (i - suggestionScroll) * SUGG_H;
                if (mouseX >= leftX() && mouseX < leftX() + leftW() && mouseY >= rowY && mouseY < rowY + SUGG_H)
                    ctx.fill(leftX() + 1, rowY, leftX() + leftW() - 1, rowY + SUGG_H, 0x553355BB);
                drawItem(ctx, itemMatches.get(i), leftX() + 2, rowY);
            }
            if (itemMatches.size() > visibleSuggestions()) {
                int scrollX = suggestionScrollbarX();
                ctx.fill(scrollX, y + 1, leftX() + leftW() - 1, y + h - 1, 0xFF10141C);
                int thumbH = Math.max(8, h * visibleSuggestions() / itemMatches.size());
                int maxScroll = Math.max(1, itemMatches.size() - visibleSuggestions());
                int thumbY = y + (h - thumbH) * suggestionScroll / maxScroll;
                ctx.fill(scrollX + 1, thumbY, leftX() + leftW() - 1, thumbY + thumbH, 0xFF7AA8D0);
            }
        }

        int paletteX = rightX() + 8, paletteY = paletteY(), paletteW = rightW() - 16, paletteH = paletteH();
        ctx.fill(paletteX + 1, paletteY + 1, paletteX + paletteW - 1, paletteY + paletteH - 1, 0xDD101820);
        drawBox(ctx, paletteX, paletteY, paletteW, paletteH, 0xFF3E6B84);
        List<String> used = usedItems();
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 21 + (i / paletteColumns()) * 24;
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            boolean empty = i == 0;
            ctx.fill(x + 1, y + 1, x + 19, y + 19, hovered ? 0xFF315D7D : empty ? 0xFF242A30 : 0xFF303030);
            drawBox(ctx, x, y, 20, 20, hovered ? 0xFF78C8FF : empty ? 0xFF9A6670 : 0xFF5C6D78);
            if (empty) ctx.text(font, "x", x + 6, y + 5, 0xFFFF7777, false); else drawItem(ctx, used.get(i - 1), x + 2, y + 2);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (itemField != null) drawBox(ctx, leftX(), fieldY(), leftW(), 14, itemField.isFocused() ? 0xFF5577DD : 0xFF404050);
        if (!itemMatches.isEmpty()) {
            int y = suggY();
            for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
                int rowY = y + (i - suggestionScroll) * SUGG_H;
                if (mouseX >= leftX() && mouseX < leftX() + leftW() && mouseY >= rowY && mouseY < rowY + SUGG_H) {
                    ctx.setTooltipForNextFrame(font, List.of(Component.literal(itemMatches.get(i)).getVisualOrderText()), mouseX, mouseY);
                    break;
                }
            }
        }
        List<String> used = usedItems();
        int paletteX = rightX() + 8, paletteY = paletteY();
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 21 + (i / paletteColumns()) * 24;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                ctx.setTooltipForNextFrame(font, List.of(Component.literal(i == 0 ? "Empty - remove item" : used.get(i - 1)).getVisualOrderText()), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean focused) {
        double mouseX = click.x(), mouseY = click.y();
        int gridX = gridX(), gridY = gridY();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            int slot = row * 3 + column;
            int x = gridX + column * SLOT, y = gridY + row * SLOT;
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) { selectSlot(slot); return true; }
        }
        int resultX = resX(), resultY = resY();
        if (mouseX >= resultX && mouseX < resultX + SLOT && mouseY >= resultY && mouseY < resultY + SLOT) { selectSlot(-1); return true; }
        if (itemField != null && mouseX >= leftX() && mouseX < leftX() + leftW() - 14 && mouseY >= fieldY() && mouseY < fieldY() + 14) {
            restoreFocus = 1;
            if (itemMatches.isEmpty()) { onItemTyped(itemFieldText); return true; }
            return super.mouseClicked(click, focused);
        }
        if (!itemMatches.isEmpty()) {
            int y = suggY(), listWidth = leftW();
            if (itemMatches.size() > visibleSuggestions() && mouseX >= suggestionScrollbarX() && mouseX < leftX() + listWidth && mouseY >= y && mouseY < y + visibleSuggestions() * SUGG_H) {
                draggingSuggestionScrollbar = true;
                updateSuggestionScrollbar(mouseY);
                return true;
            }
            if (mouseX >= leftX() && mouseX < leftX() + listWidth && mouseY >= y && mouseY < y + visibleSuggestions() * SUGG_H) {
                int index = suggestionScroll + (int) (mouseY - y) / SUGG_H;
                if (index >= 0 && index < loadedSuggestions()) { selectItem(itemMatches.get(index)); return true; }
            }
        }
        List<String> used = usedItems();
        int paletteX = rightX() + 8, paletteY = paletteY();
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 21 + (i / paletteColumns()) * 24;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                if (i == 0) selectEmptyItem(); else selectItem(used.get(i - 1));
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int y = suggY();
        if (itemMatches.size() > visibleSuggestions() && mouseX >= leftX() && mouseX < leftX() + leftW() && mouseY >= y && mouseY < y + visibleSuggestions() * SUGG_H) {
            int direction = -(int) Math.signum(verticalAmount);
            if (direction > 0 && suggestionScroll + visibleSuggestions() >= loadedSuggestions() && loadedSuggestions() < itemMatches.size()) loadedSuggestionLimit += ITEM_BATCH_SIZE;
            int maxScroll = loadedSuggestions() - visibleSuggestions();
            int next = Math.max(0, Math.min(maxScroll, suggestionScroll + direction));
            if (next != suggestionScroll) { suggestionScroll = next; restoreFocus = 1; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingSuggestionScrollbar) { updateSuggestionScrollbar(click.y()); return true; }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingSuggestionScrollbar = false;
        return super.mouseReleased(click);
    }

    private void updateSuggestionScrollbar(double mouseY) {
        int visible = visibleSuggestions(), maxScroll = itemMatches.size() - visible;
        if (maxScroll <= 0) return;
        int y = suggY(), height = visible * SUGG_H;
        double progress = Math.max(0.0, Math.min(1.0, (mouseY - y) / Math.max(1, height - 1)));
        int next = (int) Math.round(progress * maxScroll);
        int required = next + visible;
        if (required > loadedSuggestions()) loadedSuggestionLimit = Math.min(itemMatches.size(), ((required + ITEM_BATCH_SIZE - 1) / ITEM_BATCH_SIZE) * ITEM_BATCH_SIZE);
        if (next != suggestionScroll) { suggestionScroll = next; restoreFocus = 1; rebuildWidgets(); }
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (key.key() == 256) { minecraft.gui.setScreen(parent); return true; }
        return super.keyPressed(key);
    }

    private void confirm() {
        if (resultItemId.isEmpty()) return;
        boolean hasIngredient = false;
        for (String item : slotItems) if (item != null && !item.isEmpty()) { hasIngredient = true; break; }
        if (!hasIngredient) return;
        CustomRecipeEntry entry = new CustomRecipeEntry();
        entry.id = UUID.randomUUID().toString();
        entry.server_enabled = parent.isServerManaged() ? Boolean.TRUE : Boolean.FALSE;
        entry.known_by_default = knownByDefault;
        entry.result = resultItemId;
        entry.count = Math.max(1, resultCount);
        if (shaped) {
            entry.type = "shaped";
            Map<String, Character> itemToChar = new LinkedHashMap<>();
            char next = 'A';
            String[] rows = new String[3];
            for (int row = 0; row < 3; row++) {
                StringBuilder line = new StringBuilder();
                for (int column = 0; column < 3; column++) {
                    String item = slotItems[row * 3 + column];
                    if (item == null || item.isEmpty()) line.append(' ');
                    else { if (!itemToChar.containsKey(item)) itemToChar.put(item, next++); line.append(itemToChar.get(item)); }
                }
                rows[row] = line.toString();
            }
            entry.pattern = Arrays.asList(rows);
            for (Map.Entry<String, Character> value : itemToChar.entrySet()) entry.keys.put(String.valueOf(value.getValue()), value.getKey());
        } else {
            entry.type = "shapeless";
            for (String item : slotItems) if (item != null && !item.isEmpty()) entry.ingredients.add(item);
        }
        RecipeIntegrity.rememberRequiredMods(entry);
        parent.recipes.add(entry);
        minecraft.gui.setScreen(parent);
    }

    private void drawItem(GuiGraphicsExtractor ctx, String itemId, int x, int y) {
        if (itemId == null || itemId.isEmpty()) return;
        var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId));
        if (item != null && item != Items.AIR) ctx.item(ClientItemStacks.fromItem(item), x, y);
    }

    private String shortId(String id) { return id.startsWith("minecraft:") ? id.substring(10) : id; }

    private MultiLineTextWidget makeLabel(int x, int y, String text, int color) {
        MultiLineTextWidget widget = new MultiLineTextWidget(x, y, Component.literal(text).withColor(color), font);
        widget.setMaxWidth(leftW());
        widget.setMaxRows(1);
        return widget;
    }

    private MultiLineTextWidget makeRightLabel(int x, int y, String text, int color) {
        MultiLineTextWidget widget = new MultiLineTextWidget(x, y, Component.literal(text).withColor(color), font);
        widget.setMaxWidth(width - x - PAD);
        widget.setMaxRows(1);
        return widget;
    }

    private void drawBox(GuiGraphicsExtractor ctx, int x, int y, int width, int height, int color) {
        ctx.horizontalLine(x, x + width - 1, y, color);
        ctx.horizontalLine(x, x + width - 1, y + height - 1, color);
        ctx.verticalLine(x, y, y + height - 1, color);
        ctx.verticalLine(x + width - 1, y, y + height - 1, color);
    }

    private void drawPanel(GuiGraphicsExtractor ctx, int x, int y, int width, int height, int fill, int border) {
        ctx.fill(x, y, x + width, y + height, fill);
        drawBox(ctx, x, y, width, height, border);
    }

    @Override public boolean isPauseScreen() { return false; }
}
