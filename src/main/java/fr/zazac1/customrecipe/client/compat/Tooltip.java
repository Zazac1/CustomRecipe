package fr.zazac1.customrecipe.client.compat;

import net.minecraft.network.chat.Component;

public record Tooltip(Component text) {
    public static Tooltip create(Component text) { return new Tooltip(text); }
}
