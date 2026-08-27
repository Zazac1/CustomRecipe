package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class WelcomeScreen extends Screen {

    /**
     * L'écran vers lequel revenir après le welcome.
     * Peut être TitleScreen (si affiché au lancement) ou ConfigScreen.
     */
    private final Screen returnTo;

    public WelcomeScreen(Screen returnTo) {
        super(Component.literal("Welcome to Custom Recipe!"));
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
        MultiLineTextWidget titleW = new MultiLineTextWidget(lx, py + 8, title, font);
        titleW.setMaxWidth(inner);
        titleW.setMaxRows(1);
        addRenderableWidget(titleW);

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
        addRenderableWidget(Button.builder(
                Component.literal("\u25b8  Built-in Recipes"),
                b -> {
                    dismiss();
                    ConfigScreen cs = configScreen();
                    minecraft.gui.setScreen(new BuiltinRecipesScreen(cs));
                }
        ).bounds(btnX, py + 100, btnW, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("\u25b8  Create a Recipe"),
                b -> {
                    dismiss();
                    ConfigScreen cs = configScreen();
                    minecraft.gui.setScreen(new RecipeBuilderScreen(cs));
                }
        ).bounds(btnX, py + 124, btnW, 20).build());

        // ── Dismiss ──────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.literal("\u2713  Got it, don't show again"),
                b -> { dismiss(); minecraft.gui.setScreen(returnTo); }
        ).bounds(btnX, py + 152, btnW, 20).build());
    }

    /** Retourne ou crée un ConfigScreen pour la navigation. */
    private ConfigScreen configScreen() {
        if (returnTo instanceof ConfigScreen cs) return cs;
        return new ConfigScreen(returnTo);
    }

    /** Ajoute un bloc de texte left-aligné avec maxWidth. */
    private void addLine(int x, int y, int maxW, String text, int color) {
        MultiLineTextWidget w = new MultiLineTextWidget(x, y,
                Component.literal(text).withColor(color), font);
        w.setMaxWidth(maxW);
        w.setMaxRows(2);
        addRenderableWidget(w);
    }

    /** Sauvegarde seen_welcome = true pour ne plus afficher cet écran. */
    private void dismiss() {
        ModConfig cfg = ConfigLoader.get();
        cfg.seen_welcome = true;
        ConfigLoader.saveAndInvalidate(cfg);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Fond bleuté distinctif
        ctx.fillGradient(0, 0, width, height, 0xD0101828, 0xE0101828);

        // Panel
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        ctx.fill(px, py, px + pw, py + ph, 0x88000020);
        drawBox(ctx, px, py, pw, ph, 0xFF4466CC);
        // Séparateur sous le titre
        ctx.fill(px + 8, py + 21, px + pw - 8, py + 22, 0x554466CC);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent k) {
        if (k.key() == 256) { // Escape → dismiss
            dismiss();
            minecraft.gui.setScreen(returnTo);
            return true;
        }
        return super.keyPressed(k);
    }

    private void drawBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.horizontalLine(x, x + w - 1, y, c);
        ctx.horizontalLine(x, x + w - 1, y + h - 1, c);
        ctx.verticalLine(x, y, y + h - 1, c);
        ctx.verticalLine(x + w - 1, y, y + h - 1, c);
    }

    @Override public boolean isPauseScreen() { return false; }
}
