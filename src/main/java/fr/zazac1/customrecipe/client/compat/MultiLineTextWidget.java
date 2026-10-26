package fr.zazac1.customrecipe.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class MultiLineTextWidget extends AbstractWidget {
    private final Font font;
    private boolean centered;
    private int maxRows = Integer.MAX_VALUE;
    public MultiLineTextWidget(int x, int y, Component text, Font font) { super(x,y,font.width(text),font.lineHeight,text); this.font=font; this.active=false; }
    public MultiLineTextWidget setMaxWidth(int width) { this.width=width; return this; }
    public MultiLineTextWidget setCentered(boolean centered) { this.centered=centered; return this; }
    public MultiLineTextWidget setMaxRows(int rows) { this.maxRows=rows; return this; }
    @Override public void renderButton(PoseStack pose,int mx,int my,float delta) {
        MultiLineLabel label=MultiLineLabel.create(font,getMessage(),Math.max(1,width),maxRows);
        if (centered) label.renderCentered(pose,x+width/2,y); else label.renderLeftAligned(pose,x,y,font.lineHeight,0xFFFFFF);
    }
    @Override public void updateNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
