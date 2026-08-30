package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-validated, still-unsaved configuration returned to the OP editor. */
public record ValidatedServerConfigPayload(String json) implements CustomPayload {
    public static final Id<ValidatedServerConfigPayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "validated_server_config"));
    public static final PacketCodec<RegistryByteBuf, ValidatedServerConfigPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ValidatedServerConfigPayload::json, ValidatedServerConfigPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
