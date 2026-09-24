package fr.zazac1.customrecipe;

import net.minecraft.util.Identifier;

/** Server acknowledgement for an OP editor save request. */
public final class ServerConfigSaveResultPayload {
    public static final Identifier ID = new Identifier(CustomRecipeMod.MOD_ID, "server_config_save_result");

    private ServerConfigSaveResultPayload() {}
}
