package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class WelcomeScreen extends Screen {

    /**
     * L'écran vers lequel revenir après le welcome.
     * Peut être TitleScreen (si affiché au lancement) ou ConfigScreen.
     */
    private final Screen returnTo;

    public WelcomeScreen(Screen returnTo) {
        super(Text.literal("Welcome to Custom Recipe!"));
        this.returnTo = returnTo;
    }

    // ── Layout (relatif au panel, recalculé à chaque init) ───────────────
    private int panelW()  { return Math.min(380, width - 40); }
    private int panelH()  { return 176; }
    private int panelX()  { return width  / 2 - panelW() / 2; }
    private int panelY()  { return height / 2 - panelH() / 2; }
    private int panelCX() { return panelX() + panelW() / 2; }

    @Override
    protected void init() {
        int px = panelX(), py = panelY(), pw = panelW(), cx = panelCX();
        int inner = pw - 24;       // largeur du contenu (12px padding de chaque côté)
        int lx    = px + 12;       // X gauche du contenu
        int btnW  = Math.min(250, inner);
        int btnX  = cx - btnW / 2;

        // ── Titre en haut à gauche du panel ─────────────────────────────
        MultilineTextWidget titleW = new MultilineTextWidget(lx, py + 8, title, textRenderer);
        titleW.setMaxWidth(inner);
        titleW.setMaxRows(1);
        addDrawableChild(titleW);

        // ── Description ──────────────────────────────────────────────────
        addLine(lx, py + 28, inner,
                "This mod lets you craft any vanilla or modded item.",
                0xCCCCCC);

        addLine(lx, py + 52, inner,
                "\u25ba Built-in Recipes \u2014 enable or disable the 5 pre-made recipes.",
                0xFFEE88);

        addLine(lx, py + 72, inner,
                "\u25ba Create a Recipe \u2014 build your own shapeless or shaped recipe.",
                0xFFEE88);

        // ── Boutons de navigation ────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u25b8  Built-in Recipes"),
                b -> {
                    dismiss();
                    ConfigScreen cs = configScreen();
                    client.setScreen(new BuiltinRecipesScreen(cs));
                }
        ).dimensions(btnX, py + 100, btnW, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u25b8  Create a Recipe"),
                b -> {
                    dismiss();
                    ConfigScreen cs = configScreen();
                    client.setScreen(new RecipeBuilderScreen(cs));
                }
        ).dimensions(btnX, py + 124, btnW, 20).build());

        // ── Dismiss ──────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2713  Got it, don't show again"),
                b -> { dismiss(); client.setScreen(returnTo); }
        ).dimensions(btnX, py + 152, btnW, 20).build());
    }

    /** Retourne ou crée un ConfigScreen pour la navigation. */
    private ConfigScreen configScreen() {
        if (returnTo instanceof ConfigScreen cs) return cs;
        return new ConfigScreen(returnTo);
    }

    /** Ajoute un bloc de texte left-aligné avec maxWidth. */
    private void addLine(int x, int y, int maxW, String text, int color) {
        MultilineTextWidget w = new MultilineTextWidget(x, y,
                Text.literal(text).withColor(color), textRenderer);
        w.setMaxWidth(maxW);
        w.setMaxRows(2);
        addDrawableChild(w);
    }

    /** Sauvegarde seen_welcome = true pour ne plus afficher cet écran. */
    private void dismiss() {
        ModConfig cfg = ConfigLoader.get();
        cfg.seen_welcome = true;
        ConfigLoader.saveAndInvalidate(cfg);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Fond bleuté distinctif
        ctx.fillGradient(0, 0, width, height, 0xD0101828, 0xE0101828);

        // Panel
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        ctx.fill(px, py, px + pw, py + ph, 0x88000020);
        drawBox(ctx, px, py, pw, ph, 0xFF4466CC);
        // Séparateur sous le titre
        ctx.fill(px + 8, py + 21, px + pw - 8, py + 22, 0x554466CC);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape → dismiss
            dismiss();
            client.setScreen(returnTo);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawBox(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.drawHorizontalLine(x, x + w - 1, y, c);
        ctx.drawHorizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.drawVerticalLine(x, y, y + h - 1, c);
        ctx.drawVerticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean shouldPause() { return false; }
}
