package fr.zazac1.customrecipe;

import net.minecraft.resources.ResourceLocation;

/** Server acknowledgement for an OP editor save request. */
public final class ServerConfigSaveResultPayload {
    public static final ResourceLocation ID = new ResourceLocation(CustomRecipeMod.MOD_ID, "server_config_save_result");

    private ServerConfigSaveResultPayload() {}
}
