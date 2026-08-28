package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** OP request for the selectable ingredient variants of a recipe. */
public record VanillaRecipeDetailsQueryPayload(String recipeId) implements CustomPayload {
    public static final Id<VanillaRecipeDetailsQueryPayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "vanilla_recipe_details_query"));
    public static final PacketCodec<RegistryByteBuf, VanillaRecipeDetailsQueryPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, VanillaRecipeDetailsQueryPayload::recipeId, VanillaRecipeDetailsQueryPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
