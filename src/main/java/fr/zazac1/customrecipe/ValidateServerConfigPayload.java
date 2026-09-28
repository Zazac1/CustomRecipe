package fr.zazac1.customrecipe;

import net.minecraft.resources.ResourceLocation;

/** Client-to-server request to validate staged local recipes against server resources. */
public final class ValidateServerConfigPayload {
    public static final ResourceLocation ID = new ResourceLocation(CustomRecipeMod.MOD_ID, "validate_server_config");
    private ValidateServerConfigPayload() {}
}
