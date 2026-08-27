package fr.isaac.customrecipe.client;

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
import java.util.List;

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

    private static final int PAD     = 8;
    private static final int ROW     = 22;
    private static final int MINI    = 18;  // mini-slot size for detail grid
    private static final int DETAIL_H = 76; // height of detail panel

    private final ConfigScreen parent;
    private final List<String> disabled;
    private int scroll         = 0;
    private int selectedRecipe = -1;

    public BuiltinRecipesScreen(ConfigScreen parent) {
        super(Component.literal("Built-in Recipes"));
        this.parent   = parent;
        this.disabled = parent.disabled;
    }

    private int listTop()    { return 28; }
    private int listH()      { return height - listTop() - (selectedRecipe >= 0 ? DETAIL_H + 4 : 0) - 30; }
    private int maxVisible() { return Math.max(1, listH() / ROW); }
    private int rowY(int i)  { return listTop() + (i - scroll) * ROW; }
    private int detailY()    { return listTop() + listH() + 2; }

    // Centre du mini-grille dans le panneau de détail
    private int detailGridX() { return width / 2 - (3 * MINI + 14 + MINI) / 2; }
    private int detailGridY() { return detailY() + 18; }

    @Override
    protected void init() {
        // ── Fills + icônes + mini-grille ─────────────────────────────────
        addRenderableOnly((ctx, mx, my, d) -> {
            // Liste background
            ctx.fill(PAD, listTop(), width - PAD, listTop() + listH(), 0x88101010);
            drawBox(ctx, PAD, listTop(), width - PAD * 2, listH(), 0xFF505050);

            int vis = maxVisible();
            for (int i = scroll; i < Math.min(RECIPES.length, scroll + vis); i++) {
                int y = rowY(i);
                if (y < listTop() || y + ROW > listTop() + listH()) continue;
                boolean dis = disabled.contains(RECIPES[i][0]);
                boolean sel = selectedRecipe == i;
                ctx.fill(PAD + 1, y, width - PAD - 78, y + ROW - 2,
                        dis ? (sel ? 0x44662200 : 0x44550000)
                            : (sel ? 0x44005533 : 0x22005500));
                // Icône résultat
                var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(RECIPES[i][2]));
                if (item != null && item != Items.AIR)
                    ctx.item(ClientItemStacks.fromItem(item), width - PAD - 78 - 18, y + 3);
            }

            // Panneau de détail
            if (selectedRecipe >= 0) {
                int dy = detailY();
                ctx.fill(PAD, dy, width - PAD, dy + DETAIL_H, 0x88101010);
                drawBox(ctx, PAD, dy, width - PAD * 2, DETAIL_H, 0xFF607050);

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

        // ── Titre ────────────────────────────────────────────────────────
        MultiLineTextWidget titleW = new MultiLineTextWidget(PAD, 10, title, font);
        titleW.setMaxWidth(width - 20);
        titleW.setCentered(true);
        addRenderableWidget(titleW);

        // ── Lignes de recettes ────────────────────────────────────────────
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(RECIPES.length, scroll + vis); i++) {
            int y = rowY(i);
            if (y < listTop() || y + ROW > listTop() + listH()) continue;

            final String id = RECIPES[i][0];
            boolean dis = disabled.contains(id);
            boolean sel = selectedRecipe == i;

            MultiLineTextWidget lbl = new MultiLineTextWidget(
                    PAD + 4, y + (ROW - 8) / 2,
                    Component.literal((sel ? "▸ " : "  ") + RECIPES[i][1])
                            .withColor(dis ? (sel ? 0xBBAAAA : 0x888888)
                                           : (sel ? 0xFFEE88 : 0xE0E0E0)),
                    font);
            lbl.setMaxWidth(width - PAD * 2 - 90);
            lbl.setMaxRows(1);
            addRenderableWidget(lbl);

            addRenderableWidget(Button.builder(
                    dis ? Component.literal("Disabled").withColor(0xFF5555)
                        : Component.literal("Enabled").withColor(0x55FF55),
                    b -> toggle(id)
            ).bounds(width - PAD - 76, y + 3, 76, ROW - 6).build());
        }

        // ── Indicateur de scroll ──────────────────────────────────────────
        if (RECIPES.length > maxVisible()) {
            int from = scroll + 1, to = Math.min(scroll + maxVisible(), RECIPES.length);
            MultiLineTextWidget hint = new MultiLineTextWidget(
                    PAD, listTop() + listH() - 10,
                    Component.literal("↕ " + from + "-" + to + "/" + RECIPES.length).withColor(0x666666),
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
                    PAD + 4, detailY() + 4,
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
        }

        // ── Bouton Retour ─────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("Back"),
                b -> minecraft.gui.setScreen(parent)
        ).bounds(width / 2 - 50, height - 24, 100, 18).build());
    }

    private void toggle(String id) {
        if (!disabled.remove(id)) disabled.add(id);
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
        int toggleStart = width - PAD - 76;
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

    private void drawBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.horizontalLine(x, x + w - 1, y, c);
        ctx.horizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.verticalLine(x, y, y + h - 1, c);
        ctx.verticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean isPauseScreen() { return false; }
}
