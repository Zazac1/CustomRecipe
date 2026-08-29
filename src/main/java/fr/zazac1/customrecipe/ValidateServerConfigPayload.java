package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** An OP client's unsaved server configuration proposed for server-side validation. */
public record ValidateServerConfigPayload(String json) implements CustomPayload {
    public static final Id<ValidateServerConfigPayload> ID = new Id<>(
            Identifier.of(CustomRecipeMod.MOD_ID, "validate_server_config"));
    public static final PacketCodec<RegistryByteBuf, ValidateServerConfigPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ValidateServerConfigPayload::json, ValidateServerConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
