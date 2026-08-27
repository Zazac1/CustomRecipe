package fr.isaac.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server response containing lightweight previews for each allowed material variant. */
public record VanillaRecipeDetailsPayload(String json) implements CustomPacketPayload {
    public static final Type<VanillaRecipeDetailsPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "vanilla_recipe_details"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VanillaRecipeDetailsPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, VanillaRecipeDetailsPayload::json, VanillaRecipeDetailsPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
