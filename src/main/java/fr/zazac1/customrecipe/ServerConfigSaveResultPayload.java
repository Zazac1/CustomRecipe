package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Structured acknowledgement for an OP editor save request. */
public record ServerConfigSaveResultPayload(String json) implements CustomPacketPayload {
    public static final Type<ServerConfigSaveResultPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "server_config_save_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigSaveResultPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(1_024), ServerConfigSaveResultPayload::json,
                    ServerConfigSaveResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

