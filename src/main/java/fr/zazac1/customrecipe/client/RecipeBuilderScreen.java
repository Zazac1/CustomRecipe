package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.RecipeIntegrity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

@Environment(EnvType.CLIENT)
public class RecipeBuilderScreen extends Screen {

    private static final int PAD   = 20;
    private static final int SLOT  = 18;
    private static final int OUTPUT_SLOT = 26;
    private static final int ITEM_BATCH_SIZE = 20;
    private static final int VISIBLE_S = 10;
    private static final int SUGG_H = 16; // suggestion row height
    private static final int SEARCH_H = 18;
    private static final int SEARCH_TEXT_Y_OFFSET = 6;
    private static final int SHAPELESS_ROW_H = 26;
    private static final int MAX_SHAPELESS_INGREDIENTS = 9;
    private static final int VISIBLE_SHAPELESS_ROWS = 5;

    private final ConfigScreen parent;
    /** Exact screen that opened this editor, retained for Cancel, Escape and Save. */
    private final Screen returnTo;
    /** Index replaced on confirmation, or -1 for a new/duplicated recipe. */
    private final int editingIndex;
    private final CustomRecipeEntry editingEntry;

    // ── persistent state (survives clearAndInit) ──────────────────────────
    private final String[] slotItems = new String[9]; // grid slots (0-8)
    /** Ordered ingredient slots used only while the recipe is shapeless. */
    private final List<String> shapelessItems = new ArrayList<>();
    private String resultItemId   = "";
    private int    resultCount    = 1;
    private TextFieldWidget resultCountField;
    private boolean shaped        = true;
    private boolean knownByDefault = false;
    /** Optional second destination when creating directly in a world. */
    private boolean alsoSaveToLibrary = true;
    /** State when this draft was opened, used to protect Escape from losing it. */
    private String initialDraft;
    /** -2 = nothing selected, -1 = result slot, 0-8 = grid slot */
    private int selectedSlot      = -2;
    /** Selected ingredient row in the shapeless list, or -1. */
    private int selectedShapelessSlot = -1;
    private int shapelessScroll;
    /** null = no item picked; empty string = the permanent empty/erase item. */
    private String heldItemId;

    // text field state
    private String         itemFieldText = "";
    /** Matching item IDs; stacks are only created for rows currently visible. */
    private List<String> itemMatches      = new ArrayList<>();
    private int            suggestionScroll;
    private int            loadedSuggestionLimit = ITEM_BATCH_SIZE;
    private boolean        draggingSuggestionScrollbar;
    private int            restoreFocus  = 0; // 1 = itemField
    /** Avoid rebuilding the screen from the clear button's programmatic update. */
    private boolean        suppressItemFieldChange;

    // rebuilt each init
    private TextFieldWidget itemField;

    public RecipeBuilderScreen(ConfigScreen parent) {
        this(parent, parent, null, -1);
    }

    public RecipeBuilderScreen(ConfigScreen parent, CustomRecipeEntry source, int editingIndex) {
        this(parent, parent, source, editingIndex);
    }

    RecipeBuilderScreen(ConfigScreen parent, Screen returnTo, CustomRecipeEntry source, int editingIndex) {
        super(Text.translatable(editingIndex >= 0 ? "customrecipe.builder.edit" : "customrecipe.builder.create"));
        this.parent = parent;
        this.returnTo = returnTo;
        this.editingIndex = editingIndex;
        this.editingEntry = editingIndex >= 0 ? source : null;
        Arrays.fill(slotItems, "");
        if (source == null) {
            initialDraft = draftFingerprint();
            return;
        }

        shaped = "shaped".equalsIgnoreCase(source.type);
        knownByDefault = Boolean.TRUE.equals(source.known_by_default);
        resultItemId = source.result == null ? "" : source.result;
        resultCount = Math.max(1, Math.min(64, source.count));
        if (!shaped) {
            if (source.ingredients != null) {
                for (String itemId : source.ingredients) {
                    if (itemId != null && !itemId.isEmpty() && shapelessItems.size() < MAX_SHAPELESS_INGREDIENTS)
                        shapelessItems.add(itemId);
                }
            }
            initialDraft = draftFingerprint();
            return;
        }
        if (source.pattern == null || source.keys == null) return;
        for (int row = 0; row < 3 && row < source.pattern.size(); row++) {
            String patternRow = source.pattern.get(row);
            if (patternRow == null) continue;
            for (int column = 0; column < 3 && column < patternRow.length(); column++) {
                char key = patternRow.charAt(column);
                if (key != ' ') slotItems[row * 3 + column] = source.keys.getOrDefault(String.valueOf(key), "");
            }
        }
        initialDraft = draftFingerprint();
    }

    // ── layout helpers ────────────────────────────────────────────────────

    private int leftW()  { return Math.min(280, Math.max(210, width / 3)); }
    private int leftX()  { return PAD; }
    private int rightX() { return leftX() + leftW() + PAD; }
    private int rightW() { return width - rightX() - PAD; }
    private int panelY() { return 32; }
    private int workspaceH() { return paletteY() + paletteH() - panelY() + 12; }

