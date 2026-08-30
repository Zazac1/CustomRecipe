package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
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
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class RecipeBuilderScreen extends Screen {

    private static final int PAD   = 20;
    private static final int SLOT  = 20;  // slot size in pixels
    private static final int MAX_S = 15;   // max autocomplete suggestions
    private static final int SUGG_H = 16; // suggestion row height

    private final ConfigScreen parent;

    // ── persistent state (survives clearAndInit) ──────────────────────────
    private final String[] slotItems = new String[9]; // grid slots (0-8)
    private String resultItemId   = "";
    private int    resultCount    = 1;
    private boolean shaped        = false;
    private boolean knownByDefault = false;
    /** Persistently held picker item; empty string is the erase tile. */
    private String heldItemId;
    /** -2 = nothing selected, -1 = result slot, 0-8 = grid slot */
    private int selectedSlot      = -2;

    // text field state
    private String         itemFieldText = "";
    private List<String[]> itemSugg      = new ArrayList<>(); // [id, displayName]
    private int            restoreFocus  = 0; // 1 = itemField

    // rebuilt each init
    private TextFieldWidget itemField;

    public RecipeBuilderScreen(ConfigScreen parent) {
        super(Text.literal("Create a Recipe"));
        this.parent = parent;
        Arrays.fill(slotItems, "");
    }

    // ── layout helpers ────────────────────────────────────────────────────

    private int leftW()  { return Math.min(230, width / 3); }
    private int leftX()  { return PAD; }
    private int rightX() { return leftX() + leftW() + PAD; }

    /** X of the top-left slot in the 3×3 grid */
    private int gridX() {
        int available = width - rightX() - PAD;
        return rightX() + (available - 3 * SLOT - 12 - SLOT) / 2;
    }
    private int gridY()    { return 28; }
    private int resX()     { return gridX() + 3 * SLOT + 12; }
    private int resY()     { return gridY() + SLOT; }

    // left panel y positions
    private int slotLabelY() { return 10; }
    private int fieldY()     { return slotLabelY() + 12; }
    private int suggY()      { return fieldY() + 16; }

    // ── init ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Background fills + grid + result slot drawn via addDrawable
        addDrawable((ctx, mx, my, d) -> renderFills(ctx, mx, my));

        // ── Left panel ───────────────────────────────────────────────────

        // Slot label
        String slotLabel = switch (selectedSlot) {
            case -2 -> "Click a slot…";
            case -1 -> "Result item:";
            default -> "Slot " + (selectedSlot + 1) + ":";
        };
        addDrawableChild(makeLabel(leftX(), slotLabelY(), slotLabel, 0xCCCCCC));

        // Item search field
        itemField = addDrawableChild(new TextFieldWidget(
                textRenderer, leftX(), fieldY(), leftW(), 14, Text.literal("item")));
        itemField.setPlaceholder(Text.literal("Search item…"));
        itemField.setMaxLength(100);
        itemField.setText(itemFieldText);
        itemField.setChangedListener(s -> { itemFieldText = s; restoreFocus = 1; onItemTyped(s); });
        addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> {
            itemFieldText = "";
            itemField.setText("");
            setFocused(itemField);
        }).dimensions(leftX() + leftW() - 14, fieldY(), 14, 14).build());

        // Autocomplete suggestions (label widgets)
        for (int i = 0; i < itemSugg.size(); i++) {
            String[] s = itemSugg.get(i);
            int ry = suggY() + i * SUGG_H;
            addDrawableChild(makeLabel(leftX() + 20, ry + 4, shortId(s[0]), 0xCCCCCC));
        }

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
        addDrawableChild(makeRightLabel(rightX(), countY, "Result ×" + resultCount + ":", 0xCCCCCC));

        addDrawableChild(ButtonWidget.builder(Text.literal("−"),
                b -> { if (resultCount > 1) { resultCount--; clearAndInit(); } }
        ).dimensions(rightX() + 78, countY - 1, 14, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> { if (resultCount < 64) { resultCount++; clearAndInit(); } }
        ).dimensions(rightX() + 94, countY - 1, 14, 14).build());

        // Shaped / Shapeless toggle
        int modeY = countY + 18;
        addDrawableChild(ButtonWidget.builder(
                shaped ? Text.literal("Mode: Shaped").styled(s -> s.withColor(0xFFD700))
                       : Text.literal("Mode: Shapeless").styled(s -> s.withColor(0x88FFFF)),
                b -> { shaped = !shaped; clearAndInit(); }
        ).dimensions(rightX(), modeY, 110, 14).build());

        addDrawableChild(ButtonWidget.builder(
                knownByDefault ? Text.literal("Known by default: ON").styled(s -> s.withColor(0x55FF55))
                               : Text.literal("Known by default: OFF").styled(s -> s.withColor(0xFFCC55)),
                b -> { knownByDefault = !knownByDefault; clearAndInit(); }
        ).dimensions(rightX(), modeY + 18, 140, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Empty selected slot").styled(s -> s.withColor(0xFF7777)),
                b -> {
                    heldItemId = "";
                    if (selectedSlot != -2) applyHeldItemToSelectedSlot();
                    clearAndInit();
                }
        ).dimensions(rightX(), modeY + 36, 140, 14).build());

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
        if (q.isBlank()) {
            itemSugg = Registries.ITEM.getEntrySet().stream()
                    .filter(e -> e.getValue() != Items.AIR)
                    .map(e -> new String[]{
                            e.getKey().getValue().toString(),
                            toDisplayName(e.getKey().getValue().toString())
                    })
                    .sorted(Comparator.comparing(s -> s[1]))
                    .limit(MAX_S)
                    .collect(Collectors.toList());
        } else {
            String ql = q.toLowerCase(Locale.ROOT);
            itemSugg = new ArrayList<>();
            for (var entry : Registries.ITEM.getEntrySet()) {
                if (entry.getValue() == Items.AIR) continue;
                String id = entry.getKey().getValue().toString();
                String path = entry.getKey().getValue().getPath();
                if (id.contains(ql) || path.contains(ql)) {
                    itemSugg.add(new String[]{id, toDisplayName(id)});
                    if (itemSugg.size() >= MAX_S) break;
                }
            }
        }
        clearAndInit();
    }

    private void selectItem(String[] s) {
        heldItemId = s[0];
        applyNewHeldItemToSelectedSlot();
        itemFieldText = s[1].toLowerCase(Locale.ROOT).replace(' ', '_');
        itemSugg      = new ArrayList<>();
        clearAndInit();
    }

    /** Never replace the previous non-empty selected slot while changing the picker item. */
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

    // ── rendering ─────────────────────────────────────────────────────────

    private void renderFills(DrawContext ctx, int mx, int my) {
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
        if (!itemSugg.isEmpty()) {
            int sy = suggY(), sh = itemSugg.size() * SUGG_H;
            ctx.fill(leftX(), sy, leftX() + leftW(), sy + sh, 0xFF1A1C28);
            drawBox(ctx, leftX(), sy, leftW(), sh, 0xFF4A5578);
            for (int i = 0; i < itemSugg.size(); i++) {
                int ry2 = sy + i * SUGG_H;
                if (mx >= leftX() && mx < leftX() + leftW() && my >= ry2 && my < ry2 + SUGG_H)
                    ctx.fill(leftX() + 1, ry2, leftX() + leftW() - 1, ry2 + SUGG_H, 0x553355BB);
                var item2 = Registries.ITEM.get(Identifier.tryParse(itemSugg.get(i)[0]));
                if (item2 != null && item2 != Items.AIR)
                    ctx.drawItem(new ItemStack(item2), leftX() + 2, ry2);
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
        if (itemField != null)
            drawBox(ctx, leftX(), fieldY(), leftW(), 14,
                    itemField.isFocused() ? 0xFF5577DD : 0xFF404050);
        // Tooltip de l'ID complet au survol d'une suggestion
        if (!itemSugg.isEmpty()) {
            int sy = suggY();
            for (int i = 0; i < itemSugg.size(); i++) {
                int ry = sy + i * SUGG_H;
                if (mouseX >= leftX() && mouseX < leftX() + leftW()
                        && mouseY >= ry && mouseY < ry + SUGG_H) {
                    ctx.drawOrderedTooltip(textRenderer,
                            List.of(Text.literal(itemSugg.get(i)[0]).asOrderedText()),
                            mouseX, mouseY);
                    break;
                }
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
                    selectedSlot = slot;
                    applyHeldItemToSelectedSlot();
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
            selectedSlot = -1;
            applyHeldItemToSelectedSlot();
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
            return super.mouseClicked(mx, my, button);
        }

        // Click on a suggestion
        if (!itemSugg.isEmpty()) {
            int sy = suggY(), sw = leftW();
            if (mx >= leftX() && mx < leftX() + sw
                    && my >= sy && my < sy + itemSugg.size() * SUGG_H) {
                int idx = (int)(my - sy) / SUGG_H;
                if (idx >= 0 && idx < itemSugg.size()) {
                    selectItem(itemSugg.get(idx));
                    return true;
                }
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
        entry.server_enabled = parent.isServerManaged() ? Boolean.TRUE : null;
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
        return id;
    }

    private MultilineTextWidget makeLabel(int x, int y, String text, int color) {
        MultilineTextWidget w = new MultilineTextWidget(x, y, Text.literal(text).styled(s -> s.withColor(color)), textRenderer);
        w.setMaxWidth(leftW());
        w.setMaxRows(1);
        return w;
    }

    private MultilineTextWidget makeRightLabel(int x, int y, String text, int color) {
        MultilineTextWidget w = new MultilineTextWidget(x, y, Text.literal(text).styled(s -> s.withColor(color)), textRenderer);
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
