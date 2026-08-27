package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An OP client's proposed replacement for the authoritative server config. */
public record SaveServerConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<SaveServerConfigPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "save_server_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveServerConfigPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SaveServerConfigPayload::json, SaveServerConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
