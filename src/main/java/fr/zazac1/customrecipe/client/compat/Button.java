package fr.zazac1.customrecipe.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;

/** Builder and tooltip adapter for the older vanilla button. */
public final class Button extends net.minecraft.client.gui.widget.button.Button {
    private Tooltip tooltip;
    private Button(int x, int y, int w, int h, ITextComponent text, IPressable press) {
        super(x, y, w, h, text, press, (button, pose, mx, my) -> {
            Tooltip tip = ((Button) button).tooltip;
            if (tip != null && Minecraft.getInstance().screen != null) Minecraft.getInstance().screen.renderTooltip(pose, tip.text(), mx, my);
        });
    }
    /** Stretch the face between the atlas borders without repeating its lower bevel. */
    @Override public void blit(com.mojang.blaze3d.matrix.MatrixStack pose, int x, int y,
                               int u, int v, int width, int height) {
        if (height == 20 || height < 4) {
            super.blit(pose, x, y, u, v, width, height);
            return;
        }
        int border = Math.min(3, height / 2);
        super.blit(pose, x, y, u, v, width, border);
        int faceHeight = height - border * 2;
        if (faceHeight > 0) {
            net.minecraft.client.gui.AbstractGui.blit(pose, x, y + border, width, faceHeight,
                    (float) u, (float) (v + border), width, 20 - border * 2, 256, 256);
        }
        super.blit(pose, x, y + height - border, u, v + 20 - border, width, border);
    }
    public void setTooltip(Tooltip tooltip) { this.tooltip = tooltip; }
    public static Builder builder(ITextComponent text, IPressable press) { return new Builder(text, press); }
    public static final class Builder {
        private final ITextComponent text;
        private final IPressable press;
        private int x, y, w = 150, h = 20;
        private Tooltip tooltip;
        private Builder(ITextComponent text, IPressable press) { this.text = text; this.press = press; }
        public Builder bounds(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; return this; }
        public Builder tooltip(Tooltip tooltip) { this.tooltip = tooltip; return this; }
        public Button build() { Button button = new Button(x,y,w,h,text,press); button.tooltip=tooltip; return button; }
    }
}
