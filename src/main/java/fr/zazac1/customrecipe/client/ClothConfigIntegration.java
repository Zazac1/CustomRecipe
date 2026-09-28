package fr.zazac1.customrecipe.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * Forge configuration integration while Cloth Config remains a required Forge
 * dependency. The recipe editor is the configuration screen because it exposes
 * workflows which are not simple settings.
 */
public final class ClothConfigIntegration {
    private ClothConfigIntegration() {}

    /** Opens the complete editor directly from Forge's Mods > Config button. */
    public static Screen createScreen(Screen parent) {
        return ConfigScreen.fromModMenu(parent);
    }
}
