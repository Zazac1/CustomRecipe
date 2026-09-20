package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.GlobalRecipeTarget;
import fr.zazac1.customrecipe.RecipeIntegrity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class CustomRecipesScreen extends Screen {

    private static final int PAD      = 8;
    private static final int ROW      = 20;
    private static final int HEADER_H = 16;
    private static final int MINI     = 18;
    // Compact normal previews so the table can always show one more complete row.
    private static final int DETAIL_H = 118;
    private static final int QUICK_ADD_DETAIL_H = 116;
    private static final int SHAPELESS_DETAIL_W = 150;
    private static final int QUICK_ADD_W = 24;
    private static final int QUICK_ADD_GAP = 4;

    private final ConfigScreen parent;
    private final List<CustomRecipeEntry> recipes;
    /** Reuses the main recipe table as a read-only library picker. */
    private final boolean libraryPicker;
    /** Original shaped layouts kept while their recipe is temporarily shapeless. */
    private final Map<CustomRecipeEntry, ShapedLayout> shapedLayouts = new IdentityHashMap<>();
    private int scroll         = 0;
    private int selectedRecipe = -1;
    private int selectedQuickAdd = -1;
    private int quickAddScroll;
    private boolean quickAddEditMode;
    private boolean draggingRecipeScrollbar;

    public CustomRecipesScreen(ConfigScreen parent) {
        this(parent, false);
    }

    CustomRecipesScreen(ConfigScreen parent, boolean libraryPicker) {
        super(Text.translatable(libraryPicker ? "customrecipe.screen.global_library" : "customrecipe.screen.my_recipes"));
        this.parent  = parent;
        this.libraryPicker = libraryPicker;
        this.recipes = libraryPicker ? parent.currentConfig().global_library.custom_recipes : parent.recipes;
    }

    private int listTop()     { return 28; }
    private boolean hasDetail() { return selectedRecipe >= 0 || selectedQuickAdd >= 0; }
    private int listH() {
        int available = height - listTop() - (hasDetail() ? detailH() + 4 : 0) - bottomReserve();
        // Keep the Quick Add column on complete 20 px icon rows: no orphaned gap at the bottom.
        if (!hasQuickAdd()) return available;
        return 26 + Math.max(1, (available - 26) / 20) * 20;
    }
    private int bottomReserve() {
        if (libraryPicker) return 30;
        // World mode has two action buttons before Back; keep the table above both.
        return parent.target().isWorld() ? 70 : 48;
    }
    private int rowsTop()     { return listTop() + HEADER_H; }
    private int maxVisible()  { return Math.max(1, (listH() - HEADER_H) / ROW); }
    private int rowY(int i)   { return rowsTop() + (i - scroll) * ROW; }
    private int detailY()     { return listTop() + listH() + 2; }
    /** Keep the detail header fixed below the table, then use all remaining space below it. */
    private int renderedDetailH() { return Math.max(detailH(), height - bottomReserve() - detailY()); }
    private boolean hasQuickAdd() { return !libraryPicker; }
    private int tableRight() { return width - PAD - (hasQuickAdd() ? QUICK_ADD_W + QUICK_ADD_GAP : 0); }
    private int tableWidth() { return tableRight() - PAD; }
    private int detailRight() { return width - PAD; }
    private int detailWidth() { return detailRight() - PAD; }
    private int quickAddX() { return tableRight() + QUICK_ADD_GAP; }
    private int quickAddY(int index) { return listTop() + 24 + (index - quickAddScroll) * 20; }
    private int quickAddVisible() { return Math.max(1, (listH() - 26) / 20); }
    private int quickAddCount() {
        return quickAddBuiltinIndices().size() + parent.quickAddRecipes.size();
    }
    private List<Integer> quickAddBuiltinIndices() {
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < BuiltinRecipesScreen.quickAddCount(); i++)
            if (!parent.hiddenQuickAddBuiltin.contains(BuiltinRecipesScreen.quickAddId(i))) visible.add(i);
        return visible;
    }
    private boolean quickAddIsBuiltin(int index) { return index < quickAddBuiltinIndices().size(); }
    private int quickAddBuiltinIndex(int index) { return quickAddBuiltinIndices().get(index); }
    private int quickAddCustomRecipeIndex(int index) {
        int wanted = index - quickAddBuiltinIndices().size();
        return wanted >= 0 && wanted < parent.quickAddRecipes.size() ? wanted : -1;
    }
    private CustomRecipeEntry quickAddRecipe(int index) {
        if (quickAddIsBuiltin(index)) return BuiltinRecipesScreen.createQuickAddRecipe(quickAddBuiltinIndex(index));
        int custom = quickAddCustomRecipeIndex(index);
        return custom < 0 ? null : parent.quickAddRecipes.get(custom);
    }
    private String quickAddName(int index) {
        CustomRecipeEntry recipe = quickAddRecipe(index);
        return recipe == null ? "" : toName(recipe.result);
    }
    private int quickAddMaxScroll() { return Math.max(0, quickAddCount() - quickAddVisible()); }
    private int detailGridX() { return PAD + 4; }
    private int detailGridY() { return detailY() + (selectedQuickAdd >= 0 ? 60 : 62); }
    private int detailShapelessListX() { return PAD + 4; }
    private int detailShapelessListY() { return detailY() + (selectedQuickAdd >= 0 ? 60 : 62); }
    private int detailShapedListX() { return detailGridX() + 3 * MINI + 14 + MINI + 16; }
    private int detailShapelessResultX(CustomRecipeEntry recipe) {
        return detailShapelessListX() + shapelessColumnCount(recipe) * SHAPELESS_DETAIL_W + 14;
    }
    private int detailShapelessResultY(CustomRecipeEntry recipe) {
        int visibleRows = Math.min(3, Math.max(1, shapelessIngredientRows(recipe).size()));
        return detailShapelessListY() + (visibleRows - 1) * MINI / 2;
    }
    private int detailActionX() { return PAD + 4; }
    private int detailActionY() { return detailY() + 26; }
    private int detailKnownX() { return detailActionX() + 32 + textRenderer.getWidth("Shapeless"); }
    private int detailH() {
        if (selectedQuickAdd >= 0) return QUICK_ADD_DETAIL_H;
        if (selectedRecipe < 0 || selectedRecipe >= recipes.size()) return DETAIL_H;
        return needsTallDetail(recipes.get(selectedRecipe)) ? 140 : DETAIL_H;
    }
    private boolean needsTallDetail(CustomRecipeEntry recipe) {
        // Warnings need their own line below the ingredient preview.
        if (isCorrupted(recipe) || firstEnabledConflict(recipe) != null) return true;
        if (Boolean.TRUE.equals(recipe.known_by_default)) return false;
        return LocalRecipeConflictDetector.hasVanillaCraftingOutput(recipe.result)
                || firstEnabledSameShapeRecipe(recipe) != null;
    }
    private int detailItemRows(CustomRecipeEntry recipe) {
        return 3;
    }
    private int shapelessColumnCount(CustomRecipeEntry recipe) {
        return Math.max(1, (shapelessIngredientRows(recipe).size() + 2) / 3);
    }
    private int detailMessageY() {
        if (selectedRecipe < 0 || selectedRecipe >= recipes.size()) return detailY() + 76;
        return detailY() + 66 + detailItemRows(recipes.get(selectedRecipe)) * MINI;
    }
    private int itemIconX()   { return PAD + 14; }
    private int warningIconX(){ return PAD + 63; }
    private int typeIconX()   { return PAD + 100; }
    private int knownTypeIconX() { return typeIconX() + 18; }
    private int recipeX()     { return PAD + 144; }
    private int statusX()     { return tableRight() - 117; }
    private int deleteX()     { return tableRight() - 22; }
    private int maxScroll()   { return Math.max(0, recipes.size() - maxVisible()); }
    private boolean hasRecipeScrollbar() { return maxScroll() > 0; }
    private int recipeScrollTrackX() { return statusX() - 9; }
    private int recipeScrollTrackY() { return rowsTop() + 1; }
    private int recipeScrollTrackH() { return Math.max(1, listTop() + listH() - recipeScrollTrackY() - 2); }
    private int recipeScrollThumbH() {
        return Math.max(12, recipeScrollTrackH() * maxVisible() / Math.max(1, recipes.size()));
    }
    private int recipeScrollThumbY() {
        int travel = Math.max(0, recipeScrollTrackH() - recipeScrollThumbH());
        return recipeScrollTrackY() + (maxScroll() == 0 ? 0 : travel * scroll / maxScroll());
    }

    @Override
    protected void init() {
        // A recipe can recover when a previously missing mod is installed again.
        // Refresh first so stale persisted corruption flags never survive that recovery.
        for (CustomRecipeEntry recipe : recipes) RecipeIntegrity.refresh(recipe);
        if (!parent.isServerManaged()) {
            LocalRecipeConflictDetector.refresh(recipes, client);
        } else {
            LocalRecipeConflictDetector.refreshVanillaCraftingOutputs(client);
        }
        // ── Fills ────────────────────────────────────────────────────────
        addDrawable((ctx, mx, my, d) -> {
            ctx.fill(PAD, listTop(), tableRight(), listTop() + listH(), 0x88101010);
            drawBox(ctx, PAD, listTop(), tableWidth(), listH(), 0xFF505050);
            // Spreadsheet-style column header: every icon has a named, fixed column.
            ctx.fill(PAD + 1, listTop() + 1, tableRight() - 1, rowsTop() - 1, 0xDD252B2A);
            ctx.drawHorizontalLine(PAD + 1, tableRight() - 2, rowsTop() - 1, 0xFF596462);
            int[] columns = {PAD + 45, PAD + 96, PAD + 138, statusX() - 4, deleteX() - 4};
            for (int column : columns)
                ctx.drawVerticalLine(column, listTop() + 1, listTop() + listH() - 2, 0xFF39433F);
            int headerColor = 0xFFB8C7C1;
            ctx.drawText(textRenderer, Text.translatable("customrecipe.table.output"), PAD + 4, listTop() + 4, headerColor, false);
            ctx.drawText(textRenderer, Text.translatable("customrecipe.table.status"), PAD + 48, listTop() + 4, headerColor, false);
            ctx.drawText(textRenderer, Text.translatable("customrecipe.table.type"), PAD + 101, listTop() + 4, headerColor, false);
            ctx.drawText(textRenderer, Text.translatable("customrecipe.table.recipe"), recipeX(), listTop() + 4, headerColor, false);
            ctx.drawText(textRenderer, Text.translatable(libraryPicker ? "customrecipe.table.add" : "customrecipe.table.state"), statusX() + 28, listTop() + 4, headerColor, false);
            if (hasRecipeScrollbar()) {
                int trackX = recipeScrollTrackX();
                int trackY = recipeScrollTrackY();
                ctx.fill(trackX, trackY, trackX + 4, trackY + recipeScrollTrackH(), 0xFF171B1A);
                ctx.fill(trackX, recipeScrollThumbY(), trackX + 4,
                        recipeScrollThumbY() + recipeScrollThumbH(), 0xFFB8C7C1);
            }
            int vis = maxVisible();
            for (int i = scroll; i < Math.min(recipes.size(), scroll + vis); i++) {
                int y = rowY(i);
                if (y < listTop() || y + ROW > listTop() + listH()) continue;
                boolean sel = selectedRecipe == i;
                boolean corrupted = isCorrupted(recipes.get(i));
                boolean conflict = isActiveForThisScreen(recipes.get(i))
                        && firstEnabledConflict(recipes.get(i)) != null;
                boolean on = (libraryPicker || isActiveForThisScreen(recipes.get(i))) && !corrupted;
                int rowColor = corrupted ? (sel ? 0x44662200 : 0x44550000)
                        : on  ? (sel ? 0x44005533 : 0x22005500)
                            : (sel ? 0x44662200 : 0x44550000);
                // The state tint runs under the action controls too, then gently fades out.
                ctx.fillGradient(PAD + 1, y, tableRight() - 1, y + ROW - 2,
                        rowColor, (rowColor & 0x00FFFFFF) | 0x18000000);
                // Icône de l'item résultat
                String resId = recipes.get(i).result;
                Identifier resultId = resId == null ? null : Identifier.tryParse(resId);
                if (resultId != null && Registries.ITEM.containsId(resultId)) {
                    ctx.drawItem(new ItemStack(Registries.ITEM.get(resultId)), itemIconX(), y + 2);
                }
                drawRecipeStatusIcon(ctx, warningIconX(), y + 2, corrupted, conflict);
                boolean shapedType = "shaped".equalsIgnoreCase(recipes.get(i).type);
                drawRecipeTypeIcon(ctx, typeIconX() + (shapedType ? 1 : 0), y + (shapedType ? 3 : 2),
                        shapedType, 0xFF8FC7E8);
                drawKnownByDefaultIcon(ctx, knownTypeIconX(), y + 1,
                        Boolean.TRUE.equals(recipes.get(i).known_by_default));
            }
            if (hasQuickAdd()) renderQuickAddPanel(ctx);
            if (selectedRecipe >= 0 && selectedRecipe < recipes.size())
                renderDetailFills(ctx, recipes.get(selectedRecipe), false);
            else if (selectedQuickAdd >= 0 && quickAddRecipe(selectedQuickAdd) != null)
                renderDetailFills(ctx, quickAddRecipe(selectedQuickAdd), true);
        });

        // ── Titre ─────────────────────────────────────────────────────────
        if (libraryPicker) {
            String libraryLabel = "Global Library";
            addDrawable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, client,
                    GlobalRecipeTarget.INSTANCE, libraryLabel));
            boolean hasWorldTarget = parent.target().isWorld();
            int importable = hasWorldTarget ? parent.globalLibraryImportableCount() : 0;
            int importButtonX = 52 + textRenderer.getWidth(libraryLabel);
            int libraryActionW = 92;
            int libraryActionsX = width - PAD - libraryActionW * 2 - 4;
            int importButtonW = Math.max(1, Math.min(200, libraryActionsX - 8 - importButtonX));
            Text importLabel = hasWorldTarget
                    ? Text.translatable("customrecipe.button.add_all_from_library")
                    : Text.translatable("customrecipe.button.select_world_to_import");
            ButtonWidget addAll = ButtonWidget.builder(importLabel, b -> {
                if (hasWorldTarget) {
                    client.setScreen(new ImportGlobalLibraryScreen(parent, this, importable));
                } else {
                    client.setScreen(new RecipeTargetSelectScreen(parent,
                            selected -> new CustomRecipesScreen(selected, true)));
                }
            }).dimensions(importButtonX, 4, importButtonW, 20).build();
            addAll.active = !hasWorldTarget || importable > 0;
            if (hasWorldTarget && !addAll.active) addAll.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                    Text.translatable("customrecipe.import.nothing_to_add")));
            addDrawableChild(addAll);
        } else if (parent.target().isWorld()) {
            String worldLabel = "Recipes from " + parent.target().displayName();
            addDrawable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, client,
                    parent.target(), worldLabel));
        } else {
            addDrawable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, client,
                    parent.target(), "Global Library"));
            int libraryActionW = 118;
        }

        // ── Lignes de recettes ────────────────────────────────────────────
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(recipes.size(), scroll + vis); i++) {
            final int idx = i;
            int y = rowY(i);
            if (y < listTop() || y + ROW > listTop() + listH()) continue;
            boolean sel = selectedRecipe == idx;

            MultilineTextWidget lbl = new MultilineTextWidget(
                    recipeX(), y + (ROW - 8) / 2,
                    Text.literal(formatEntry(recipes.get(i)))
                            .withColor(sel ? 0xFFEE88 : 0xDDDDDD),
                    textRenderer);
            lbl.setMaxWidth(statusX() - recipeX() - 8);
            lbl.setMaxRows(1);
            addDrawableChild(lbl);

            CustomRecipeEntry entry = recipes.get(idx);
            boolean corrupted = isCorrupted(entry);
            boolean active = isActiveForThisScreen(entry);
            addDrawableChild(ButtonWidget.builder(
                    corrupted ? Text.translatable("customrecipe.state.corrupted").withColor(0xFF5555)
                    : libraryPicker ? Text.translatable("customrecipe.button.add").withColor(0x55FF55)
                    : parent.target().isWorld()
                            ? Text.translatable(active ? "customrecipe.state.enabled" : "customrecipe.state.disabled")
                                    .withColor(active ? 0x55FF55 : 0xFF5555)
                            : Text.translatable("customrecipe.state.library").withColor(0x77BBFF),
                    b -> {
                        if (isCorrupted(recipes.get(idx))) return;
                        if (libraryPicker) {
                            parent.addFromLibrary(recipes.get(idx));
                            client.setScreen(new CustomRecipesScreen(parent));
                            return;
                        }
                        if (parent.target().isWorld()) {
                            // Per-world recipes can be toggled directly from their State cell.
                            recipes.get(idx).enabled = active ? Boolean.FALSE : Boolean.TRUE;
                            clearAndInit();
                        }
                    }
            ).dimensions(statusX(), y + 2, 88, ROW - 4).build());

            if (!libraryPicker) addDrawableChild(ButtonWidget.builder(Text.empty(),
                    b -> {
                        if (quickAddEditMode) {
                            CustomRecipeEntry snapshot = ConfigLoader.copyRecipe(recipes.get(idx));
                            if (snapshot != null) {
                                snapshot.quick_add = null;
                                parent.quickAddRecipes.add(snapshot);
                            }
                            // Adding one shortcut completes this one-shot mode.
                            quickAddEditMode = false;
                            clearAndInit();
                            return;
                        }
                        if (selectedRecipe == idx) selectedRecipe = -1;
                        else if (selectedRecipe > idx) selectedRecipe--;
                        recipes.remove(idx);
                        clearAndInit();
                    }
            ).dimensions(deleteX(), y + 1, 18, 18).build());
            if (!libraryPicker) addDrawable((ctx, mx, my, d) -> {
                if (quickAddEditMode) {
                    if (parent.target().isWorld()) drawBox(ctx, deleteX(), y + 1, 18, 18, 0xFFFFD447);
                    CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ADD, deleteX() + 1, y + 2, 16, 16);
                } else {
                    CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, deleteX(), y + 1, 18, 18);
                }
            });
        }

        if (hasQuickAdd()) {
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                quickAddEditMode = !quickAddEditMode;
                selectedQuickAdd = -1;
                clearAndInit();
            }).dimensions(quickAddX() + 2, listTop() + 3, 20, 20).build());
            addDrawable((ctx, mx, my, d) -> {
                if (quickAddEditMode) {
                    ctx.fill(quickAddX() + 3, listTop() + 4, quickAddX() + 21, listTop() + 22, 0xFF303030);
                }
                CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ADD, quickAddX() + 4, listTop() + 5, 16, 16);
            });
            for (int i = quickAddScroll; i < Math.min(quickAddCount(), quickAddScroll + quickAddVisible()); i++) {
                final int quickIndex = i;
                addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    // A Quick Add always owns the detail panel, including snapshots from the library.
                    selectedRecipe = -1;
                    selectedQuickAdd = quickIndex;
                    clearAndInit();
                }).dimensions(quickAddX() + 2, quickAddY(i), 20, 20).build());
                final int iconY = quickAddY(i);
                addDrawable((ctx, mx, my, d) -> drawQuickAddIcon(ctx, quickIndex, quickAddX() + 4, iconY + 2));
            }
        }

        // ── Message vide ──────────────────────────────────────────────────
        if (recipes.isEmpty()) {
            int emptyX = recipeX();
            MultilineTextWidget empty = new MultilineTextWidget(
                    emptyX, rowsTop() + Math.max(4, (listH() - HEADER_H - 8) / 2),
                    Text.translatable(libraryPicker ? "customrecipe.empty.library" : "customrecipe.empty.recipes")
                            .withColor(0x999999),
                    textRenderer);
            empty.setMaxWidth(Math.max(20, statusX() - emptyX - 8));
            empty.setCentered(true);
            addDrawableChild(empty);
        }

        // ── Scroll hint ───────────────────────────────────────────────────
        // ── Panneau de détail (widgets texte) ─────────────────────────────
        if (selectedQuickAdd >= 0) {
            CustomRecipeEntry e = quickAddRecipe(selectedQuickAdd);
            if (e == null) return;
            MultilineTextWidget name = new MultilineTextWidget(PAD + 46, detailY() + 4,
                    Text.translatable("customrecipe.quick.preview", quickAddName(selectedQuickAdd))
                            .withColor(0xFFEECC77), textRenderer);
            name.setMaxWidth(detailWidth() - 170);
            name.setMaxRows(1);
            addDrawableChild(name);
            int quickIndex = selectedQuickAdd;
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.add_recipe"), b -> {
                CustomRecipeEntry added = ConfigLoader.copyRecipe(quickAddRecipe(quickIndex));
                if (added == null) return;
                added.id = java.util.UUID.randomUUID().toString();
                added.quick_add = null;
                recipes.add(added);
                selectedQuickAdd = -1;
                selectedRecipe = recipes.size() - 1;
                clearAndInit();
            }).dimensions(detailRight() - 128, detailY() + 4, 102, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                if (quickAddIsBuiltin(quickIndex)) {
                    String builtinId = BuiltinRecipesScreen.quickAddId(quickAddBuiltinIndex(quickIndex));
                    if (!parent.hiddenQuickAddBuiltin.contains(builtinId)) parent.hiddenQuickAddBuiltin.add(builtinId);
                } else {
                    int custom = quickAddCustomRecipeIndex(quickIndex);
                    if (custom >= 0) parent.quickAddRecipes.remove(custom);
                }
                selectedQuickAdd = -1;
                quickAddScroll = Math.min(quickAddScroll, quickAddMaxScroll());
                clearAndInit();
            }).dimensions(detailRight() - 22, detailY() + 4, 18, 18).build());
            addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                    CustomRecipeSprites.REJECT, detailRight() - 22, detailY() + 4, 18, 18));
        } else if (selectedRecipe >= 0 && selectedRecipe < recipes.size()) {
            CustomRecipeEntry e = recipes.get(selectedRecipe);
            boolean corrupted = isCorrupted(e);
            String label = (corrupted ? "Corrupted: " : "") + toName(e.result) + (e.count > 1 ? " ×" + e.count : "");
            MultilineTextWidget nameW = new MultilineTextWidget(
                    PAD + 46, detailY() + 4,
                    Text.literal(label).withColor(corrupted ? 0xFF7777 : 0xFFEE77), textRenderer);
            nameW.setMaxWidth(detailWidth() - 220);
            nameW.setMaxRows(1);
            addDrawableChild(nameW);

            // Flèche →
            boolean shapedPreview = "shaped".equalsIgnoreCase(e.type);
            int previewResultX = shapedPreview ? detailGridX() + 3 * MINI + 14 : detailShapelessResultX(e);
            int previewResultY = shapedPreview ? detailGridY() + MINI
                    : detailShapelessResultY(e);
            MultilineTextWidget arrow = new MultilineTextWidget(
                    previewResultX - 11, previewResultY + (MINI - 8) / 2,
                    Text.literal("→"), textRenderer);
            arrow.setMaxWidth(12);
            arrow.setMaxRows(1);
            addDrawableChild(arrow);

            final int selected = selectedRecipe;
            if (libraryPicker) {
                addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.recipe.add_to", parent.target().displayName()), b -> {
                    if (isCorrupted(recipes.get(selected))) return;
                    parent.addFromLibrary(recipes.get(selected));
                    client.setScreen(new CustomRecipesScreen(parent));
                }).dimensions(detailRight() - 202, detailY() + 4, 198, 18).build());
            } else if (corrupted) {
                String mods = String.join(", ", RecipeIntegrity.requiredModIds(e));
                MultilineTextWidget warning = new MultilineTextWidget(PAD + 4, detailY() + 76,
                        Text.translatable("customrecipe.recipe.missing", String.join(", ", missingItems(e))
                                + " — reinstall " + (mods.isBlank() ? "the required mod" : mods) + " or delete this recipe.")
                                .withColor(0xFF7777), textRenderer);
                warning.setMaxWidth(detailWidth() - 8);
                warning.setMaxRows(1);
                addDrawableChild(warning);
                addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.delete_corrupted").withColor(0xFF7777), b -> {
                    recipes.remove(selected);
                    selectedRecipe = -1;
                    clearAndInit();
                }).dimensions(detailRight() - 142, detailY() + 30, 138, 18).build());
            } else {
                addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.duplicate"),
                        b -> client.setScreen(new RecipeBuilderScreen(parent, this, recipes.get(selected), -1)))
                        .dimensions(detailRight() - 204, detailY() + 4, 96, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.edit"),
                        b -> client.setScreen(new RecipeBuilderScreen(parent, this, recipes.get(selected), selected)))
                        .dimensions(detailRight() - 102, detailY() + 4, 96, 18).build());
                int typeX = detailActionX();
                int knownX = detailKnownX();
                boolean shaped = "shaped".equalsIgnoreCase(e.type);
                addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    toggleRecipeType(recipes.get(selected));
                    clearAndInit();
                }).dimensions(typeX, detailActionY(), 20, 20).build());
                addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                        shaped ? CustomRecipeSprites.LOCKED_BUTTON : CustomRecipeSprites.UNLOCKED_BUTTON,
                        typeX, detailActionY(), 20, 20));
                boolean known = Boolean.TRUE.equals(e.known_by_default);
                addDrawableChild(ButtonWidget.builder(Text.empty(),
                        b -> {
                            CustomRecipeEntry recipe = recipes.get(selected);
                            recipe.known_by_default = !Boolean.TRUE.equals(recipe.known_by_default);
                            clearAndInit();
                        }
                ).dimensions(knownX, detailActionY(), 20, 20).build());
                addDrawable((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                        known ? CustomRecipeSprites.KNOWN_BY_DEFAULT : CustomRecipeSprites.NOT_KNOWN_BY_DEFAULT,
                        knownX, detailActionY(), 20, 20));
                String conflict = firstEnabledConflict(e);
                if (conflict != null) {
                    MultilineTextWidget warning = new MultilineTextWidget(PAD + 4, detailMessageY(),
                            Text.translatable("customrecipe.recipe.conflict", shortRecipeId(conflict))
                                    .withColor(0xFFCC55), textRenderer);
                    warning.setMaxWidth(detailWidth() - 8);
                    warning.setMaxRows(1);
                    addDrawableChild(warning);
                    addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.disable_conflict").withColor(0xFFCC55), b -> {
                        if (!parent.disabledRecipes.contains(conflict)) parent.disabledRecipes.add(conflict);
                        clearAndInit();
                    }).dimensions(detailRight() - 204, detailY() + 26, 198, 18).build());
                } else if (!known) {
                    if (LocalRecipeConflictDetector.hasVanillaCraftingOutput(e.result)) {
                        MultilineTextWidget advice = new MultilineTextWidget(PAD + 4, detailMessageY(),
                                Text.translatable("customrecipe.recipe.vanilla_conflict")
                                        .withColor(0x77BBFF), textRenderer);
                        advice.setMaxWidth(detailWidth() - 8);
                        advice.setMaxRows(1);
                        addDrawableChild(advice);
                    } else {
                        String alternative = firstEnabledSameShapeRecipe(e);
                        if (alternative != null) {
                        MultilineTextWidget advice = new MultilineTextWidget(PAD + 4, detailMessageY(),
                                Text.translatable("customrecipe.recipe.same_inputs", alternative)
                                        .withColor(0x77BBFF), textRenderer);
                        advice.setMaxWidth(detailWidth() - 8);
                        advice.setMaxRows(2);
                        addDrawableChild(advice);
                        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.set_known").withColor(0x77BBFF), b -> {
                            recipes.get(selected).known_by_default = Boolean.TRUE;
                            clearAndInit();
                        }).dimensions(detailRight() - 142, detailY() + 52, 138, 18).build());
                        }
                    }
                }
            }
        }

        // ── Boutons du bas ────────────────────────────────────────────────
        if (!libraryPicker && parent.target().isWorld()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.add_from_library"),
                    b -> client.setScreen(new CustomRecipesScreen(parent, true))
            ).dimensions(width / 2 - 100, height - 66, 200, 18).build());
        }

        if (!libraryPicker) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.add_recipe_new"),
                    b -> client.setScreen(new RecipeBuilderScreen(parent, this, null, -1))
            ).dimensions(width / 2 - 100, height - 44, 200, 18).build());
        }

        if (libraryPicker) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.back_to_recipes"),
                    b -> client.setScreen(new CustomRecipesScreen(parent))
            ).dimensions(width / 2 - 75, height - 22, 150, 18).build());
        } else {
            String saveLabel = Text.translatable("customrecipe.button.save").getString();
            int saveY = height - 26;
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> parent.saveFromSubmenu())
                    .dimensions(width / 2 - 100, saveY, 200, 22).build());
            addDrawable((ctx, mouseX, mouseY, delta) -> {
                int iconX = width / 2 - textRenderer.getWidth(saveLabel) / 2 - 20;
                CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SAVE, iconX, saveY + 3, 16, 16);
                ctx.drawCenteredTextWithShadow(textRenderer, saveLabel, width / 2, saveY + 7, 0xFFFFFFFF);
            });
        }
    }

    private void renderQuickAddPanel(DrawContext ctx) {
        int x = quickAddX();
        int h = listH();
        ctx.fill(x, listTop(), width - PAD, listTop() + h, 0x88101010);
        drawBox(ctx, x, listTop(), width - PAD - x, h, 0xFF505050);
        for (int i = quickAddScroll; i < Math.min(quickAddCount(), quickAddScroll + quickAddVisible()); i++) {
            int y = quickAddY(i);
            boolean selected = selectedQuickAdd == i;
            if (selected) ctx.fill(x + 3, y, x + QUICK_ADD_W - 3, y + 20, 0x44335533);
        }
        if (quickAddMaxScroll() > 0) {
            int trackY = listTop() + 2;
            int trackH = h - 4;
            int thumbH = Math.max(10, trackH * quickAddVisible() / quickAddCount());
            int travel = trackH - thumbH;
            int thumbY = trackY + travel * quickAddScroll / quickAddMaxScroll();
            ctx.fill(x + QUICK_ADD_W - 2, trackY, x + QUICK_ADD_W - 1, trackY + trackH, 0xFF171B1A);
            ctx.fill(x + QUICK_ADD_W - 2, thumbY, x + QUICK_ADD_W - 1, thumbY + thumbH, 0xFFB8C7C1);
        }
    }

    private void drawQuickAddIcon(DrawContext ctx, int quickIndex, int x, int y) {
        String result = quickAddIsBuiltin(quickIndex)
                ? BuiltinRecipesScreen.quickAddResult(quickAddBuiltinIndex(quickIndex))
                : quickAddCustomRecipeIndex(quickIndex) < 0 ? "" : parent.quickAddRecipes.get(quickAddCustomRecipeIndex(quickIndex)).result;
        Identifier id = Identifier.tryParse(result);
        if (id != null && Registries.ITEM.containsId(id))
            ctx.drawItem(new ItemStack(Registries.ITEM.get(id)), x, y);
    }

    private void renderDetailFills(DrawContext ctx, CustomRecipeEntry e, boolean quickAddPreview) {
        int dy = detailY();
        ctx.fill(PAD, dy, detailRight(), dy + renderedDetailH(), 0x88101010);
        drawBox(ctx, PAD, dy, detailWidth(), renderedDetailH(), 0xFF506070);
        boolean shapedDetail = "shaped".equalsIgnoreCase(e.type);
        drawRecipeTypeIcon(ctx, PAD + 4 + (shapedDetail ? 1 : 0), dy + (shapedDetail ? 3 : 2), shapedDetail,
                isCorrupted(e) ? 0xFFFF7777 : 0xFFEECC77);
        if (!quickAddPreview) {
            drawRecipeStatusIcon(ctx, PAD + 24, dy + 2, isCorrupted(e), firstEnabledConflict(e) != null);
        }
        boolean known = Boolean.TRUE.equals(e.known_by_default);
        ctx.drawText(textRenderer, Text.translatable(shapedDetail ? "customrecipe.recipe.shaped" : "customrecipe.recipe.shapeless"), detailActionX() + 24, detailActionY() + 6,
                shapedDetail ? 0xFFFFD700 : 0xFF88FFFF, false);
        ctx.drawText(textRenderer, Text.translatable("customrecipe.recipe.known", Text.translatable(known ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), detailKnownX() + 24,
                detailActionY() + 6, known ? 0xFF55FF55 : 0xFFFF7777, false);
        ctx.drawText(textRenderer, Text.translatable(shapedDetail
                ? "customrecipe.recipe.shaped_ingredients" : "customrecipe.recipe.shapeless_ingredients",
                detailIngredientCount(e)), PAD + 4, dy + 50, 0xFF8FC7E8, false);

        if (!shapedDetail) {
            renderShapelessDetail(ctx, e);
            return;
        }

        renderShapedIngredientColumns(ctx, shapedIngredientRows(e));
        String[][] grid = buildDisplayGrid(e);
        int gx = detailGridX(), gy = detailGridY();

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int sx = gx + c * MINI, sy = gy + r * MINI;
                ctx.fill(sx + 1, sy + 1, sx + MINI - 1, sy + MINI - 1, 0xFF3A3A3A);
                drawBox(ctx, sx, sy, MINI, MINI, 0xFF555555);
                String id = grid[r][c];
                if (id != null && !id.isEmpty()) {
                    var item = Registries.ITEM.get(Identifier.tryParse(id));
                    if (item != null && item != Items.AIR)
                        ctx.drawItem(new ItemStack(item), sx + 1, sy + 1);
                }
            }
        }

        // Slot résultat (centré sur la rangée du milieu)
        int resultX = gx + 3 * MINI + 14;
        int resultY = gy + MINI;
        ctx.fill(resultX + 1, resultY + 1, resultX + MINI - 1, resultY + MINI - 1, 0xFF3A3A3A);
        drawBox(ctx, resultX, resultY, MINI, MINI, 0xFF908830);
        if (e.result != null && !e.result.isEmpty()) {
            var ri = Registries.ITEM.get(Identifier.tryParse(e.result));
            if (ri != null && ri != Items.AIR)
                ctx.drawItem(new ItemStack(ri), resultX + 1, resultY + 1);
        }
    }

    /** Reconstruit la grille 3×3 d'affichage depuis un CustomRecipeEntry. */
    private record ShapelessIngredient(String itemId, int count) {}

    private int detailIngredientCount(CustomRecipeEntry e) {
        if (!"shaped".equalsIgnoreCase(e.type)) return e.ingredients == null ? 0 : e.ingredients.size();
        int count = 0;
        String[][] grid = buildDisplayGrid(e);
        for (String[] row : grid) for (String itemId : row)
            if (itemId != null && !itemId.isEmpty()) count++;
        return count;
    }

    private List<ShapelessIngredient> shapelessIngredientRows(CustomRecipeEntry e) {
        return groupedIngredientRows(e.ingredients);
    }

    private List<ShapelessIngredient> shapedIngredientRows(CustomRecipeEntry e) {
        List<String> ingredients = new ArrayList<>();
        for (String[] row : buildDisplayGrid(e)) {
            for (String itemId : row) {
                if (itemId != null && !itemId.isEmpty()) ingredients.add(itemId);
            }
        }
        return groupedIngredientRows(ingredients);
    }

    private List<ShapelessIngredient> groupedIngredientRows(List<String> ingredients) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (ingredients != null) {
            for (String itemId : ingredients) {
                if (itemId != null && !itemId.isEmpty()) counts.merge(itemId, 1, Integer::sum);
            }
        }
        List<ShapelessIngredient> rows = new ArrayList<>();
        counts.forEach((itemId, count) -> rows.add(new ShapelessIngredient(itemId, count)));
        return rows;
    }

    private void renderShapelessDetail(DrawContext ctx, CustomRecipeEntry e) {
        int x = detailShapelessListX();
        int y = detailShapelessListY();
        List<ShapelessIngredient> rows = shapelessIngredientRows(e);
        renderShapelessIngredientColumns(ctx, rows, x, y);

        int resultX = detailShapelessResultX(e);
        int resultY = detailShapelessResultY(e);
        ctx.fill(resultX + 1, resultY + 1, resultX + MINI - 1, resultY + MINI - 1, 0xFF3A3A3A);
        drawBox(ctx, resultX, resultY, MINI, MINI, 0xFF908830);
        if (e.result != null && !e.result.isEmpty()) {
            var result = Registries.ITEM.get(Identifier.tryParse(e.result));
            if (result != null && result != Items.AIR) ctx.drawItem(new ItemStack(result), resultX + 1, resultY + 1);
        }
    }

    private void renderIngredientRows(DrawContext ctx, List<ShapelessIngredient> rows, int x, int y) {
        for (int i = 0; i < rows.size(); i++) {
            ShapelessIngredient row = rows.get(i);
            int rowY = y + i * MINI;
            var item = Registries.ITEM.get(Identifier.tryParse(row.itemId()));
            if (item != null && item != Items.AIR) ctx.drawItem(new ItemStack(item), x, rowY + 1);
            String name = textRenderer.trimToWidth(toName(row.itemId()), 104);
            ctx.drawText(textRenderer, name, x + 20, rowY + 5, 0xFFDDDDDD, false);
            ctx.drawText(textRenderer, "x" + row.count(), x + 126, rowY + 5, 0xFFEECC77, false);
        }
    }

    private void renderShapedIngredientColumns(DrawContext ctx, List<ShapelessIngredient> rows) {
        for (int i = 0; i < rows.size(); i++) {
            int column = i / 3;
            int row = i % 3;
            renderIngredientRow(ctx, rows.get(i), detailShapedListX() + column * SHAPELESS_DETAIL_W,
                    detailGridY() + row * MINI);
        }
    }

    private void renderShapelessIngredientColumns(DrawContext ctx, List<ShapelessIngredient> rows, int x, int y) {
        for (int i = 0; i < rows.size(); i++) {
            int column = i / 3;
            int row = i % 3;
            renderIngredientRow(ctx, rows.get(i), x + column * SHAPELESS_DETAIL_W, y + row * MINI);
        }
    }

    private void renderIngredientRow(DrawContext ctx, ShapelessIngredient row, int x, int y) {
        var item = Registries.ITEM.get(Identifier.tryParse(row.itemId()));
        if (item != null && item != Items.AIR) ctx.drawItem(new ItemStack(item), x, y + 1);
        String name = textRenderer.trimToWidth(toName(row.itemId()), 104);
        ctx.drawText(textRenderer, name, x + 20, y + 5, 0xFFDDDDDD, false);
        ctx.drawText(textRenderer, "x" + row.count(), x + 126, y + 5, 0xFFEECC77, false);
    }

    private String[][] buildDisplayGrid(CustomRecipeEntry e) {
        String[][] grid = new String[3][3];
        if ("shaped".equalsIgnoreCase(e.type)) {
            List<String> pat = e.pattern;
            Map<String, String> keys = e.keys;
            if (pat == null || keys == null) return grid;
            for (int r = 0; r < 3 && r < pat.size(); r++) {
                String row = pat.get(r);
                for (int c = 0; c < 3 && c < row.length(); c++) {
                    char ch = row.charAt(c);
                    if (ch != ' ') grid[r][c] = keys.get(String.valueOf(ch));
                }
            }
        } else {
            List<String> ing = e.ingredients;
            if (ing == null) return grid;
            int n = 0;
            for (int r = 0; r < 3 && n < ing.size(); r++)
                for (int c = 0; c < 3 && n < ing.size(); c++)
                    grid[r][c] = ing.get(n++);
        }
        return grid;
    }

    /** Switches between fixed shaped slots and free shapeless ingredients. */
    private void toggleRecipeType(CustomRecipeEntry e) {
        String[][] grid = buildDisplayGrid(e);
        if ("shaped".equalsIgnoreCase(e.type)) {
            shapedLayouts.put(e, new ShapedLayout(e.pattern, e.keys));
            e.type = "shapeless";
            e.ingredients = new ArrayList<>();
            for (int row = 0; row < 3; row++)
                for (int column = 0; column < 3; column++)
                    if (grid[row][column] != null && !grid[row][column].isEmpty())
                        e.ingredients.add(grid[row][column]);
            e.pattern = new ArrayList<>();
            e.keys = new LinkedHashMap<>();
            return;
        }

        e.type = "shaped";
        ShapedLayout original = shapedLayouts.remove(e);
        if (original != null) {
            e.pattern = new ArrayList<>(original.pattern());
            e.keys = new LinkedHashMap<>(original.keys());
            e.ingredients = new ArrayList<>();
            return;
        }
        e.pattern = new ArrayList<>();
        e.keys = new LinkedHashMap<>();
        Map<String, Character> letters = new LinkedHashMap<>();
        char nextLetter = 'A';
        for (int row = 0; row < 3; row++) {
            StringBuilder patternRow = new StringBuilder(3);
            for (int column = 0; column < 3; column++) {
                String itemId = grid[row][column];
                if (itemId == null || itemId.isEmpty()) {
                    patternRow.append(' ');
                    continue;
                }
                Character letter = letters.get(itemId);
                if (letter == null) {
                    letter = nextLetter++;
                    letters.put(itemId, letter);
                    e.keys.put(String.valueOf(letter), itemId);
                }
                patternRow.append(letter);
            }
            e.pattern.add(patternRow.toString());
        }
        e.ingredients = new ArrayList<>();
    }

    private record ShapedLayout(List<String> pattern, Map<String, String> keys) {
        private ShapedLayout(List<String> pattern, Map<String, String> keys) {
            this.pattern = pattern == null ? List.of() : new ArrayList<>(pattern);
            this.keys = keys == null ? Map.of() : new LinkedHashMap<>(keys);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (quickAddEditMode && button == 1) {
            quickAddEditMode = false;
            clearAndInit();
            return true;
        }
        if (hasRecipeScrollbar()
                && mx >= recipeScrollTrackX() - 3 && mx < recipeScrollTrackX() + 7
                && my >= recipeScrollTrackY() && my < recipeScrollTrackY() + recipeScrollTrackH()) {
            draggingRecipeScrollbar = true;
            updateRecipeScrollbar(my);
            return true;
        }
        // Keep recipe-preview clicks out of the full status-button hitbox.
        int btnStart = tableRight() - 113;
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(recipes.size(), scroll + vis); i++) {
            int rowTop = rowY(i);
            if (rowTop < listTop() || rowTop + ROW > listTop() + listH()) continue;
            if (my >= rowTop && my < rowTop + ROW && mx >= PAD && mx < btnStart) {
                selectedQuickAdd = -1;
                selectedRecipe = (selectedRecipe == i) ? -1 : i;
                int maxScroll = maxScroll();
                scroll = Math.max(0, Math.min(scroll, maxScroll));
                if (selectedRecipe >= 0) {
                    int visible = maxVisible();
                    if (selectedRecipe < scroll) scroll = selectedRecipe;
                    else if (selectedRecipe >= scroll + visible) scroll = selectedRecipe - visible + 1;
                    scroll = Math.max(0, Math.min(scroll, maxScroll));
                }
                clearAndInit();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (hasQuickAdd() && mx >= quickAddX() && mx < width - PAD
                && my >= listTop() && my < listTop() + listH() && quickAddMaxScroll() > 0) {
            int next = Math.max(0, Math.min(quickAddScroll - (int) v, quickAddMaxScroll()));
            if (next != quickAddScroll) {
                quickAddScroll = next;
                clearAndInit();
            }
            return true;
        }
        if (my < listTop() || my >= listTop() + listH() || !hasRecipeScrollbar())
            return super.mouseScrolled(mx, my, h, v);
        int next = Math.max(0, Math.min(scroll - (int) v, maxScroll()));
        if (next != scroll) {
            scroll = next;
            clearAndInit();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingRecipeScrollbar) {
            updateRecipeScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingRecipeScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateRecipeScrollbar(double mouseY) {
        int travel = recipeScrollTrackH() - recipeScrollThumbH();
        if (travel <= 0) return;
        int next = (int) Math.round((mouseY - recipeScrollTrackY() - recipeScrollThumbH() / 2.0)
                * maxScroll() / travel);
        next = Math.max(0, Math.min(next, maxScroll()));
        if (next != scroll) {
            scroll = next;
            clearAndInit();
        }
    }

    private String formatEntry(CustomRecipeEntry e) {
        String res   = toName(e.result);
        String count = e.count > 1 ? " ×" + e.count : "";
        int ingCount = "shaped".equalsIgnoreCase(e.type)
                ? (e.keys != null ? e.keys.size() : 0)
                : (e.ingredients != null ? e.ingredients.size() : 0);
        if (isCorrupted(e)) {
            return res + count;
        }
        if (firstEnabledConflict(e) != null) {
            return res + count;
        }
        return res + count + " — " + ingCount + " ingredient(s)";
    }

    /** Compact visual mode marker: a 3×3 grid for shaped, loose dots for shapeless. */
    private void drawRecipeTypeIcon(DrawContext ctx, int x, int y, boolean shaped, int color) {
        if (shaped) {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.LOCKED_INFO, x, y, 10, 14);
        } else {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.UNLOCKED_INFO, x, y, 16, 16);
        }
    }

    private void drawKnownByDefaultIcon(DrawContext ctx, int x, int y, boolean known) {
        CustomRecipeSprites.draw(ctx,
                known ? CustomRecipeSprites.KNOWN_BY_DEFAULT_INFO : CustomRecipeSprites.NOT_KNOWN_BY_DEFAULT_INFO,
                x, y, 20, 18);
    }

    /** Yellow pixel warning triangle, independent from any optional font glyph. */
    private void drawWarningIcon(DrawContext ctx, int x, int y) {
        int yellow = 0xFFFFD447;
        ctx.fill(x + 5, y, x + 7, y + 2, yellow);
        ctx.fill(x + 3, y + 2, x + 9, y + 4, yellow);
        ctx.fill(x + 2, y + 4, x + 10, y + 7, yellow);
        ctx.fill(x + 1, y + 7, x + 11, y + 9, yellow);
        ctx.fill(x + 5, y + 3, x + 7, y + 6, 0xFF3A3000);
        ctx.fill(x + 5, y + 7, x + 7, y + 8, 0xFF3A3000);
    }

    private void drawRecipeStatusIcon(DrawContext ctx, int x, int y, boolean corrupted, boolean conflict) {
        if (corrupted) {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.CORRUPTED, x, y, 16, 16);
        } else if (conflict) {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.WARNING_INFO, x, y, 16, 16);
        } else {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ACCEPT, x - 1, y - 1, 18, 18);
        }
    }

    private String shortRecipeId(String id) {
        if (id == null) return "unknown recipe";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private boolean isCorrupted(CustomRecipeEntry entry) {
        return Boolean.TRUE.equals(entry.corrupted)
                || (!parent.isServerManaged() && !RecipeIntegrity.missingItems(entry).isEmpty());
    }

    private List<String> missingItems(CustomRecipeEntry entry) {
        java.util.LinkedHashSet<String> missing = new java.util.LinkedHashSet<>();
        if (entry.missing_items != null) missing.addAll(entry.missing_items);
        missing.addAll(RecipeIntegrity.missingItems(entry));
        return new java.util.ArrayList<>(missing);
    }

    private String firstEnabledConflict(CustomRecipeEntry entry) {
        if (entry.conflicting_recipes == null) return null;
        return entry.conflicting_recipes.stream()
                .filter(id -> id != null && !parent.disabledRecipes.contains(id))
                .findFirst()
                .orElse(null);
    }

    private String firstEnabledSameShapeRecipe(CustomRecipeEntry entry) {
        if (entry.same_shape_recipes == null) return null;
        return entry.same_shape_recipes.stream()
                .filter(id -> id != null && !parent.disabledRecipes.contains(id))
                .findFirst()
                .orElse(null);
    }

    private boolean isActiveForThisScreen(CustomRecipeEntry entry) {
        return !Boolean.FALSE.equals(entry.enabled)
                && !Boolean.FALSE.equals(entry.server_enabled)
                && (!parent.isServerManaged() || parent.isAddedToCurrentWorld(entry));
    }

    private String toName(String id) {
        if (id == null || id.isEmpty()) return "?";
        String path = id.contains(":") ? id.split(":")[1] : id;
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : parts)
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /** Small scroll/list marker shown beside range indicators. */
    private void drawScrollIcon(DrawContext ctx, int x, int y) {
        CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SCROLLER_IDLE, x, y, 12, 15);
    }

    private void drawBox(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.drawHorizontalLine(x, x + w - 1, y, c);
        ctx.drawHorizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.drawVerticalLine(x, y, y + h - 1, c);
        ctx.drawVerticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean shouldPause() { return true; }

    /** Escape leaves Recipe Creator, so let its owner protect pending edits. */
    @Override public void close() {
        if (libraryPicker) client.setScreen(new CustomRecipesScreen(parent));
        else client.setScreen(parent);
    }
}