    /** X of the top-left slot in the 3×3 grid */
    private int craftingTableX() { return rightX() + 8; }
    private int craftingTableY() { return panelY() + 86; }
    private int gridX() { return shaped ? craftingTableX() + 29 : rightX() + (rightW() - 116) / 2; }
    private int gridY() { return shaped ? craftingTableY() + 16 : panelY() + 30; }
    private int resX()  { return shaped ? craftingTableX() + 119 : shapelessListX() + shapelessListW() + 36; }
    private int resY()     {
        if (shaped) return gridY() + 14;
        return shapelessListY() + Math.max(0, (visibleShapelessRows() * SHAPELESS_ROW_H - OUTPUT_SLOT) / 2);
    }
    private int settingsX() { return shaped ? craftingTableX() + 34 : shapelessListX() + 34; }
    private int shapelessListW() { return 176; }
    private int shapelessListX() { return rightX() + 8; }
    private int shapelessHeaderY() { return panelY() + 62; }
    private int shapelessListY() { return panelY() + 86; }
    private int shapelessRowCount() { return Math.max(1, shapelessRows().size()); }
    private int visibleShapelessRows() { return Math.min(VISIBLE_SHAPELESS_ROWS, shapelessRowCount()); }
    private int maxShapelessScroll() { return Math.max(0, shapelessRowCount() - VISIBLE_SHAPELESS_ROWS); }
    private int shapelessArrowX() { return shapelessListX() + shapelessListW() / 2 - 8; }
    private int shapelessUpArrowX() { return shapelessListX() + shapelessListW() / 2 - 24; }
    private int shapelessDownArrowY() { return shapelessListY() + visibleShapelessRows() * SHAPELESS_ROW_H - 18; }
    private int shapelessUpArrowY() { return shapelessListY() - 14; }
    private int shapelessIngredientCount() {
        return (int) shapelessItems.stream().filter(item -> item != null && !item.isEmpty()).count();
    }
    private int shapedIngredientCount() {
        return (int) Arrays.stream(slotItems).filter(item -> item != null && !item.isEmpty()).count();
    }
    private int recipeBottom() { return shaped ? craftingTableY() + 86 : shapelessListY() + visibleShapelessRows() * SHAPELESS_ROW_H; }
    private int countY() { return recipeBottom() + 10; }
    private int modeY() { return countY() + 18; }
    private int knownY() { return modeY() + 24; }

    // left panel y positions
    private int slotLabelY() { return panelY() + 8; }
    private int fieldY()     { return slotLabelY() + 14; }
    // Suggestions start directly below the search field: one continuous picker.
    private int suggY()      { return fieldY() + SEARCH_H; }
    private int paletteY() {
        // In shapeless mode, let new ingredient rows use the empty workspace first.
        // The reusable-item strip only moves once the result controls reach it.
        return Math.max(panelY() + 174, countY() + 24);
    }
    private int loadedSuggestions() { return Math.min(loadedSuggestionLimit, itemMatches.size()); }
    private int visibleSuggestions() { return Math.min(VISIBLE_S, Math.max(0, loadedSuggestions() - suggestionScroll)); }
    private int suggestionScrollbarX() { return leftX() + leftW() - 12; }
    private int paletteColumns() { return Math.max(8, Math.min(16, (rightW() - 16) / 24)); }
    private int paletteRows() { return Math.max(1, (usedItems().size() + 1 + paletteColumns() - 1) / paletteColumns()); }
    private int paletteH()   { return paletteRows() * 24 + 8; }
    private int paletteW()   { return rightW() - 16; }
    private int paletteX()   { return rightX() + 8; }

