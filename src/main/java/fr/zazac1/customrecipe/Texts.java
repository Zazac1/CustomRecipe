package fr.zazac1.customrecipe;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Text factories and chat delivery before the Component static factories. */
public final class Texts {
    private Texts() {}
    public static MutableComponent literal(String text) { return new TextComponent(text); }
    public static MutableComponent translatable(String key, Object... args) { return new TranslatableComponent(key,args); }
    public static MutableComponent empty() { return new TextComponent(""); }
    public static void send(Player player, Component text) { player.displayClientMessage(text,false); }
}
