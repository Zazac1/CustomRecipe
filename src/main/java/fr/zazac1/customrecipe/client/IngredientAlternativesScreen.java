package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.EditBox;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Edits the set of item IDs accepted by one recipe ingredient slot. */
final class IngredientAlternativesScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    private static final int PAD = 20;
    private static final int ROW_H = 20;
    private static final int SCROLLBAR_W = 12;
    private static final int SCROLLBAR_TRACK_COLOR = 0xFF5A5A5A;
    private static final int LEFT_MOUSE_BUTTON = 0;

    private final RecipeBuilderScreen parent;
    private final String primaryItem;
    private final List<String> alternatives;
    private String query = "";
    private List<String> matches = new ArrayList<>();
    private int matchScroll;
    private int alternativeScroll;
    private boolean draggingMatchScrollbar;
    private boolean draggingAlternativeScrollbar;
    private boolean restoreSearchFocus;
    private EditBox searchField;

    IngredientAlternativesScreen(RecipeBuilderScreen parent, String primaryItem, List<String> alternatives) {
        super(new TranslationTextComponent("customrecipe.alternatives.title"));
        this.parent = parent;
        this.primaryItem = primaryItem;
        this.alternatives = alternatives;
        this.matches = availableMatches();
    }

    private int panelTop() { return 52; }
    private int panelBottom() { return height - 48; }
    private int panelWidth() { return Math.max(180, (width - PAD * 2 - 12) / 2); }
    private int leftX() { return PAD; }
    private int rightX() { return leftX() + panelWidth() + 12; }
    private int listTop() { return panelTop() + 48; }
    private int visibleRows() { return Math.max(1, (panelBottom() - listTop() - 28) / ROW_H); }
    private int maxMatchScroll() { return Math.max(0, matches.size() - visibleRows()); }
    private int maxAlternativeScroll() { return Math.max(0, alternatives.size() - visibleRows()); }
    private int listHeight() { return visibleRows() * ROW_H; }
    private int leftScrollbarX() { return leftX() + panelWidth() - SCROLLBAR_W; }
    private int rightScrollbarX() { return rightX() + panelWidth() - SCROLLBAR_W; }
    private boolean hasMatchScrollbar() { return maxMatchScroll() > 0; }
    private boolean hasAlternativeScrollbar() { return maxAlternativeScroll() > 0; }

    @Override
    protected void init() {
        matches = availableMatches();
        matchScroll = Math.max(0, Math.min(maxMatchScroll(), matchScroll));
        alternativeScroll = Math.max(0, Math.min(maxAlternativeScroll(), alternativeScroll));

        searchField = addRenderableWidget(new EditBox(font, leftX() + 6, panelTop() + 24,
                panelWidth() - 12, 18, new TranslationTextComponent("customrecipe.alternatives.search")));
        searchField.setHint(new TranslationTextComponent("customrecipe.alternatives.search"));
        searchField.setValue(query);
        searchField.setResponder(value -> {
            query = value;
            matchScroll = 0;
            restoreSearchFocus = true;
            rebuildWidgets();
        });

        Button addAll = addRenderableWidget(Button.builder(new TranslationTextComponent("customrecipe.alternatives.add_all"),
                button -> addAllMatches()).bounds(leftX() + 6, panelBottom() - 24, panelWidth() - 12, 18).build());
        addAll.active = !query.trim().isEmpty() && !matches.isEmpty();
        Button removeAll = addRenderableWidget(Button.builder(new TranslationTextComponent("customrecipe.alternatives.remove_all"),
                button -> {
                    alternatives.clear();
                    alternativeScroll = 0;
                    rebuildWidgets();
                }).bounds(rightX() + 6, panelBottom() - 24, panelWidth() - 12, 18).build());
        removeAll.active = !alternatives.isEmpty();
        Button distinct = addRenderableWidget(Button.builder(distinctLabel(), button -> {
            parent.setSelectedIngredientRequiresDistinct(!parent.selectedIngredientRequiresDistinct());
            rebuildWidgets();
        }).bounds(rightX() + 6, panelTop() + 24, panelWidth() - 12, 18).build());
        distinct.active = !alternatives.isEmpty();
        addRenderableWidget(Button.builder(new TranslationTextComponent("customrecipe.alternatives.done"),
                button -> onClose()).bounds(width / 2 - 60, height - 28, 120, 20).build());

        if (restoreSearchFocus) {
            setFocused(searchField);
            searchField.setFocused(true);
            restoreSearchFocus = false;
        }
    }

    private TranslationTextComponent distinctLabel() {
        return new TranslationTextComponent("customrecipe.alternatives.distinct",
                new TranslationTextComponent(parent.selectedIngredientRequiresDistinct()
                        ? "customrecipe.common.on" : "customrecipe.common.off"));
    }

    private List<String> availableMatches() {
        return parent.alternativeMatches(query).stream()
                .filter(itemId -> !itemId.equals(primaryItem) && !alternatives.contains(itemId))
                .collect(java.util.stream.Collectors.toList());
    }

    private void addAlternative(String itemId) {
        if (!itemId.equals(primaryItem) && !alternatives.contains(itemId)) alternatives.add(itemId);
        restoreSearchFocus = true;
        rebuildWidgets();
    }

    private void addAllMatches() {
        for (String itemId : matches) {
            if (!itemId.equals(primaryItem) && !alternatives.contains(itemId)) alternatives.add(itemId);
        }
        matchScroll = 0;
        rebuildWidgets();
    }

    private void removeAlternative(int index) {
        if (index >= 0 && index < alternatives.size()) alternatives.remove(index);
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == LEFT_MOUSE_BUTTON && hasMatchScrollbar()
                && mouseX >= leftScrollbarX() && mouseX < leftScrollbarX() + SCROLLBAR_W
                && mouseY >= listTop() && mouseY < listTop() + listHeight()) {
            draggingMatchScrollbar = true;
            updateScrollbar(mouseY, true);
            return true;
        }
        if (button == LEFT_MOUSE_BUTTON && hasAlternativeScrollbar()
                && mouseX >= rightScrollbarX() && mouseX < rightScrollbarX() + SCROLLBAR_W
                && mouseY >= listTop() && mouseY < listTop() + listHeight()) {
            draggingAlternativeScrollbar = true;
            updateScrollbar(mouseY, false);
            return true;
        }
        if (button == LEFT_MOUSE_BUTTON && mouseY >= listTop() && mouseY < listTop() + listHeight()) {
            int row = (int) (mouseY - listTop()) / ROW_H;
            if (mouseX >= leftX() + 6 && mouseX < leftScrollbarX()) {
                int index = matchScroll + row;
                if (index >= 0 && index < matches.size()) addAlternative(matches.get(index));
                return true;
            }
            if (mouseX >= rightX() + 6 && mouseX < rightScrollbarX()) {
                int index = alternativeScroll + row;
                if (index >= 0 && index < alternatives.size()) removeAlternative(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        int direction = -(int) Math.signum(verticalAmount);
        if (mouseX >= leftX() && mouseX < leftX() + panelWidth()
                && mouseY >= listTop() && mouseY < listTop() + listHeight()) {
            matchScroll = Math.max(0, Math.min(maxMatchScroll(), matchScroll + direction));
            return true;
        }
        if (mouseX >= rightX() && mouseX < rightX() + panelWidth()
                && mouseY >= listTop() && mouseY < listTop() + listHeight()) {
            alternativeScroll = Math.max(0, Math.min(maxAlternativeScroll(), alternativeScroll + direction));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingMatchScrollbar) {
            updateScrollbar(mouseY, true);
            return true;
        }
        if (draggingAlternativeScrollbar) {
            updateScrollbar(mouseY, false);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingMatchScrollbar = false;
        draggingAlternativeScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollbar(double mouseY, boolean available) {
        int maxScroll = available ? maxMatchScroll() : maxAlternativeScroll();
        if (maxScroll <= 0) return;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseY - listTop()) / Math.max(1, listHeight() - 1)));
        int value = (int) Math.round(progress * maxScroll);
        if (available) matchScroll = value;
        else alternativeScroll = value;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF080C12);
        drawPanel(context, leftX(), panelTop(), panelWidth(), panelBottom() - panelTop());
        drawPanel(context, rightX(), panelTop(), panelWidth(), panelBottom() - panelTop());
        renderItemRows(context, mouseX, mouseY, matches, matchScroll, leftX(), true);
        renderItemRows(context, mouseX, mouseY, alternatives, alternativeScroll, rightX(), false);
        if (hasMatchScrollbar()) drawScrollbar(context, leftScrollbarX(), matchScroll,
                maxMatchScroll(), draggingMatchScrollbar);
        if (hasAlternativeScrollbar()) drawScrollbar(context, rightScrollbarX(), alternativeScroll,
                maxAlternativeScroll(), draggingAlternativeScrollbar);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        ITextComponent primaryLabel = new TranslationTextComponent("customrecipe.alternatives.primary",
                parent.alternativeDisplayName(primaryItem));
        ItemStack primary = parent.alternativeStack(primaryItem);
        int primaryWidth = font.width(primaryLabel) + (primary.isEmpty() ? 0 : 22);
        int primaryX = width / 2 - primaryWidth / 2;
        if (!primary.isEmpty()) context.renderItem(primary, primaryX, 25);
        context.drawString(font, primaryLabel, primaryX + (primary.isEmpty() ? 0 : 22), 30, 0xFFEECC77, true);
        context.drawString(font, new TranslationTextComponent("customrecipe.alternatives.available"),
                leftX() + 6, panelTop() + 7, 0xFF8FC7E8, true);
        context.drawString(font, new TranslationTextComponent("customrecipe.alternatives.selected", alternatives.size()),
                rightX() + 6, panelTop() + 7, 0xFF8FC7E8, true);
    }

    private void renderItemRows(GuiGraphics context, int mouseX, int mouseY, List<String> items,
                                int scroll, int panelX, boolean add) {
        int end = Math.min(items.size(), scroll + visibleRows());
        for (int index = scroll; index < end; index++) {
            String itemId = items.get(index);
            int rowY = listTop() + (index - scroll) * ROW_H;
            boolean hovered = mouseX >= panelX + 6 && mouseX < panelX + panelWidth() - SCROLLBAR_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) context.fill(panelX + 6, rowY, panelX + panelWidth() - SCROLLBAR_W,
                    rowY + ROW_H - 1, 0x55315D7D);
            context.drawString(font, add ? "+" : "×", panelX + 9, rowY + 6,
                    add ? 0xFF66FF88 : 0xFFFF7777, true);
            ItemStack stack = parent.alternativeStack(itemId);
            if (!stack.isEmpty()) context.renderItem(stack, panelX + 20, rowY + 2);
            String name = font.plainSubstrByWidth(parent.alternativeDisplayName(itemId), panelWidth() - 54);
            context.drawString(font, name, panelX + 40, rowY + 6, 0xFFDDDDDD, true);
        }
    }

    private void drawScrollbar(GuiGraphics context, int x, int scroll, int maxScroll, boolean dragging) {
        int trackH = listHeight();
        int thumbH = 15;
        int thumbY = listTop() + (trackH - thumbH) * scroll / maxScroll;
        context.fill(x, listTop(), x + SCROLLBAR_W, listTop() + trackH, SCROLLBAR_TRACK_COLOR);
        CustomRecipeSprites.draw(context,
                dragging ? CustomRecipeSprites.SCROLLER_ACTIVE : CustomRecipeSprites.SCROLLER_IDLE,
                x, thumbY, SCROLLBAR_W, thumbH);
    }

    private void drawPanel(GuiGraphics context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0xAA141A24);
        drawBorder(context, x, y, width, height, 0xFF40506A);
    }

    private void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.hLine(x, x + width - 1, y, color);
        context.hLine(x, x + width - 1, y + height - 1, color);
        context.vLine(x, y, y + height - 1, color);
        context.vLine(x + width - 1, y, y + height - 1, color);
    }

    @Override
    public void onClose() {
        parent.alternativesChanged();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
