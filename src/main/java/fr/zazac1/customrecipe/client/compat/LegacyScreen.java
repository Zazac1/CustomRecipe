package fr.zazac1.customrecipe.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keeps the editor's rendering code shared with the 1.20 port. */
public abstract class LegacyScreen extends Screen {
    protected LegacyScreen(Component title) { super(title); }
    @Override public final void render(PoseStack pose, int x, int y, float delta) { render(new GuiGraphics(pose), x, y, delta); }
    public void render(GuiGraphics graphics, int x, int y, float delta) { super.render(graphics.pose(), x, y, delta); }
    public void renderBackground(GuiGraphics graphics) { super.renderBackground(graphics.pose()); }
    @FunctionalInterface protected interface LegacyRenderable { void render(GuiGraphics graphics, int x, int y, float delta); }
    protected void addLegacyRenderable(LegacyRenderable renderer) { addRenderableOnly((pose,x,y,delta) -> renderer.render(new GuiGraphics(pose),x,y,delta)); }
}
