package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** OP request for one page of vanilla crafting recipes. */
public record VanillaRecipeQueryPayload(String json) implements CustomPacketPayload {
    public static final Type<VanillaRecipeQueryPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "vanilla_recipe_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VanillaRecipeQueryPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, VanillaRecipeQueryPayload::json, VanillaRecipeQueryPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