    // ── init ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        addDrawable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, client, parent.target(),
                parent.target().isWorld() ? parent.target().displayName() : "Global Library"));
        shapelessScroll = Math.max(0, Math.min(maxShapelessScroll(), shapelessScroll));
        if (itemFieldText.isBlank() && itemMatches.isEmpty()) itemMatches = findItemMatches("");

        // Background fills + grid + result slot drawn via addDrawable
        addDrawable((ctx, mx, my, d) -> renderFills(ctx, mx, my));
        addDrawableChild(makeRightLabel(rightX() + 8, panelY() + 8, "Recipe preview", 0xFFEECC77));
        if (shaped) {
            addDrawableChild(makeRightLabel(rightX() + 8, shapelessHeaderY(),
                    "Shaped ingredients (" + shapedIngredientCount() + "/9)", 0xFF8FC7E8));
        }

        // ── Left panel ───────────────────────────────────────────────────

        // Slot label
        String slotLabel = selectedShapelessSlot >= 0 ? "Ingredient " + (selectedShapelessSlot + 1) + ":" : switch (selectedSlot) {
            case -2 -> "Click a slot…";
            case -1 -> "Result item:";
            default -> "Slot " + (selectedSlot + 1) + ":";
        };
        addDrawableChild(makeLabel(leftX() + 8, slotLabelY(), slotLabel, 0xCCCCCC));

        // Item search field
        itemField = addDrawableChild(new TextFieldWidget(
                textRenderer, leftX() + 4, fieldY() + SEARCH_TEXT_Y_OFFSET,
                leftW() - 22, SEARCH_H, Text.literal("item")));
        // The whole search-and-clear control already has one shared outline in render().
        // Do not render a second TextField border beside the × button.
        itemField.setDrawsBackground(false);
        itemField.setPlaceholder(Text.translatable("customrecipe.builder.search_item"));
        itemField.setMaxLength(100);
        itemField.setText(itemFieldText);
        itemField.setChangedListener(s -> {
            itemFieldText = s;
            if (!suppressItemFieldChange) {
                restoreFocus = 1;
                onItemTyped(s);
            }
        });
        int clearSearchX = leftX() + leftW() - 18;
        addDrawableChild(ButtonWidget.builder(Text.empty(), b -> clearItemSearch())
                .dimensions(clearSearchX, fieldY(), 18, SEARCH_H).build());
        addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                CustomRecipeSprites.REJECT, clearSearchX, fieldY(), 18, 18));

        // Autocomplete suggestions (label widgets)
        for (int i = suggestionScroll; i < suggestionScroll + visibleSuggestions(); i++) {
            String id = itemMatches.get(i);
            int ry = suggY() + (i - suggestionScroll) * SUGG_H;
            MultilineTextWidget suggestionLabel = makeLabel(leftX() + 22, ry + 4, id, 0xCCCCCC);
            // Leave five characters of safety before the scrollbar instead of clipping the ellipsis.
            suggestionLabel.setMaxWidth(leftW() - 30);
            addDrawableChild(suggestionLabel);
        }

        // ── Right panel ──────────────────────────────────────────────────

        if (!shaped) {
            addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                    CustomRecipeSprites.OUTPUT_ARROW,
                    resX() - 29, resY() + 5, 22, 15));
        }

        // Result count: type a stack size directly instead of clicking through 64 values.
        int countY = countY();
        addDrawableChild(makeRightLabel(settingsX(), countY,
                Text.translatable("customrecipe.builder.result_count").getString(), 0xCCCCCC));
        resultCountField = addDrawableChild(new TextFieldWidget(textRenderer, settingsX() + 70, countY - 2,
                34, 18, Text.translatable("customrecipe.builder.result_count")));
        resultCountField.setMaxLength(2);
        resultCountField.setTextPredicate(value -> value.isEmpty() || (value.matches("\\d{1,2}")
                && Integer.parseInt(value) >= 1 && Integer.parseInt(value) <= 64));
        resultCountField.setText(String.valueOf(resultCount));
        resultCountField.setChangedListener(value -> {
            if (!value.isEmpty()) resultCount = Integer.parseInt(value);
        });

        // Shaped / Shapeless toggle
        int modeX = rightX() + 8;
        int modeY = panelY() + 28;
        addDrawableChild(ButtonWidget.builder(Text.empty(),
                b -> { toggleShapeMode(); clearAndInit(); }
        ).dimensions(modeX, modeY, 20, 20).build());
        addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                shaped ? CustomRecipeSprites.LOCKED_BUTTON : CustomRecipeSprites.UNLOCKED_BUTTON,
                modeX, modeY, 20, 20));
        addDrawableChild(makeRightLabel(modeX + 24, modeY + 6,
                shaped ? "Shaped" : "Shapeless", shaped ? 0xFFD700 : 0x88FFFF));

        int knownX = modeX + 32 + textRenderer.getWidth("Shapeless");
        int knownY = modeY;
        addDrawableChild(ButtonWidget.builder(Text.empty(),
                b -> { knownByDefault = !knownByDefault; clearAndInit(); }
        ).dimensions(knownX, knownY, 20, 20).build());
        addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                knownByDefault ? CustomRecipeSprites.KNOWN_BY_DEFAULT : CustomRecipeSprites.NOT_KNOWN_BY_DEFAULT,
                knownX, knownY, 20, 20));
        addDrawableChild(makeRightLabel(knownX + 24, knownY + 6,
                "Known by default: " + (knownByDefault ? "ON" : "OFF"),
                knownByDefault ? 0xFF55FF55 : 0xFFFF7777));

        if (!shaped) addShapelessListControls();

        if (parent.target().isWorld()) {
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("customrecipe.builder.also_library", Text.translatable(alsoSaveToLibrary ? "customrecipe.recipe.on" : "customrecipe.recipe.off")),
                    b -> { alsoSaveToLibrary = !alsoSaveToLibrary; clearAndInit(); }
            ).dimensions(width / 2 - 110, height - 46, 220, 18).build());
        }

        // ── Bottom buttons ────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.cancel"),
                b -> client.setScreen(returnTo)
        ).dimensions(width / 2 - 102, height - 22, 98, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable(editingIndex >= 0
                        ? "customrecipe.builder.save" : "customrecipe.button.add_recipe"),
                b -> confirm()
        ).dimensions(width / 2 + 4, height - 22, 98, 18).build());
        addDrawable((ctx, mx, my, d) -> {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, width / 2 - 98, height - 22, 18, 18);
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ACCEPT, width / 2 + 8, height - 22, 18, 18);
        });

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

    /** Clears immediately on the first click, without selecting the old query. */
    private void clearItemSearch() {
        itemFieldText = "";
        itemMatches = findItemMatches("");
        suggestionScroll = 0;
        loadedSuggestionLimit = ITEM_BATCH_SIZE;
        restoreFocus = 0;
        suppressItemFieldChange = true;
        itemField.setText("");
        suppressItemFieldChange = false;
        setFocused(null);
        clearAndInit();
    }

    private List<String> findItemMatches(String q) {
        String ql = q.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (var entry : Registries.ITEM.getEntrySet()) {
            if (entry.getValue() == Items.AIR) continue;
            String id = entry.getKey().getValue().toString();
            String path = entry.getKey().getValue().getPath();
            if (id.contains(ql) || path.contains(ql)) {
                matches.add(id);
            }
        }
        matches.sort(String::compareTo);
        return matches;
    }

    private void selectItem(String itemId) {
        heldItemId = itemId;
        itemFieldText = shortId(itemId);
        itemMatches   = new ArrayList<>();
        suggestionScroll = 0;
        restoreFocus = 0;
        setFocused(null);
        applyNewHeldItemToSelectedSlot();
        clearAndInit();
    }

    private void selectEmptyItem() {
        heldItemId = "";
        itemFieldText = "";
        itemMatches = new ArrayList<>();
        suggestionScroll = 0;
        restoreFocus = 0;
        setFocused(null);
        applyNewHeldItemToSelectedSlot();
        clearAndInit();
    }

    /**
     * A search result may change the held item, but never silently replace the
     * item in the last slot used. Only an empty selected slot is filled here.
     */
    private void applyNewHeldItemToSelectedSlot() {
        if (selectedShapelessSlot >= 0) {
            if (selectedShapelessSlot < shapelessItems.size()
                    && shapelessItems.get(selectedShapelessSlot).isEmpty()) {
                applyHeldItemToSelectedSlot();
            } else {
                selectedShapelessSlot = -1;
            }
            return;
        }
        if (selectedSlot == -2) return;
        if (selectedSlotIsEmpty()) {
            applyHeldItemToSelectedSlot();
        } else {
            selectedSlot = -2;
        }
    }

    private boolean selectedSlotIsEmpty() {
        if (selectedShapelessSlot >= 0)
            return selectedShapelessSlot < shapelessItems.size() && shapelessItems.get(selectedShapelessSlot).isEmpty();
        if (selectedSlot == -1) return resultItemId.isEmpty();
        return selectedSlot >= 0 && selectedSlot < 9 && slotItems[selectedSlot].isEmpty();
    }

    private void applyHeldItemToSelectedSlot() {
        if (heldItemId == null) return;
        if (selectedShapelessSlot >= 0 && selectedShapelessSlot < shapelessItems.size()) {
            shapelessItems.set(selectedShapelessSlot, heldItemId);
            // Shapeless rows are one-shot targets: after placing an item, the
            // row returns to its normal, non-selected state.
            selectedShapelessSlot = -1;
        } else if (selectedSlot == -1) {
            resultItemId = heldItemId;
        } else if (selectedSlot >= 0 && selectedSlot < 9) {
            slotItems[selectedSlot] = heldItemId;
        }
    }

    private void selectSlot(int slot) {
        // The erase cursor clears the clicked slot, then returns to neutral.
        // Keeping it selected made the next picked item unexpectedly replace it.
        if (heldItemId != null && heldItemId.isEmpty()) {
            if (slot == -1) {
                resultItemId = "";
            } else if (slot >= 0 && slot < 9) {
                slotItems[slot] = "";
            }
            selectedSlot = -2;
            restoreFocus = 0;
            clearAndInit();
            return;
        }
        selectedSlot = slot;
        selectedShapelessSlot = -1;
        applyHeldItemToSelectedSlot();
        restoreFocus = 1;
        clearAndInit();
    }

    /** Unique items already present in the draft, ordered result first. */
    private List<String> usedItems() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (resultItemId != null && !resultItemId.isBlank()) items.add(resultItemId);
        for (String item : slotItems) {
            if (item != null && !item.isBlank()) items.add(item);
        }
        for (String item : shapelessItems) {
            if (item != null && !item.isBlank()) items.add(item);
        }
        return new ArrayList<>(items);
    }

    private void selectUsedItem(String itemId) {
        selectItem(itemId);
    }

    private void selectShapelessSlot(int slot) {
        if (heldItemId != null && heldItemId.isEmpty()) {
            shapelessItems.set(slot, "");
            selectedShapelessSlot = -1;
            selectedSlot = -2;
            clearAndInit();
            return;
        }
        selectedSlot = -2;
        selectedShapelessSlot = slot;
        applyHeldItemToSelectedSlot();
        restoreFocus = 1;
        clearAndInit();
    }

    private void addShapelessSlot() {
        if (shapelessItems.size() < MAX_SHAPELESS_INGREDIENTS) shapelessItems.add("");
        selectedShapelessSlot = -1;
        clearAndInit();
    }

    private void removeShapelessSlot(int slot) {
        if (shapelessItems.size() > 1) shapelessItems.remove(slot);
        else shapelessItems.set(0, "");
        selectedShapelessSlot = -1;
        clearAndInit();
    }

    private void clearFirstShapelessSlot() {
        shapelessItems.set(0, "");
        selectedShapelessSlot = -1;
        clearAndInit();
    }

    private List<ShapelessRow> shapelessRows() {
        Map<String, ShapelessRow> grouped = new LinkedHashMap<>();
        List<ShapelessRow> rows = new ArrayList<>();
        for (int i = 0; i < shapelessItems.size(); i++) {
            String itemId = shapelessItems.get(i);
            if (itemId == null || itemId.isEmpty()) {
                rows.add(new ShapelessRow("", i, 0));
                continue;
            }
            ShapelessRow previous = grouped.get(itemId);
            if (previous == null) {
                ShapelessRow row = new ShapelessRow(itemId, i, 1);
                grouped.put(itemId, row);
                rows.add(row);
            } else {
                ShapelessRow row = new ShapelessRow(itemId, previous.slotIndex(), previous.count() + 1);
                grouped.put(itemId, row);
                rows.set(rows.indexOf(previous), row);
            }
        }
        return rows;
    }

    private void changeShapelessCount(String itemId, int change) {
        if (change > 0 && shapelessItems.size() < MAX_SHAPELESS_INGREDIENTS) {
            shapelessItems.add(itemId);
        } else if (change < 0) {
            shapelessItems.remove(itemId);
            if (shapelessItems.isEmpty()) shapelessItems.add("");
        }
        selectedShapelessSlot = -1;
        clearAndInit();
    }

    private void removeShapelessItem(String itemId) {
        shapelessItems.removeIf(itemId::equals);
        if (shapelessItems.isEmpty()) shapelessItems.add("");
        selectedShapelessSlot = -1;
        clearAndInit();
    }

    private record ShapelessRow(String itemId, int slotIndex, int count) {}

    private void toggleShapeMode() {
        if (shaped) {
            shapelessItems.clear();
            for (String itemId : slotItems)
                if (itemId != null && !itemId.isBlank()) shapelessItems.add(itemId);
            if (shapelessItems.isEmpty()) shapelessItems.add("");
        }
        shaped = !shaped;
        selectedSlot = -2;
        selectedShapelessSlot = -1;
        shapelessScroll = 0;
        heldItemId = null;
    }

    private void addShapelessListControls() {
        int x = shapelessListX();
        int removeX = x + shapelessListW() - 22;
        int plusX = removeX - 16;
        int minusX = plusX - 16;
        List<ShapelessRow> rows = shapelessRows();
        for (int i = shapelessScroll; i < Math.min(rows.size(), shapelessScroll + VISIBLE_SHAPELESS_ROWS); i++) {
            final int rowIndex = i;
            ShapelessRow row = rows.get(i);
            int y = shapelessListY() + (i - shapelessScroll) * SHAPELESS_ROW_H;
            if (!row.itemId().isEmpty()) {
                addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> changeShapelessCount(row.itemId(), -1))
                        .dimensions(minusX, y + 4, 14, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeShapelessCount(row.itemId(), 1))
                        .dimensions(plusX, y + 4, 14, 18).build());
            }
            boolean canRemove = i > 0 || (!row.itemId().isEmpty() && rows.size() == 1);
            if (canRemove) {
                addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    if (rowIndex == 0 && rows.size() == 1) clearFirstShapelessSlot();
                    else if (row.itemId().isEmpty()) removeShapelessSlot(row.slotIndex());
                    else removeShapelessItem(row.itemId());
                }).dimensions(removeX, y + 4, 18, 18).build());
                addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                        CustomRecipeSprites.REJECT, removeX, y + 4, 18, 18));
            }
        }
        if (shapelessItems.size() < MAX_SHAPELESS_INGREDIENTS) {
            int addY = shapelessListY() + visibleShapelessRows() * SHAPELESS_ROW_H + 4;
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> addShapelessSlot())
                    .dimensions(x + 4, addY, 18, 18).build());
        }
    }

    // ── rendering ─────────────────────────────────────────────────────────

    private void renderFills(DrawContext ctx, int mx, int my) {
        // Keep the list border flush with the panel border; the old extra six
        // pixels left an empty strip below the final visible item.
        int suggestionsH = itemMatches.isEmpty() ? 0 : visibleSuggestions() * SUGG_H + 1;
        int leftPanelH = Math.max(70, suggY() + suggestionsH - panelY());
        drawPanel(ctx, leftX(), panelY(), leftW(), leftPanelH, 0xAA141A24, 0xFF40506A);
        drawPanel(ctx, rightX(), panelY(), rightW(), workspaceH(), 0xAA141A24, 0xFF5B5A45);

        int gx = gridX(), gy = gridY();

        // 3×3 grid slots
        if (shaped) {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.CRAFTING_TABLE,
                    craftingTableX(), craftingTableY(), 176, 86);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slot = r * 3 + c;
                    int sx = gx + c * SLOT, sy = gy + r * SLOT;
                        if (selectedSlot == slot || (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT))
                            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SLOT_SELECTED, sx, sy, SLOT, SLOT);
                    String itemId = slotItems[slot];
                    if (itemId != null && !itemId.isEmpty()) {
                        var item = Registries.ITEM.get(Identifier.tryParse(itemId));
                        if (item != null && item != Items.AIR)
                            ctx.drawItem(new ItemStack(item), sx + 1, sy + 1);
                    }
                }
            }
        } else {
            renderShapelessIngredientList(ctx, mx, my);
        }

        // Result slot
        int rx = resX(), ry = resY();
        boolean outputSelected = selectedSlot == -1
                || (mx >= rx && mx < rx + OUTPUT_SLOT && my >= ry && my < ry + OUTPUT_SLOT);
        if (!shaped || outputSelected) {
            CustomRecipeSprites.draw(ctx,
                    outputSelected ? CustomRecipeSprites.OUTPUT_SELECTED : CustomRecipeSprites.OUTPUT,
                    rx, ry, OUTPUT_SLOT, OUTPUT_SLOT);
        }
        if (!resultItemId.isEmpty()) {
            var item = Registries.ITEM.get(Identifier.tryParse(resultItemId));
            if (item != null && item != Items.AIR)
                ctx.drawItem(new ItemStack(item), rx + 5, ry + 5);
        }

        // Suggestion list background + icons
        if (!itemMatches.isEmpty()) {
            // One bottom padding pixel keeps the final 16×16 item icon above the border.
            int sy = suggY(), sh = visibleSuggestions() * SUGG_H + 1;
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
                int thumbH = 15;
                int maxScroll = Math.max(1, itemMatches.size() - visibleSuggestions());
                int thumbY = sy + (sh - thumbH) * suggestionScroll / maxScroll;
                CustomRecipeSprites.draw(ctx,
                        draggingSuggestionScrollbar ? CustomRecipeSprites.SCROLLER_ACTIVE : CustomRecipeSprites.SCROLLER_IDLE,
                        scrollbarX, thumbY, 12, 15);
            }
        }

        int paletteY = paletteY();
        int paletteH = paletteH();
        int paletteX = paletteX();
        int paletteW = paletteW();
        ctx.fill(paletteX + 1, paletteY + 1, paletteX + paletteW - 1, paletteY + paletteH - 1, 0xEE25313A);
        drawBox(ctx, paletteX, paletteY, paletteW, paletteH, 0xFF3E6B84);
        // Reusable items only: the selected item already follows the cursor.
        List<String> used = usedItems();
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 6 + (i / paletteColumns()) * 24;
            boolean hovered = mx >= x && mx < x + 20 && my >= y && my < y + 20;
            boolean empty = i == 0;
            ctx.fill(x + 1, y + 1, x + 19, y + 19,
                    hovered ? 0xFF315D7D : empty ? 0xFF242A30 : 0xFF303030);
            drawBox(ctx, x, y, 20, 20, hovered ? 0xFF78C8FF : empty ? 0xFF9A6670 : 0xFF5C6D78);
            if (empty) {
                CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, x + 1, y + 1, 18, 18);
            } else {
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
            drawBox(ctx, leftX(), fieldY(), leftW(), SEARCH_H,
                    itemField.isFocused() ? 0xFF5577DD : 0xFF404050);

        // Show the held item under the cursor while positioning it in the recipe preview.
        if (heldItemId != null
                && mouseX >= rightX() && mouseX < rightX() + rightW()
                && mouseY >= panelY() && mouseY < panelY() + workspaceH()) {
            int ghostX = mouseX - 8;
            int ghostY = mouseY - 8;
            if (heldItemId.isEmpty()) {
                CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, ghostX - 1, ghostY - 1, 18, 18);
            } else {
                var heldItem = Registries.ITEM.get(Identifier.tryParse(heldItemId));
                if (heldItem != null && heldItem != Items.AIR) {
                ctx.drawItem(new ItemStack(heldItem), ghostX, ghostY);
                // The 1.21.11 GUI item pipeline has no alpha overload. Its
                // translucent veil keeps the cursor item at 80% visual weight.
                ctx.fill(ghostX, ghostY, ghostX + 16, ghostY + 16, 0x33000000);
                }
            }
        }

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
        int paletteX = paletteX();
        for (int i = 0; i <= used.size(); i++) {
            int x = paletteX + 8 + (i % paletteColumns()) * 24;
            int y = paletteY + 6 + (i / paletteColumns()) * 24;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                ctx.drawOrderedTooltip(textRenderer,
                        List.of(i == 0 ? Text.translatable("customrecipe.builder.empty_remove").asOrderedText() : Text.literal(used.get(i - 1)).asOrderedText()), mouseX, mouseY);
                break;
            }
        }

    }

    // ── mouse events ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean focused) {
        double mx = click.x(), my = click.y();
        // Right-clicking the preview area cancels the item currently carried
        // by the cursor; it never edits a recipe slot.
        if (click.button() == 1
                && mx >= rightX() && mx < rightX() + rightW()
                && my >= panelY() && my < panelY() + workspaceH()) {
            heldItemId = null;
            selectedSlot = -2;
            selectedShapelessSlot = -1;
            clearAndInit();
            return true;
        }
        int gx = gridX(), gy = gridY();

        // Click on a grid slot (shaped recipes only).
        if (shaped) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slot = r * 3 + c;
                    int sx = gx + c * SLOT, sy = gy + r * SLOT;
                    if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                        selectSlot(slot);
                        return true;
                    }
                }
            }
        }

        if (!shaped) {
            int listX = shapelessListX();
            List<ShapelessRow> rows = shapelessRows();
            if (rows.size() > VISIBLE_SHAPELESS_ROWS) {
                int arrowX = shapelessArrowX();
                if (shapelessScroll > 0
                        && mx >= shapelessUpArrowX() && mx < shapelessUpArrowX() + 32
                        && my >= shapelessUpArrowY() && my < shapelessUpArrowY() + 32) {
                    shapelessScroll--;
                    clearAndInit();
                    return true;
                }
                if (shapelessScroll < maxShapelessScroll()
                        && mx >= arrowX && mx < arrowX + 32
                        && my >= shapelessDownArrowY() && my < shapelessDownArrowY() + 32) {
                    shapelessScroll++;
                    clearAndInit();
                    return true;
                }
            }
            for (int i = shapelessScroll; i < Math.min(rows.size(), shapelessScroll + VISIBLE_SHAPELESS_ROWS); i++) {
                int slotY = shapelessListY() + (i - shapelessScroll) * SHAPELESS_ROW_H;
                if (mx >= listX + 4 && mx < listX + 4 + SLOT
                        && my >= slotY + 4 && my < slotY + 4 + SLOT) {
                    selectShapelessSlot(rows.get(i).slotIndex());
                    return true;
                }
            }
        }

        // Click on result slot
        int rx = resX(), ry = resY();
        if (mx >= rx && mx < rx + OUTPUT_SLOT && my >= ry && my < ry + OUTPUT_SLOT) {
            selectSlot(-1);
            return true;
        }

        // Click on text field — laisser super.mouseClicked gérer
        // pour que la sélection par drag fonctionne nativement
        if (itemField != null
                && mx >= leftX() && mx < leftX() + leftW() - 18
                && my >= fieldY() && my < fieldY() + SEARCH_H) {
            // The first click after choosing an item rebuilds suggestions. Keep
            // focus through that rebuild so it also selects the field immediately.
            setFocused(itemField);
            if (itemMatches.isEmpty()) {
                restoreFocus = 1;
                onItemTyped(itemFieldText);
                return true;
            }
            // Dispatch directly: Screen's child traversal could miss this first
            // click after the search field was rebuilt.
            itemField.mouseClicked(click, focused);
            return true;
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

        {
            List<String> used = usedItems();
            int paletteY = paletteY();
            int paletteX = paletteX();
            for (int i = 0; i <= used.size(); i++) {
                int x = paletteX + 8 + (i % paletteColumns()) * 24;
                int y = paletteY + 6 + (i / paletteColumns()) * 24;
                if (mx >= x && mx < x + 20 && my >= y && my < y + 20) {
                    if (i == 0) selectEmptyItem();
                    else selectUsedItem(used.get(i - 1));
                    return true;
                }
            }
        }

        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (!shaped && shapelessRowCount() > VISIBLE_SHAPELESS_ROWS
                && mx >= shapelessListX() && mx < shapelessListX() + shapelessListW()
                && my >= shapelessListY() && my < shapelessListY() + visibleShapelessRows() * SHAPELESS_ROW_H) {
            int direction = -(int) Math.signum(verticalAmount);
            int next = Math.max(0, Math.min(maxShapelessScroll(), shapelessScroll + direction));
            if (next != shapelessScroll) {
                shapelessScroll = next;
                clearAndInit();
            }
            return true;
        }
        int sy = suggY();
        if (itemMatches.size() > visibleSuggestions()
                && mx >= leftX() && mx < leftX() + leftW()
                && my >= sy && my < sy + visibleSuggestions() * SUGG_H) {
            int direction = -(int) Math.signum(verticalAmount);
            if (direction > 0 && suggestionScroll + visibleSuggestions() >= loadedSuggestions()
                    && loadedSuggestions() < itemMatches.size()) {
                loadedSuggestionLimit += ITEM_BATCH_SIZE;
            }
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
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double my = click.y();
        if (draggingSuggestionScrollbar) {
            updateSuggestionScrollbar(my);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSuggestionScrollbar = false;
        return super.mouseReleased(click);
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
    public boolean keyPressed(KeyInput key) {
        if (key.key() == 256) { // Escape
            close();
            return true;
        }
        return super.keyPressed(key);
    }

    // ── save ──────────────────────────────────────────────────────────────

    private void confirm() {
        if (resultItemId.isEmpty()) return;

        boolean hasIngredient = shaped
                ? Arrays.stream(slotItems).anyMatch(s -> s != null && !s.isEmpty())
                : shapelessItems.stream().anyMatch(s -> s != null && !s.isEmpty());
        if (!hasIngredient) return;

        CustomRecipeEntry entry = new CustomRecipeEntry();
        entry.id = editingEntry == null ? UUID.randomUUID().toString() : editingEntry.id;
        // A ModMenu recipe is a local draft. Recipes created from the OP editor
        // are explicitly added to that server immediately.
        entry.world_ids = editingEntry == null ? new ArrayList<>()
                : (editingEntry.world_ids == null ? null : new ArrayList<>(editingEntry.world_ids));
        entry.world_names = editingEntry == null ? new LinkedHashMap<>()
                : (editingEntry.world_names == null ? null : new LinkedHashMap<>(editingEntry.world_names));
        entry.server_enabled = editingEntry == null ? null : editingEntry.server_enabled;
        entry.enabled = editingEntry == null ? null : editingEntry.enabled;
        entry.quick_add = editingEntry == null ? null : editingEntry.quick_add;
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
            for (String itemId : shapelessItems)
                if (itemId != null && !itemId.isEmpty()) entry.ingredients.add(itemId);
        }

        RecipeIntegrity.rememberRequiredMods(entry);

        if (editingIndex >= 0) parent.recipes.set(editingIndex, entry);
        else parent.recipes.add(entry);
        if (alsoSaveToLibrary && editingIndex < 0) parent.alsoSaveToLibrary(entry);
        client.setScreen(returnTo);
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

    @Override
    public void close() {
        if (!draftFingerprint().equals(initialDraft)) {
            client.setScreen(new SaveChangesScreen(this, this::confirm, () -> client.setScreen(returnTo)));
        } else {
            client.setScreen(returnTo);
        }
    }

    private String draftFingerprint() {
        return shaped + "|" + resultItemId + "|" + resultCount + "|" + knownByDefault + "|" + alsoSaveToLibrary
                + "|" + Arrays.toString(slotItems) + "|" + String.join("\\u001F", shapelessItems);
    }

    private void renderShapelessIngredientList(DrawContext ctx, int mouseX, int mouseY) {
        int x = shapelessListX();
        int y = shapelessListY();
        int w = shapelessListW();
        ctx.drawText(textRenderer, Text.translatable("customrecipe.builder.shapeless_ingredients", shapelessIngredientCount()), x, shapelessHeaderY(), 0xFF8FC7E8, false);
        int removeX = x + w - 22;
        int plusX = removeX - 16;
        int minusX = plusX - 16;
        List<ShapelessRow> rows = shapelessRows();
        for (int i = shapelessScroll; i < Math.min(rows.size(), shapelessScroll + VISIBLE_SHAPELESS_ROWS); i++) {
            ShapelessRow row = rows.get(i);
            String itemId = row.itemId();
            int rowY = y + (i - shapelessScroll) * SHAPELESS_ROW_H;
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SHAPELESS_BG, x, rowY, 176, 26);
            CustomRecipeSprites.draw(ctx,
                    selectedShapelessSlot == row.slotIndex()
                            || (mouseX >= x + 4 && mouseX < x + 4 + SLOT
                            && mouseY >= rowY + 4 && mouseY < rowY + 4 + SLOT)
                            ? CustomRecipeSprites.SLOT_SELECTED : CustomRecipeSprites.SLOT,
                    x + 4, rowY + 4, SLOT, SLOT);
            ctx.drawText(textRenderer, (i + 1) + ")", x + 26, rowY + 8, 0xFF3F3F3F, false);
            if (itemId != null && !itemId.isEmpty()) {
                var item = Registries.ITEM.get(Identifier.tryParse(itemId));
                if (item != null && item != Items.AIR) ctx.drawItem(new ItemStack(item), x + 5, rowY + 5);
                String name = textRenderer.trimToWidth(toDisplayName(itemId), Math.max(20, minusX - x - 68));
                ctx.drawText(textRenderer, name, x + 42, rowY + 8, 0xFFDDDDDD, false);
                ctx.drawText(textRenderer, "×" + row.count(), minusX - 26, rowY + 7, 0xFFEECC77, false);
            }
        }
        if (rows.size() > VISIBLE_SHAPELESS_ROWS) {
            int trackX = x - 6;
            int trackY = y;
            int trackH = visibleShapelessRows() * SHAPELESS_ROW_H;
            int thumbH = Math.max(14, trackH * VISIBLE_SHAPELESS_ROWS / rows.size());
            int thumbY = trackY + (trackH - thumbH) * shapelessScroll / maxShapelessScroll();
            ctx.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xAA101820);
            ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF9AB1BE);
            if (shapelessScroll > 0) drawShapelessScrollArrow(ctx, shapelessUpArrowX(), shapelessUpArrowY(), true,
                    mouseX >= shapelessUpArrowX() && mouseX < shapelessUpArrowX() + 32
                            && mouseY >= shapelessUpArrowY() && mouseY < shapelessUpArrowY() + 32);
            if (shapelessScroll < maxShapelessScroll())
                drawShapelessScrollArrow(ctx, shapelessArrowX(), shapelessDownArrowY(), false,
                        mouseX >= shapelessArrowX() && mouseX < shapelessArrowX() + 32
                                && mouseY >= shapelessDownArrowY() && mouseY < shapelessDownArrowY() + 32);
        }
    }

    private void drawShapelessScrollArrow(DrawContext ctx, int x, int y, boolean up, boolean hovered) {
        Identifier texture = hovered ? CustomRecipeSprites.MOVE_DOWN_HIGHLIGHTED : CustomRecipeSprites.MOVE_DOWN;
        if (!up) {
            CustomRecipeSprites.draw(ctx, texture, x, y, 32, 32);
            return;
        }
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + 16, y + 16);
        ctx.getMatrices().rotate((float) Math.PI);
        ctx.getMatrices().translate(-x - 16, -y - 16);
        CustomRecipeSprites.draw(ctx, texture, x, y, 32, 32);
        ctx.getMatrices().popMatrix();
    }

    /** Closed lock: fixed shaped layout. Open lock: free shapeless ingredient order. */
    private void drawShapeLockIcon(DrawContext ctx, int x, int y, boolean locked) {
        int metal = locked ? 0xFFD6D6D6 : 0xFFAAAAAA;
        int shadow = 0xFF303030;
        ctx.fill(x + 1, y + 4, x + 11, y + 12, shadow);
        ctx.fill(x + 2, y + 5, x + 10, y + 11, metal);
        ctx.fill(x + 5, y + 7, x + 7, y + 10, shadow);
        // The open lock has its right shackle lifted.
        ctx.fill(x + 3, y + 1, x + 9, y + 3, metal);
        ctx.fill(x + 2, y + 2, x + 4, y + 5, metal);
        if (locked) ctx.fill(x + 8, y + 2, x + 10, y + 5, metal);
        else ctx.fill(x + 8, y, x + 10, y + 3, metal);
        ctx.fill(x + 4, y + 2, x + 8, y + 4, 0xFF555555);
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

    private void drawPanel(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        drawBox(ctx, x, y, w, h, border);
    }

    @Override public boolean shouldPause() { return true; }
}
