package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** An OP client's proposed replacement for the authoritative server config. */
public record SaveServerConfigPayload(String json) implements CustomPayload {
    public static final Id<SaveServerConfigPayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "save_server_config"));
    public static final PacketCodec<RegistryByteBuf, SaveServerConfigPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, SaveServerConfigPayload::json, SaveServerConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
