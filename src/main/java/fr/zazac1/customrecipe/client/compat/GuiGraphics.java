package fr.zazac1.customrecipe.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;

/** Rendering adapter for the MatrixStack GUI used before Minecraft 1.20. */
public final class GuiGraphics extends AbstractGui {
    private final MatrixStack pose;
    public GuiGraphics(MatrixStack pose) { this.pose = pose; }
    public MatrixStack pose() { return pose; }
    public void hLine(int x1, int x2, int y, int color) { super.hLine(pose,x1,x2,y,color); }
    public void vLine(int x, int y1, int y2, int color) { super.vLine(pose,x,y1,y2,color); }
    public void fill(int x1, int y1, int x2, int y2, int color) { fill(pose, x1, y1, x2, y2, color); }
    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) { fillGradient(pose, x1, y1, x2, y2, top, bottom); }
    public void drawCenteredString(FontRenderer font, ITextComponent text, int x, int y, int color) { drawCenteredString(pose, font, text, x, y, color); }
    public void drawCenteredString(FontRenderer font, String text, int x, int y, int color) { drawCenteredString(pose, font, text, x, y, color); }
    public int drawString(FontRenderer font, ITextComponent text, int x, int y, int color) { return drawString(font, text, x, y, color, true); }
    public int drawString(FontRenderer font, String text, int x, int y, int color) { return drawString(font, text, x, y, color, true); }
    public int drawString(FontRenderer font, ITextComponent text, int x, int y, int color, boolean shadow) { return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color); }
    public int drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) { return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color); }
    public void blit(ResourceLocation texture, int x, int y, float u, float v, int w, int h, int tw, int th) {
        Minecraft.getInstance().getTextureManager().bind(texture);
        RenderSystem.color4f(1, 1, 1, 1);
        blit(pose, x, y, u, v, w, h, tw, th);
    }
    public void renderItem(ItemStack stack, int x, int y) { Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y); }
    public void renderFakeItem(ItemStack stack, int x, int y) { renderItem(stack, x, y); }
    public void renderItemDecorations(FontRenderer font, ItemStack stack, int x, int y) { renderItemDecorations(font, stack, x, y, null); }
    public void renderItemDecorations(FontRenderer font, ItemStack stack, int x, int y, String count) { Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x, y, count); }
    public void renderTooltip(FontRenderer font, ITextComponent text, int x, int y) {
        if (Minecraft.getInstance().screen != null) Minecraft.getInstance().screen.renderTooltip(pose, text, x, y);
    }
}
