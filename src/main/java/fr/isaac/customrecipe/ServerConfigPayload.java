package fr.isaac.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** The authoritative server config sent to an OP client. */
public record ServerConfigPayload(String json) implements CustomPayload {
    public static final Id<ServerConfigPayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "server_config"));
    public static final PacketCodec<RegistryByteBuf, ServerConfigPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ServerConfigPayload::json, ServerConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
