package fr.isaac.customrecipe.client;

import net.minecraft.client.Minecraft;

/** Reloads the integrated server after saving the local ModMenu configuration. */
final class LocalRecipeReload {
    private LocalRecipeReload() {}

    static void afterSave(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "reload"));
    }
}
