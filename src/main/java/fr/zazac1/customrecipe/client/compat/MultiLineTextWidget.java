package fr.zazac1.customrecipe.client.compat;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.IBidiRenderer;
import net.minecraft.util.text.ITextComponent;

public final class MultiLineTextWidget extends Widget {
    private final FontRenderer font;
    private boolean centered;
    private int maxRows = Integer.MAX_VALUE;
    public MultiLineTextWidget(int x, int y, ITextComponent text, FontRenderer font) { super(x,y,font.width(text),font.lineHeight,text); this.font=font; this.active=false; }
    public MultiLineTextWidget setMaxWidth(int width) { this.width=width; return this; }
    public MultiLineTextWidget setCentered(boolean centered) { this.centered=centered; return this; }
    public MultiLineTextWidget setMaxRows(int rows) { this.maxRows=rows; return this; }
    @Override public void renderButton(MatrixStack pose,int mx,int my,float delta) {
        IBidiRenderer label=IBidiRenderer.create(font,getMessage(),Math.max(1,width),maxRows);
        if (centered) label.renderCentered(pose,x+width/2,y); else label.renderLeftAligned(pose,x,y,font.lineHeight,0xFFFFFF);
    }
}
