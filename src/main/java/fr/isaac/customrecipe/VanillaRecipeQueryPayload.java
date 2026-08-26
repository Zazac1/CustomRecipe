package fr.isaac.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** OP request for one page of vanilla crafting recipes. */
public record VanillaRecipeQueryPayload(String json) implements CustomPayload {
    public static final Id<VanillaRecipeQueryPayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "vanilla_recipe_query"));
    public static final PacketCodec<RegistryByteBuf, VanillaRecipeQueryPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, VanillaRecipeQueryPayload::json, VanillaRecipeQueryPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
