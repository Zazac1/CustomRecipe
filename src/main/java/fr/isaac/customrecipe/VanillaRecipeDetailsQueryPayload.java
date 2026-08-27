package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** OP request for the selectable ingredient variants of a recipe. */
public record VanillaRecipeDetailsQueryPayload(String recipeId) implements CustomPacketPayload {
    public static final Type<VanillaRecipeDetailsQueryPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "vanilla_recipe_details_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VanillaRecipeDetailsQueryPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, VanillaRecipeDetailsQueryPayload::recipeId, VanillaRecipeDetailsQueryPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
