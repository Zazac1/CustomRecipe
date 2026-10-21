package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Structured acknowledgement for an OP editor save request. */
public record ServerConfigSaveResultPayload(String json) implements CustomPayload {
    public static final Id<ServerConfigSaveResultPayload> ID =
            new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "server_config_save_result"));
    public static final PacketCodec<RegistryByteBuf, ServerConfigSaveResultPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.string(1_000_000), ServerConfigSaveResultPayload::json,
                    ServerConfigSaveResultPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

