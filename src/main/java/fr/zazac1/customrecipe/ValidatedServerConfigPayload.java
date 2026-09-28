package fr.zazac1.customrecipe;

import net.minecraft.resources.ResourceLocation;

/** Server response containing a validated but not yet saved editor config. */
public final class ValidatedServerConfigPayload {
    public static final ResourceLocation ID = new ResourceLocation(CustomRecipeMod.MOD_ID, "validated_server_config");
    private ValidatedServerConfigPayload() {}
}
