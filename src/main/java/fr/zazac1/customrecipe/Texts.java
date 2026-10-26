package fr.zazac1.customrecipe;

import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.PlayerEntity;

/** Text factories and chat delivery before the ITextComponent static factories. */
public final class Texts {
    private Texts() {}
    public static IFormattableTextComponent literal(String text) { return new StringTextComponent(text); }
    public static IFormattableTextComponent translatable(String key, Object... args) { return new TranslationTextComponent(key,args); }
    public static IFormattableTextComponent empty() { return new StringTextComponent(""); }
    public static void send(PlayerEntity player, ITextComponent text) { player.displayClientMessage(text,false); }
}
