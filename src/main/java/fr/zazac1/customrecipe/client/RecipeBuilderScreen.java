package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.RecipeIntegrity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

@Environment(EnvType.CLIENT)
public class RecipeBuilderScreen extends Screen {

    private static final int PAD   = 20;
    private static final int SLOT  = 20;  // slot size in pixels
    private static final int ITEM_BATCH_SIZE = 20;
    private static final int VISIBLE_S = 10;
    private static final int SUGG_H = 16; // suggestion row height

    private final ConfigScreen parent;

    // ── persistent state (survives clearAndInit) ──────────────────────────
    private final String[] slotItems = new String[9]; // grid slots (0-8)
    private String resultItemId   = "";
    private int    resultCount    = 1;
    private boolean shaped        = false;
    private boolean knownByDefault = false;
    /** -2 = nothing selected, -1 = result slot, 0-8 = grid slot */
    private int selectedSlot      = -2;
    /** null = no item picked; empty string = the permanent empty/erase item. */
    private String heldItemId;

    // text field state
    private String         itemFieldText = "";
    /** Matching IDs; ItemStacks are only created for currently visible rows. */
    private List<String> itemMatches = new ArrayList<>();
    private int suggestionScroll;
    private int loadedSuggestionLimit = ITEM_BATCH_SIZE;
    private boolean draggingSuggestionScrollbar;
    private int            restoreFocus  = 0; // 1 = itemField

    // rebuilt each init
    private TextFieldWidget itemField;

    public RecipeBuilderScreen(ConfigScreen parent) {
        super(Text.literal("Create a Recipe"));
        this.parent = parent;
        Arrays.fill(slotItems, "");
    }

    // ── layout helpers ────────────────────────────────────────────────────

    private int leftW()  { return Math.min(280, Math.max(210, width / 3)); }
    private int leftX()  { return PAD; }
    private int rightX() { return leftX() + leftW() + PAD; }
    private int rightW() { return width - rightX() - PAD; }
    private int panelY() { return 20; }
    private int workspaceH() { return paletteY() + paletteH() - panelY() + 12; }

    /** X of the top-left slot in the 3×3 grid */
    private int gridX() {
        return rightX() + (rightW() - 3 * SLOT - 12 - SLOT) / 2;
    }
    private int gridY()    { return panelY() + 30; }
    private int resX()     { return gridX() + 3 * SLOT + 12; }
    private int resY()     { return gridY() + SLOT; }

    // left panel y positions
    private int settingsX() { return gridX() - 12; }
    private int slotLabelY() { return panelY() + 8; }
    private int fieldY()     { return slotLabelY() + 14; }
    private int suggY()      { return fieldY() + 16; }
    private int paletteY()   { return gridY() + 3 * SLOT + 82; }
    private int loadedSuggestions() { return Math.min(loadedSuggestionLimit, itemMatches.size()); }
    private int visibleSuggestions() { return Math.min(VISIBLE_S, Math.max(0, loadedSuggestions() - suggestionScroll)); }
    private int suggestionScrollbarX() { return leftX() + leftW() - 7; }
    private int paletteColumns() { return Math.max(8, Math.min(16, (rightW() - 16) / 24)); }
    private int paletteRows() { return Math.max(1, (usedItems().size() + 1 + paletteColumns() - 1) / paletteColumns()); }
    private int paletteH()   { return 22 + paletteRows() * 24 + 6; }

    // ── init ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (itemFieldText.isBlank() && itemMatches.isEmpty()) itemMatches = findItemMatches("");
        // Background fills + grid + result slot drawn via addDrawable
        addDrawable((ctx, mx, my, d) -> renderFills(ctx, mx, my));
        addDrawableChild(makeRightLabel(rightX() + 8, panelY() + 8, "Recipe preview", 0xFFEECC77));

        // ── Left panel ───────────────────────────────────────────────────

        // Slot label
        String slotLabel = switch (selectedSlot) {
            case -2 -> "Click a slot…";
            case -1 -> "Result item:";
            default -> "Slot " + (selectedSlot + 1) + ":";
        };
        addDrawableChild(makeLabel(leftX() + 8, slotLabelY(), slotLabel, 0xCCCCCC));

        // Item search field
        itemField = addDrawableChild(new TextFieldWidget(
                textRenderer, leftX(), fieldY(), leftW() - 14, 14, Text.literal("item")));
        itemField.setPlaceholder(Text.literal("Search item…"));
        itemField.setMaxLength(100);
        itemField.setText(itemFieldText);
        itemField.setChangedListener(s -> { itemFieldText = s; restoreFocus = 1; onItemTyped(s); });

        addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> {
            itemField.setText("");
            setFocused(itemField);
        }).dimensions(leftX() + leftW() - 14, fieldY(), 14, 14).build());

        // Autocomplete suggestions (label widgets)
        for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
            String id = itemMatches.get(i);
            int ry = suggY() + (i - suggestionScroll) * SUGG_H;
            addDrawableChild(makeLabel(leftX() + 22, ry + 4, id, 0xCCCCCC));
        }
        String heldLabel = heldItemId == null ? "Pick an item, then click a slot"
                : heldItemId.isEmpty() ? "Holding: Empty (removes an item)"
                : "Holding: " + shortId(heldItemId);
        addDrawableChild(makeRightLabel(rightX() + 16, paletteY() + 6, heldLabel, 0xFF88BBDD));

        // ── Right panel ──────────────────────────────────────────────────

        // Arrow between grid and result
        MultilineTextWidget arrow = new MultilineTextWidget(
                gridX() + 3 * SLOT + 2, gridY() + SLOT + (SLOT - 8) / 2,
                Text.literal("→"), textRenderer);
        arrow.setMaxWidth(10);
        arrow.setMaxRows(1);
        addDrawableChild(arrow);

        // Result count label + controls
        int countY = gridY() + 3 * SLOT + 10;
        addDrawableChild(makeRightLabel(settingsX(), countY, "Result ×" + resultCount + ":", 0xCCCCCC));

        addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                b -> { if (resultCount > 1) { resultCount--; clearAndInit(); } }
        ).dimensions(settingsX() + 78, countY - 1, 14, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> { if (resultCount < 64) { resultCount++; clearAndInit(); } }
        ).dimensions(settingsX() + 94, countY - 1, 14, 14).build());

        // Shaped / Shapeless toggle
        int modeY = countY + 18;
        addDrawableChild(ButtonWidget.builder(
                shaped ? Text.literal("Mode: Shaped").withColor(0xFFD700)
                       : Text.literal("Mode: Shapeless").withColor(0x88FFFF),
                b -> { shaped = !shaped; clearAndInit(); }
        ).dimensions(settingsX(), modeY, 110, 14).build());

        addDrawableChild(ButtonWidget.builder(
                knownByDefault ? Text.literal("Known by default: ON").withColor(0x55FF55)
                               : Text.literal("Known by default: OFF").withColor(0xFFCC55),
                b -> { knownByDefault = !knownByDefault; clearAndInit(); }
        ).dimensions(settingsX(), modeY + 18, 140, 14).build());

        // ── Bottom buttons ────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),
                b -> client.setScreen(parent)
        ).dimensions(width / 2 - 102, height - 22, 98, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✓ Add Recipe"),
                b -> confirm()
        ).dimensions(width / 2 + 4, height - 22, 98, 18).build());

        // Restore focus after clearAndInit
        if (restoreFocus == 1 && itemField != null) {
            setFocused(itemField);
            restoreFocus = 0;
        }
    }

    // ── autocomplete ──────────────────────────────────────────────────────

    private void onItemTyped(String q) {
        itemMatches = findItemMatches(q);
        suggestionScroll = 0;
        loadedSuggestionLimit = ITEM_BATCH_SIZE;
        clearAndInit();
    }

    private List<String> findItemMatches(String q) {
        String ql = q.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (var entry : Registries.ITEM.getEntrySet()) {
            if (entry.getValue() == Items.AIR) continue;
            String id = entry.getKey().getValue().toString();
            String path = entry.getKey().getValue().getPath();
            if (id.contains(ql) || path.contains(ql)) matches.add(id);
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
        clearAndInit();
    }

    private void selectEmptyItem() {
        heldItemId = "";
        itemFieldText = "";
        itemMatches = new ArrayList<>();
        suggestionScroll = 0;
        applyNewHeldItemToSelectedSlot();
        clearAndInit();
    }

    /** Never silently overwrite the last non-empty selected slot when changing item. */
    private void applyNewHeldItemToSelectedSlot() {
        if (selectedSlot == -2) return;
        if (selectedSlotIsEmpty()) applyHeldItemToSelectedSlot();
        else selectedSlot = -2;
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
        clearAndInit();
    }

    private List<String> usedItems() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (resultItemId != null && !resultItemId.isBlank()) items.add(resultItemId);
        for (String item : slotItems) if (item != null && !item.isBlank()) items.add(item);
        return new ArrayList<>(items);
    }

    private void selectUsedItem(String itemId) {
        selectItem(itemId);
    }

    private void updateSuggestionScrollbar(double mouseY) {
        int visible = visibleSuggestions();
        int maxScroll = itemMatches.size() - visible;
        if (maxScroll <= 0) return;
        int sy = suggY();
        int sh = visible * SUGG_H;
        double progress = Math.max(0.0, Math.min(1.0, (mouseY - sy) / Math.max(1, sh - 1)));
        int next = (int) Math.round(progress * maxScroll);
        int required = next + visible;
        if (required > loadedSuggestions()) {
            loadedSuggestionLimit = Math.min(itemMatches.size(),
                    ((required + ITEM_BATCH_SIZE - 1) / ITEM_BATCH_SIZE) * ITEM_BATCH_SIZE);
        }
        if (next != suggestionScroll) {
            suggestionScroll = next;
            restoreFocus = 1;
            clearAndInit();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        int sy = suggY();
        if (itemMatches.size() > visibleSuggestions()
                && mx >= leftX() && mx < leftX() + leftW() - 14
                && my >= sy && my < sy + visibleSuggestions() * SUGG_H) {
            int direction = -(int) Math.signum(verticalAmount);
            if (direction > 0 && suggestionScroll + visibleSuggestions() >= loadedSuggestions()
                    && loadedSuggestions() < itemMatches.size()) loadedSuggestionLimit += ITEM_BATCH_SIZE;
            int maxScroll = loadedSuggestions() - visibleSuggestions();
            int next = Math.max(0, Math.min(maxScroll, suggestionScroll + direction));
            if (next != suggestionScroll) {
                suggestionScroll = next;
                restoreFocus = 1;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSuggestionScrollbar) {
            updateSuggestionScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSuggestionScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawPanel(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        drawBox(ctx, x, y, w, h, border);
    }

    // ── rendering ─────────────────────────────────────────────────────────

    private void renderFills(DrawContext ctx, int mx, int my) {
        int suggestionsH = itemMatches.isEmpty() ? 0 : visibleSuggestions() * SUGG_H + 6;
        int leftPanelH = Math.max(70, suggY() + suggestionsH - panelY());
        drawPanel(ctx, leftX(), panelY(), leftW(), leftPanelH, 0xAA141A24, 0xFF40506A);
        drawPanel(ctx, rightX(), panelY(), rightW(), workspaceH(), 0xAA141A24, 0xFF5B5A45);
        int gx = gridX(), gy = gridY();

        // 3×3 grid slots
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slot = r * 3 + c;
                int sx = gx + c * SLOT, sy = gy + r * SLOT;
                boolean sel = selectedSlot == slot;
                ctx.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1,
                        sel ? 0xFF1A3A6A : 0xFF3A3A3A);
                drawBox(ctx, sx, sy, SLOT, SLOT,
                        sel ? 0xFF4488FF : 0xFF555555);
                String itemId = slotItems[slot];
                if (itemId != null && !itemId.isEmpty()) {
                    var item = Registries.ITEM.get(Identifier.tryParse(itemId));
                    if (item != null && item != Items.AIR)
                        ctx.drawItem(new ItemStack(item), sx + 2, sy + 2);
                }
            }
        }

        // Result slot
        int rx = resX(), ry = resY();
        boolean resSel = selectedSlot == -1;
        ctx.fill(rx + 1, ry + 1, rx + SLOT - 1, ry + SLOT - 1,
                resSel ? 0xFF1A3A2A : 0xFF3A3A3A);
        drawBox(ctx, rx, ry, SLOT, SLOT,
                resSel ? 0xFF44BB44 : 0xFF908830);
        if (!resultItemId.isEmpty()) {
            var item = Registries.ITEM.get(Identifier.tryParse(resultItemId));
            if (item != null && item != Items.AIR)
                ctx.drawItem(new ItemStack(item), rx + 2, ry + 2);
        }

        // Suggestion list background + icons
        if (!itemMatches.isEmpty()) {
            int sy = suggY(), sh = visibleSuggestions() * SUGG_H;
            ctx.fill(leftX(), sy, leftX() + leftW(), sy + sh, 0xFF1A1C28);
            drawBox(ctx, leftX(), sy, leftW(), sh, 0xFF4A5578);
            for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
                int ry2 = sy + (i - suggestionScroll) * SUGG_H;
                if (mx >= leftX() && mx < leftX() + leftW() && my >= ry2 && my < ry2 + SUGG_H)
                    ctx.fill(leftX() + 1, ry2, leftX() + leftW() - 1, ry2 + SUGG_H, 0x553355BB);
                var item2 = Registries.ITEM.get(Identifier.tryParse(itemMatches.get(i)));
                if (item2 != null && item2 != Items.AIR)
                    ctx.drawItem(new ItemStack(item2), leftX() + 2, ry2);
            }
            if (itemMatches.size() > visibleSuggestions()) {
                int scrollbarX = suggestionScrollbarX();
                ctx.fill(scrollbarX, sy + 1, leftX() + leftW() - 1, sy + sh - 1, 0xFF10141C);
                int thumbH = Math.max(8, sh * visibleSuggestions() / itemMatches.size());
                int maxScroll = Math.max(1, itemMatches.size() - visibleSuggestions());
                int thumbY = sy + (sh - thumbH) * suggestionScroll / maxScroll;
                ctx.fill(scrollbarX + 1, thumbY, leftX() + leftW() - 1, thumbY + thumbH, 0xFF7AA8D0);
            }
        }

        int paletteY = paletteY();
        int paletteH = paletteH();
        int paletteX = rightX() + 8;
        int paletteW = rightW() - 16;
        ctx.fill(paletteX + 1, paletteY + 1, paletteX + paletteW - 1, paletteY + paletteH - 1, 0xDD101820);
        drawBox(ctx, paletteX, paletteY, paletteW, paletteH, 0xFF3E6B84);
        List<String> used = usedItems();
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 21 + (i / paletteColumns()) * 24;
            boolean hovered = mx >= x && mx < x + 20 && my >= y && my < y + 20;
            boolean empty = i == 0;
            ctx.fill(x + 1, y + 1, x + 19, y + 19,
                    hovered ? 0xFF315D7D : empty ? 0xFF242A30 : 0xFF303030);
            drawBox(ctx, x, y, 20, 20, hovered ? 0xFF78C8FF : empty ? 0xFF9A6670 : 0xFF5C6D78);
            if (empty) ctx.drawText(textRenderer, "×", x + 6, y + 5, 0xFFFF7777, false);
            else {
                var item = Registries.ITEM.get(Identifier.tryParse(used.get(i - 1)));
                if (item != null && item != Items.AIR) ctx.drawItem(new ItemStack(item), x + 2, y + 2);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
        if (itemField != null)
            drawBox(ctx, leftX(), fieldY(), leftW() - 14, 14,
                    itemField.isFocused() ? 0xFF5577DD : 0xFF404050);
        // Tooltip de l'ID complet au survol d'une suggestion
        if (!itemMatches.isEmpty()) {
            int sy = suggY();
            for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
                int ry = sy + (i - suggestionScroll) * SUGG_H;
                if (mouseX >= leftX() && mouseX < leftX() + leftW()
                        && mouseY >= ry && mouseY < ry + SUGG_H) {
                    ctx.drawOrderedTooltip(textRenderer,
                            List.of(Text.literal(itemMatches.get(i)).asOrderedText()),
                            mouseX, mouseY);
                    break;
                }
            }
        }
        List<String> used = usedItems();
        int paletteY = paletteY();
        int paletteX = rightX() + 8;
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 21 + (i / paletteColumns()) * 24;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                ctx.drawOrderedTooltip(textRenderer,
                        List.of(Text.literal(i == 0 ? "Empty - remove item" : used.get(i - 1)).asOrderedText()), mouseX, mouseY);
                break;
            }
        }
    }

    // ── mouse events ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gx = gridX(), gy = gridY();

        // Click on a grid slot
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slot = r * 3 + c;
                int sx = gx + c * SLOT, sy = gy + r * SLOT;
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                    selectSlot(slot);
                    // Conserver itemFieldText et itemSugg — l'utilisateur n'a pas à reécrire
                    restoreFocus = 1;
                    clearAndInit();
                    return true;
                }
            }
        }

        // Click on result slot
        int rx = resX(), ry = resY();
        if (mx >= rx && mx < rx + SLOT && my >= ry && my < ry + SLOT) {
            selectSlot(-1);
            // Conserver itemFieldText et itemSugg
            restoreFocus = 1;
            clearAndInit();
            return true;
        }

        // Click on text field — laisser super.mouseClicked gérer
        // pour que la sélection par drag fonctionne nativement
        if (itemField != null
                && mx >= leftX() && mx < leftX() + leftW()
                && my >= fieldY() && my < fieldY() + 14) {
            restoreFocus = 1;
            if (itemMatches.isEmpty()) {
                onItemTyped(itemFieldText);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        // Click on a suggestion
        if (!itemMatches.isEmpty()) {
            int sy = suggY(), sw = leftW();
            if (itemMatches.size() > visibleSuggestions()
                    && mx >= suggestionScrollbarX() && mx < leftX() + sw
                    && my >= sy && my < sy + visibleSuggestions() * SUGG_H) {
                draggingSuggestionScrollbar = true;
                updateSuggestionScrollbar(my);
                return true;
            }
            if (mx >= leftX() && mx < leftX() + sw
                    && my >= sy && my < sy + visibleSuggestions() * SUGG_H) {
                int idx = suggestionScroll + (int)(my - sy) / SUGG_H;
                if (idx >= 0 && idx < loadedSuggestions()) {
                    selectItem(itemMatches.get(idx));
                    return true;
                }
            }
        }

        List<String> used = usedItems();
        int paletteY = paletteY();
        int paletteX = rightX() + 8;
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 21 + (i / paletteColumns()) * 24;
            if (mx >= x && mx < x + 20 && my >= y && my < y + 20) {
                if (i == 0) selectEmptyItem();
                else selectUsedItem(used.get(i - 1));
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── save ──────────────────────────────────────────────────────────────

    private void confirm() {
        if (resultItemId.isEmpty()) return;

        boolean hasIngredient = false;
        for (String s : slotItems) if (s != null && !s.isEmpty()) { hasIngredient = true; break; }
        if (!hasIngredient) return;

        CustomRecipeEntry entry = new CustomRecipeEntry();
        entry.id = UUID.randomUUID().toString();
        // A ModMenu recipe is a local draft. Recipes created from the OP editor
        // are explicitly added to that server immediately.
        entry.server_enabled = parent.isServerManaged() ? Boolean.TRUE : Boolean.FALSE;
        entry.known_by_default = knownByDefault;
        entry.result = resultItemId;
        entry.count  = Math.max(1, resultCount);

        if (shaped) {
            entry.type = "shaped";
            // Assign a letter per unique item
            Map<String, Character> itemToChar = new LinkedHashMap<>();
            char next = 'A';
            String[] rows = new String[3];
            for (int r = 0; r < 3; r++) {
                StringBuilder row = new StringBuilder();
                for (int c = 0; c < 3; c++) {
                    String id = slotItems[r * 3 + c];
                    if (id == null || id.isEmpty()) {
                        row.append(' ');
                    } else {
                        if (!itemToChar.containsKey(id)) itemToChar.put(id, next++);
                        row.append(itemToChar.get(id));
                    }
                }
                rows[r] = row.toString();
            }
            entry.pattern = Arrays.asList(rows);
            for (Map.Entry<String, Character> kv : itemToChar.entrySet())
                entry.keys.put(String.valueOf(kv.getValue()), kv.getKey());
        } else {
            entry.type = "shapeless";
            for (String s : slotItems)
                if (s != null && !s.isEmpty()) entry.ingredients.add(s);
        }

        RecipeIntegrity.rememberRequiredMods(entry);
        parent.recipes.add(entry);
        client.setScreen(parent);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private String itemIdToFieldText(String id) {
        if (id == null || id.isEmpty()) return "";
        return toDisplayName(id).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String toDisplayName(String id) {
        String path = id.contains(":") ? id.split(":")[1] : id;
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private MultilineTextWidget makeLabel(int x, int y, String text, int color) {
        MultilineTextWidget w = new MultilineTextWidget(x, y, Text.literal(text).withColor(color), textRenderer);
        w.setMaxWidth(leftW());
        w.setMaxRows(1);
        return w;
    }

    private MultilineTextWidget makeRightLabel(int x, int y, String text, int color) {
        MultilineTextWidget w = new MultilineTextWidget(x, y, Text.literal(text).withColor(color), textRenderer);
        w.setMaxWidth(width - x - PAD);
        w.setMaxRows(1);
        return w;
    }

    private void drawBox(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.drawHorizontalLine(x, x + w - 1, y, c);
        ctx.drawHorizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.drawVerticalLine(x, y, y + h - 1, c);
        ctx.drawVerticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean shouldPause() { return false; }
}
