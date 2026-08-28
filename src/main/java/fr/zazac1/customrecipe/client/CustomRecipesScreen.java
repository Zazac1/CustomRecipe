package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
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

import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class CustomRecipesScreen extends Screen {

    private static final int PAD      = 8;
    private static final int ROW      = 20;
    private static final int MINI     = 18;
    private static final int DETAIL_H = 76;

    private final ConfigScreen parent;
    private final List<CustomRecipeEntry> recipes;
    private int scroll         = 0;
    private int selectedRecipe = -1;

    public CustomRecipesScreen(ConfigScreen parent) {
        super(Text.literal("My Recipes"));
        this.parent  = parent;
        this.recipes = parent.recipes;
    }

    private int listTop()     { return 28; }
    private int listH()       { return height - listTop() - (selectedRecipe >= 0 ? DETAIL_H + 4 : 0) - 48; }
    private int maxVisible()  { return Math.max(1, listH() / ROW); }
    private int rowY(int i)   { return listTop() + (i - scroll) * ROW; }
    private int detailY()     { return listTop() + listH() + 2; }
    private int detailGridX() { return width / 2 - (3 * MINI + 14 + MINI) / 2; }
    private int detailGridY() { return detailY() + 18; }

    @Override
    protected void init() {
        // ── Fills ────────────────────────────────────────────────────────
        addDrawable((ctx, mx, my, d) -> {
            ctx.fill(PAD, listTop(), width - PAD, listTop() + listH(), 0x88101010);
            drawBox(ctx, PAD, listTop(), width - PAD * 2, listH(), 0xFF505050);
            int vis = maxVisible();
            for (int i = scroll; i < Math.min(recipes.size(), scroll + vis); i++) {
                int y = rowY(i);
                if (y < listTop() || y + ROW > listTop() + listH()) continue;
                boolean sel = selectedRecipe == i;
                boolean on = isActiveForThisScreen(recipes.get(i));
                ctx.fill(PAD + 1, y, width - PAD - 112, y + ROW - 2,
                        on  ? (sel ? 0x44005533 : 0x22005500)
                            : (sel ? 0x44662200 : 0x44550000));
                // Icône de l'item résultat
                int statusX = width - PAD - 110;
                int iconX = statusX - 22;
                ctx.fill(iconX, y + 1, iconX + 18, y + ROW - 3, 0xFF303030);
                drawBox(ctx, iconX, y + 1, 18, ROW - 4, 0xFF707070);
                String resId = recipes.get(i).result;
                Identifier resultId = resId == null ? null : Identifier.tryParse(resId);
                if (resultId != null && Registries.ITEM.containsId(resultId)) {
                    ctx.drawItem(new ItemStack(Registries.ITEM.get(resultId)), iconX + 1, y + 2);
                }
            }
            if (selectedRecipe >= 0 && selectedRecipe < recipes.size())
                renderDetailFills(ctx, recipes.get(selectedRecipe));
        });

        // ── Titre ─────────────────────────────────────────────────────────
        MultilineTextWidget titleW = new MultilineTextWidget(PAD + 5, 10, title, textRenderer);
        titleW.setMaxWidth(width - 20);
        titleW.setCentered(true);
        addDrawableChild(titleW);

        // ── Lignes de recettes ────────────────────────────────────────────
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(recipes.size(), scroll + vis); i++) {
            final int idx = i;
            int y = rowY(i);
            if (y < listTop() || y + ROW > listTop() + listH()) continue;
            boolean sel = selectedRecipe == idx;

            MultilineTextWidget lbl = new MultilineTextWidget(
                    PAD + 4, y + (ROW - 8) / 2,
                    Text.literal((sel ? "▸ " : "  ") + formatEntry(recipes.get(i)))
                            .styled(s -> s.withColor(sel ? 0xFFEE88 : 0xDDDDDD)),
                    textRenderer);
            lbl.setMaxWidth(width - PAD * 2 - 138);
            lbl.setMaxRows(1);
            addDrawableChild(lbl);

            CustomRecipeEntry entry = recipes.get(idx);
            boolean addedToServer = !parent.isServerManaged() || !Boolean.FALSE.equals(entry.server_enabled);
            boolean en = isActiveForThisScreen(entry);
            addDrawableChild(ButtonWidget.builder(
                    !addedToServer ? Text.literal("Add to server").styled(s -> s.withColor(0xFFCC55))
                    : en ? Text.literal("Enabled").styled(s -> s.withColor(0x55FF55))
                         : Text.literal("Disabled").styled(s -> s.withColor(0xFF5555)),
                    b -> {
                        CustomRecipeEntry r = recipes.get(idx);
                        if (parent.isServerManaged() && Boolean.FALSE.equals(r.server_enabled)) {
                            r.server_enabled = Boolean.TRUE;
                            r.enabled = Boolean.TRUE;
                        } else {
                            r.enabled = !Boolean.FALSE.equals(r.enabled) ? Boolean.FALSE : Boolean.TRUE;
                        }
                        clearAndInit();
                    }
            ).dimensions(width - PAD - 110, y + 2, 88, ROW - 4).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
                    b -> {
                        if (selectedRecipe == idx) selectedRecipe = -1;
                        else if (selectedRecipe > idx) selectedRecipe--;
                        recipes.remove(idx);
                        clearAndInit();
                    }
            ).dimensions(width - PAD - 18, y + 2, 18, ROW - 4).build());
        }

        // ── Message vide ──────────────────────────────────────────────────
        if (recipes.isEmpty()) {
            MultilineTextWidget empty = new MultilineTextWidget(
                    PAD + 5, listTop() + 10,
                    Text.literal("No custom recipes yet. Use \"Create a Recipe\".").styled(s -> s.withColor(0x999999)),
                    textRenderer);
            empty.setMaxWidth(width - PAD * 2);
            empty.setCentered(true);
            addDrawableChild(empty);
        }

        // ── Scroll hint ───────────────────────────────────────────────────
        if (recipes.size() > maxVisible()) {
            int from = scroll + 1, to = Math.min(scroll + maxVisible(), recipes.size());
            MultilineTextWidget hint = new MultilineTextWidget(
                    PAD, listTop() + listH() - 10,
                    Text.literal("↕ " + from + "-" + to + "/" + recipes.size()).styled(s -> s.withColor(0x777777)),
                    textRenderer);
            hint.setMaxWidth(70);
            hint.setMaxRows(1);
            addDrawableChild(hint);
        }

        // ── Panneau de détail (widgets texte) ─────────────────────────────
        if (selectedRecipe >= 0 && selectedRecipe < recipes.size()) {
            CustomRecipeEntry e = recipes.get(selectedRecipe);
            String mode  = "shaped".equalsIgnoreCase(e.type) ? "[Shaped]" : "[Shapeless]";
            String label = mode + "  " + toName(e.result) + (e.count > 1 ? " ×" + e.count : "");
            MultilineTextWidget nameW = new MultilineTextWidget(
                    PAD + 4, detailY() + 4,
                    Text.literal(label).styled(s -> s.withColor(0xFFEE77)), textRenderer);
            nameW.setMaxWidth(width - PAD * 2 - 8);
            nameW.setMaxRows(1);
            addDrawableChild(nameW);

            // Flèche →
            MultilineTextWidget arrow = new MultilineTextWidget(
                    detailGridX() + 3 * MINI + 3, detailGridY() + MINI + (MINI - 8) / 2,
                    Text.literal("→"), textRenderer);
            arrow.setMaxWidth(12);
            arrow.setMaxRows(1);
            addDrawableChild(arrow);

            final int selected = selectedRecipe;
            boolean known = Boolean.TRUE.equals(e.known_by_default);
            addDrawableChild(ButtonWidget.builder(
                    known ? Text.literal("Known by default: ON").styled(s -> s.withColor(0x55FF55))
                          : Text.literal("Known by default: OFF").styled(s -> s.withColor(0xFFCC55)),
                    b -> {
                        CustomRecipeEntry recipe = recipes.get(selected);
                        recipe.known_by_default = !Boolean.TRUE.equals(recipe.known_by_default);
                        clearAndInit();
                    }
            ).dimensions(width - PAD - 142, detailY() + 30, 138, 18).build());
        }

        // ── Boutons du bas ────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Recipe"),
                b -> client.setScreen(new RecipeBuilderScreen(parent))
        ).dimensions(width / 2 - 100, height - 44, 200, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),
                b -> client.setScreen(parent)
        ).dimensions(width / 2 - 50, height - 22, 100, 18).build());
    }

    private void renderDetailFills(DrawContext ctx, CustomRecipeEntry e) {
        int dy = detailY();
        ctx.fill(PAD, dy, width - PAD, dy + DETAIL_H, 0x88101010);
        drawBox(ctx, PAD, dy, width - PAD * 2, DETAIL_H, 0xFF506070);

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

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Keep recipe-preview clicks out of the full status-button hitbox.
        int btnStart = width - PAD - 110;
        int vis = maxVisible();
        for (int i = scroll; i < Math.min(recipes.size(), scroll + vis); i++) {
            int rowTop = rowY(i);
            if (rowTop < listTop() || rowTop + ROW > listTop() + listH()) continue;
            if (my >= rowTop && my < rowTop + ROW && mx >= PAD && mx < btnStart) {
                selectedRecipe = (selectedRecipe == i) ? -1 : i;
                scroll = Math.max(0, Math.min(scroll, Math.max(0, recipes.size() - maxVisible())));
                clearAndInit();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double v) {
        scroll = Math.max(0, Math.min(scroll - (int) v, Math.max(0, recipes.size() - maxVisible())));
        clearAndInit();
        return true;
    }

    private String formatEntry(CustomRecipeEntry e) {
        String mode  = "shaped".equalsIgnoreCase(e.type) ? "[S]" : "[U]";
        String res   = toName(e.result);
        String count = e.count > 1 ? " ×" + e.count : "";
        int ingCount = "shaped".equalsIgnoreCase(e.type)
                ? (e.keys != null ? e.keys.size() : 0)
                : (e.ingredients != null ? e.ingredients.size() : 0);
        return mode + " " + res + count + "  —  " + ingCount + " ingredient(s)";
    }

    private boolean isActiveForThisScreen(CustomRecipeEntry entry) {
        return (!parent.isServerManaged() || !Boolean.FALSE.equals(entry.server_enabled))
                && !Boolean.FALSE.equals(entry.enabled);
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

    private void drawBox(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.drawHorizontalLine(x, x + w - 1, y, c);
        ctx.drawHorizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.drawVerticalLine(x, y, y + h - 1, c);
        ctx.drawVerticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean shouldPause() { return false; }
}
