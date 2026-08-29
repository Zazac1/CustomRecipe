package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An OP client's unsaved server configuration proposed for server-side validation. */
public record ValidateServerConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<ValidateServerConfigPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "validate_server_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ValidateServerConfigPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ValidateServerConfigPayload::json, ValidateServerConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
