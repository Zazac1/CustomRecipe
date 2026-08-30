package fr.zazac1.customrecipe;

import net.minecraft.util.Identifier;

/** Client-to-server request to validate staged local recipes against server resources. */
public final class ValidateServerConfigPayload {
    public static final Identifier ID = new Identifier(CustomRecipeMod.MOD_ID, "validate_server_config");
    private ValidateServerConfigPayload() {}
}
