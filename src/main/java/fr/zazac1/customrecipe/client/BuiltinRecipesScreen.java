package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.RecipeIntegrity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class BuiltinRecipesScreen extends Screen {

    /** { id, displayName, resultItemId } */
    private static final String[][] RECIPES = {
            {"totem_of_undying",       "Totem of Undying",       "minecraft:totem_of_undying"},
            {"enchanted_golden_apple", "Enchanted Golden Apple", "minecraft:enchanted_golden_apple"},
            {"elytra",                 "Elytra",                 "minecraft:elytra"},
            {"experience_bottle",      "Bottle o' Enchanting",   "minecraft:experience_bottle"},
            {"heavy_core",             "Heavy Core",             "minecraft:heavy_core"},
    };

    /** Ingredient grids [recipeIndex][row][col] — null = slot vide */
    private static final String[][][] GRIDS = {
        // 0 totem_of_undying: _E_ / GGG / _G_  E=emerald G=gold_block
        {{null, "minecraft:emerald", null},
         {"minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"},
         {null, "minecraft:gold_block", null}},
        // 1 enchanted_golden_apple: GGG / GAG / GGG  G=gold_block A=apple
        {{"minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"},
         {"minecraft:gold_block", "minecraft:apple", "minecraft:gold_block"},
         {"minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"}},
        // 2 elytra: _S_ / MFM / M_M  S=string M=phantom_membrane F=feather
        {{null, "minecraft:string", null},
         {"minecraft:phantom_membrane", "minecraft:feather", "minecraft:phantom_membrane"},
         {"minecraft:phantom_membrane", null, "minecraft:phantom_membrane"}},
        // 3 experience_bottle: _L_ / EBE / _L_  L=lapis_lazuli E=emerald B=glass_bottle
        {{null, "minecraft:lapis_lazuli", null},
         {"minecraft:emerald", "minecraft:glass_bottle", "minecraft:emerald"},
         {null, "minecraft:lapis_lazuli", null}},
        // 4 heavy_core: _N_ / NBN / _N_  N=netherite_ingot B=breeze_rod
        {{null, "minecraft:netherite_ingot", null},
         {"minecraft:netherite_ingot", "minecraft:breeze_rod", "minecraft:netherite_ingot"},
         {null, "minecraft:netherite_ingot", null}},
    };

    private static final int[] RESULT_COUNTS = {1, 1, 1, 1, 1};

    static int quickAddCount() { return RECIPES.length; }
    static String quickAddId(int index) { return RECIPES[index][0]; }
    static String quickAddName(int index) {
        Identifier id = Identifier.tryParse(RECIPES[index][2]);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? ClientItemStacks.fromItem(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString() : RECIPES[index][1];
    }
    static String quickAddResult(int index) { return RECIPES[index][2]; }

    /** Converts an old built-in recipe into an independent custom-recipe entry. */
    static CustomRecipeEntry createQuickAddRecipe(int index) {
        CustomRecipeEntry entry = new CustomRecipeEntry();
        entry.id = UUID.randomUUID().toString();
        entry.type = "shaped";
        entry.result = RECIPES[index][2];
        entry.count = RESULT_COUNTS[index];
        entry.enabled = Boolean.TRUE;

        Map<String, Character> itemKeys = new LinkedHashMap<>();
        char next = 'A';
        for (String[] gridRow : GRIDS[index]) {
            StringBuilder patternRow = new StringBuilder();
            for (String itemId : gridRow) {
                if (itemId == null) {
                    patternRow.append(' ');
                } else {
                    if (!itemKeys.containsKey(itemId)) itemKeys.put(itemId, next++);
                    patternRow.append(itemKeys.get(itemId));
                }
            }
            entry.pattern.add(patternRow.toString());
        }
        for (Map.Entry<String, Character> key : itemKeys.entrySet()) {
            entry.keys.put(String.valueOf(key.getValue()), key.getKey());
        }
        RecipeIntegrity.rememberRequiredMods(entry);
        return entry;
    }

    private static final int PAD     = 8;
    private static final int ROW     = 20;
    private static final int HEADER_H = 16;
    private static final int MINI    = 18;  // mini-slot size for detail grid
    private static final int DETAIL_H = 76; // height of detail panel

    private final ConfigScreen parent;
    private final List<String> disabled;
    private final List<String> knownByDefault;
    private int scroll         = 0;
    private int selectedRecipe = -1;

    public BuiltinRecipesScreen(ConfigScreen parent) {
        super(Component.translatable("customrecipe.screen.built_in"));
        this.parent   = parent;
        this.disabled = parent.disabled;
        this.knownByDefault = parent.knownByDefaultBuiltin;
    }

    private int listTop()    { return 28; }
    private int listH()      { return height - listTop() - (selectedRecipe >= 0 ? DETAIL_H + 4 : 0) - 30; }
    private int rowsTop()    { return listTop() + HEADER_H; }
    private int maxVisible() { return Math.max(1, (listH() - HEADER_H) / ROW); }
    private int rowY(int i)  { return rowsTop() + (i - scroll) * ROW; }
    private int detailY()    { return listTop() + listH() + 2; }
    private int itemIconX()  { return PAD + 14; }
    private int knownIconX() { return PAD + 60; }
    private int recipeX()    { return PAD + 101; }
    private int stateX()     { return width - PAD - 92; }

    // Centre du mini-grille dans le panneau de détail
    private int detailGridX() { return width / 2 - (3 * MINI + 14 + MINI) / 2; }
    private int detailGridY() { return detailY() + 18; }

    @Override
    protected void init() {
        addRenderableOnly((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, parent.target(),
                parent.target().isWorld() ? parent.target().displayName() : "Global Library"));
        // ── Fills + icônes + mini-grille ─────────────────────────────────
        addRenderableOnly((ctx, mx, my, d) -> {
            // Liste background
            ctx.fill(PAD, listTop(), width - PAD, listTop() + listH(), 0x88101010);
            drawBox(ctx, PAD, listTop(), width - PAD * 2, listH(), 0xFF505050);
            ctx.fill(PAD + 1, listTop() + 1, width - PAD - 1, rowsTop() - 1, 0xDD252B2A);
            ctx.horizontalLine(PAD + 1, width - PAD - 2, rowsTop() - 1, 0xFF596462);
            int[] columns = {PAD + 45, PAD + 96, stateX() - 4};
            for (int column : columns)
                ctx.verticalLine(column, listTop() + 1, listTop() + listH() - 2, 0xFF39433F);
            int headerColor = 0xFFB8C7C1;
            ctx.text(font, Component.translatable("customrecipe.table.output"), PAD + 4, listTop() + 4, headerColor, false);
            ctx.text(font, Component.translatable("customrecipe.table.known"), PAD + 48, listTop() + 4, headerColor, false);
            ctx.text(font, Component.translatable("customrecipe.table.recipe"), recipeX(), listTop() + 4, headerColor, false);
            ctx.text(font, Component.translatable("customrecipe.table.state"), stateX() + 28, listTop() + 4, headerColor, false);
            if (RECIPES.length > maxVisible()) drawScrollIcon(ctx, PAD, listTop() + listH() - 15);

            int vis = maxVisible();
            for (int i = scroll; i < Math.min(RECIPES.length, scroll + vis); i++) {
                int y = rowY(i);
                if (y < listTop() || y + ROW > listTop() + listH()) continue;
                boolean dis = disabled.contains(RECIPES[i][0]);
                boolean sel = selectedRecipe == i;
                int rowColor = dis ? (sel ? 0x44662200 : 0x44550000)
                        : (sel ? 0x44005533 : 0x22005500);
                // Keep the reactive state tint visible behind the state button as well.
                ctx.fillGradient(PAD + 1, y, width - PAD - 1, y + ROW - 2,
                        rowColor, (rowColor & 0x00FFFFFF) | 0x18000000);
                var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(RECIPES[i][2]));
                if (item != null && item != Items.AIR)
                    ctx.item(ClientItemStacks.fromItem(item), itemIconX(), y + 2);
                CustomRecipeSprites.draw(ctx,
                        knownByDefault.contains(RECIPES[i][0])
                                ? CustomRecipeSprites.KNOWN_BY_DEFAULT_INFO
                                : CustomRecipeSprites.NOT_KNOWN_BY_DEFAULT_INFO,
                        knownIconX(), y + 1, 20, 18);
            }

            // Panneau de détail
            if (selectedRecipe >= 0) {
                int dy = detailY();
                ctx.fill(PAD, dy, width - PAD, dy + DETAIL_H, 0x88101010);
                drawBox(ctx, PAD, dy, width - PAD * 2, DETAIL_H, 0xFF607050);
                var detailItem = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(RECIPES[selectedRecipe][2]));
                if (detailItem != null && detailItem != Items.AIR)
                    ctx.item(ClientItemStacks.fromItem(detailItem), PAD + 4, dy + 2);
                CustomRecipeSprites.draw(ctx,
                        knownByDefault.contains(RECIPES[selectedRecipe][0])
                                ? CustomRecipeSprites.KNOWN_BY_DEFAULT_INFO
                                : CustomRecipeSprites.NOT_KNOWN_BY_DEFAULT_INFO,
                        PAD + 24, dy + 1, 20, 18);

                int gx = detailGridX(), gy = detailGridY();
                String[][] grid = GRIDS[selectedRecipe];
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int sx = gx + c * MINI, sy = gy + r * MINI;
                        ctx.fill(sx + 1, sy + 1, sx + MINI - 1, sy + MINI - 1, 0xFF3A3A3A);
                        drawBox(ctx, sx, sy, MINI, MINI, 0xFF555555);
                        String id = grid[r][c];
                        if (id != null) {
                            var it = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(id));
                            if (it != null && it != Items.AIR)
                                ctx.item(ClientItemStacks.fromItem(it), sx + 1, sy + 1);
                        }
                    }
                }
                // Slot résultat (rangée du milieu, à droite de la flèche)
                int resultX = gx + 3 * MINI + 14;
                int resultY = gy + MINI;
                ctx.fill(resultX + 1, resultY + 1, resultX + MINI - 1, resultY + MINI - 1, 0xFF3A3A3A);
                drawBox(ctx, resultX, resultY, MINI, MINI, 0xFF908830);
                var ri = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(RECIPES[selectedRecipe][2]));
                if (ri != null && ri != Items.AIR)
                    ctx.item(ClientItemStacks.fromItem(ri), resultX + 1, resultY + 1);
            }
        });

        // ── Lignes de recettes ────────────────────────────────────────────
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(RECIPES.length, scroll + vis); i++) {
            int y = rowY(i);
            if (y < listTop() || y + ROW > listTop() + listH()) continue;

            final String id = RECIPES[i][0];
            boolean dis = disabled.contains(id);
            boolean sel = selectedRecipe == i;

            MultiLineTextWidget lbl = new MultiLineTextWidget(
                    recipeX(), y + (ROW - 8) / 2,
                    Component.literal(quickAddName(i))
                            .withColor(dis ? (sel ? 0xBBAAAA : 0x888888)
                                           : (sel ? 0xFFEE88 : 0xE0E0E0)),
                    font);
            lbl.setMaxWidth(stateX() - recipeX() - 8);
            lbl.setMaxRows(1);
            addRenderableWidget(lbl);

            addRenderableWidget(Button.builder(
                    dis ? Component.translatable("customrecipe.state.disabled").withColor(0xFF5555)
                        : Component.translatable("customrecipe.state.enabled").withColor(0x55FF55),
                    b -> toggle(id)
            ).bounds(stateX(), y + 2, 88, ROW - 4).build());
        }

        // ── Indicateur de scroll ──────────────────────────────────────────
        if (RECIPES.length > maxVisible()) {
            int from = scroll + 1, to = Math.min(scroll + maxVisible(), RECIPES.length);
            MultiLineTextWidget hint = new MultiLineTextWidget(
                    PAD + 14, listTop() + listH() - 10,
                    Component.literal(from + "-" + to + "/" + RECIPES.length).withColor(0x666666),
                    font);
            hint.setMaxWidth(70);
            hint.setMaxRows(1);
            addRenderableWidget(hint);
        }

        // ── Panneau de détail (widgets texte) ─────────────────────────────
        if (selectedRecipe >= 0) {
            int cnt = RESULT_COUNTS[selectedRecipe];
            String label = RECIPES[selectedRecipe][1] + (cnt > 1 ? "  ×" + cnt : "");
            MultiLineTextWidget nameW = new MultiLineTextWidget(
                    PAD + 50, detailY() + 4,
                    Component.literal(label).withColor(0xFFEE77), font);
            nameW.setMaxWidth(width - PAD * 2 - 8);
            nameW.setMaxRows(1);
            addRenderableWidget(nameW);

            // Flèche →
            int gx = detailGridX(), gy = detailGridY();
            MultiLineTextWidget arrow = new MultiLineTextWidget(
                    gx + 3 * MINI + 3, gy + MINI + (MINI - 8) / 2,
                    Component.literal("→"), font);
            arrow.setMaxWidth(12);
            arrow.setMaxRows(1);
            addRenderableWidget(arrow);

            String id = RECIPES[selectedRecipe][0];
            boolean known = knownByDefault.contains(id);
            int knownButtonX = stateX() + 34;
            addRenderableWidget(Button.builder(Component.empty(),
                    b -> toggleKnownByDefault(id)
            ).bounds(knownButtonX, detailY() + 30, 20, 20).build());
            addRenderableOnly((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                    known ? CustomRecipeSprites.KNOWN_BY_DEFAULT : CustomRecipeSprites.NOT_KNOWN_BY_DEFAULT,
                    knownButtonX, detailY() + 30, 20, 20));
        }

        // ── Bouton Retour ─────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.back"),
                b -> minecraft.gui.setScreen(parent)
        ).bounds(width / 2 - 50, height - 24, 100, 18).build());
    }

    private void toggle(String id) {
        if (!disabled.remove(id)) disabled.add(id);
        rebuildWidgets();
    }

    private void toggleKnownByDefault(String id) {
        if (!knownByDefault.remove(id)) knownByDefault.add(id);
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean focused) {
        double mx = click.x(), my = click.y();
        int toggleStart = stateX();
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(RECIPES.length, scroll + vis); i++) {
            int rowTop = rowY(i);
            if (rowTop < listTop() || rowTop + ROW > listTop() + listH()) continue;
            // Clic sur la zone du nom (gauche du bouton toggle)
            if (my >= rowTop && my < rowTop + ROW && mx >= PAD && mx < toggleStart) {
                selectedRecipe = (selectedRecipe == i) ? -1 : i;
                // Reclamper le scroll si la liste rétrécit
                scroll = Math.max(0, Math.min(scroll, Math.max(0, RECIPES.length - maxVisible())));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(scroll - (int) v,
                Math.max(0, RECIPES.length - maxVisible())));
        rebuildWidgets();
        return true;
    }

    /** Small scroll/list marker shown beside range indicators. */
    private void drawScrollIcon(GuiGraphicsExtractor ctx, int x, int y) {
        CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SCROLLER_IDLE, x, y, 12, 15);
    }

    private void drawBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.horizontalLine(x, x + w - 1, y, c);
        ctx.horizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.verticalLine(x, y, y + h - 1, c);
        ctx.verticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean isPauseScreen() { return true; }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
