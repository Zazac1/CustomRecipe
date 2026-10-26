package fr.zazac1.customrecipe.client.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class EditBox extends net.minecraft.client.gui.components.EditBox {
    private Component hint;
    public EditBox(Font font,int x,int y,int w,int h,Component label) { super(font,x,y,w,h,label); }
    public void setHint(Component hint) { this.hint=hint; updateHint(); }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); }
    private void updateHint() { setSuggestion(getValue().isEmpty() && hint!=null ? hint.getString() : null); }
    @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose,int x,int y,float delta) { updateHint(); super.renderButton(pose,x,y,delta); }
}
