package fr.zazac1.customrecipe.client.compat;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

public final class EditBox extends net.minecraft.client.gui.widget.TextFieldWidget {
    private ITextComponent hint;
    public EditBox(FontRenderer font,int x,int y,int w,int h,ITextComponent label) { super(font,x,y,w,h,label); }
    public void setHint(ITextComponent hint) { this.hint=hint; updateHint(); }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); }
    private void updateHint() { setSuggestion(getValue().isEmpty() && hint!=null ? hint.getString() : null); }
    @Override public void renderButton(com.mojang.blaze3d.matrix.MatrixStack pose,int x,int y,float delta) { updateHint(); super.renderButton(pose,x,y,delta); }
}
