package fr.zazac1.customrecipe;

import net.minecraft.util.Identifier;

/** Server response containing a validated but not yet saved editor config. */
public final class ValidatedServerConfigPayload {
    public static final Identifier ID = new Identifier(CustomRecipeMod.MOD_ID, "validated_server_config");
    private ValidatedServerConfigPayload() {}
}
