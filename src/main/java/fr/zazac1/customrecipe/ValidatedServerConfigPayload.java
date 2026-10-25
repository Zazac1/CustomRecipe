package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-validated, still-unsaved configuration returned to the OP editor. */
public record ValidatedServerConfigPayload(String json) implements CustomPacketPayload {
    public static final Type<ValidatedServerConfigPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "validated_server_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ValidatedServerConfigPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(1_000_000), ValidatedServerConfigPayload::json, ValidatedServerConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
