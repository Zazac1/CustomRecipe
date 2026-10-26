package fr.zazac1.customrecipe.client.compat;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/** Keeps the editor's rendering code shared with the 1.20 port. */
public abstract class LegacyScreen extends Screen {
    private final java.util.List<LegacyRenderable> renderables = new java.util.ArrayList<>();
    protected LegacyScreen(ITextComponent title) { super(title); }
    @Override public void init(net.minecraft.client.Minecraft client, int width, int height) { renderables.clear(); super.init(client,width,height); }
    protected void rebuildWidgets() { clearWidgets(); init(); }
    @Override public final void render(MatrixStack pose, int x, int y, float delta) { render(new GuiGraphics(pose), x, y, delta); }
    public void render(GuiGraphics graphics, int x, int y, float delta) {
        // Modern screens draw widgets and custom renderers in insertion order.
        // Drawing all widgets first lets later panel fills cover their text.
        for (LegacyRenderable renderer : renderables) renderer.render(graphics, x, y, delta);
    }
    public void renderBackground(GuiGraphics graphics) { super.renderBackground(graphics.pose()); }
    @FunctionalInterface protected interface LegacyRenderable { void render(GuiGraphics graphics, int x, int y, float delta); }
    protected void addLegacyRenderable(LegacyRenderable renderer) { renderables.add(renderer); }
    protected <T extends net.minecraft.client.gui.widget.Widget> T addRenderableWidget(T widget) {
        addButton(widget);
        renderables.add((graphics, x, y, delta) -> widget.render(graphics.pose(), x, y, delta));
        return widget;
    }
    protected void clearWidgets() { buttons.clear(); children.clear(); renderables.clear(); }
}
