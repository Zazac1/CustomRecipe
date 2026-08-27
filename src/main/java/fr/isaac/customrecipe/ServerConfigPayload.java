package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The authoritative server config sent to an OP client. */
public record ServerConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<ServerConfigPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "server_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ServerConfigPayload::json, ServerConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
