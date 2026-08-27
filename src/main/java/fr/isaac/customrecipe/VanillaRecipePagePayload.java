package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One small server-filtered page of vanilla crafting recipes. */
public record VanillaRecipePagePayload(String json) implements CustomPacketPayload {
    public static final Type<VanillaRecipePagePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "vanilla_recipe_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VanillaRecipePagePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, VanillaRecipePagePayload::json, VanillaRecipePagePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
