package fr.isaac.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** One small server-filtered page of vanilla crafting recipes. */
public record VanillaRecipePagePayload(String json) implements CustomPayload {
    public static final Id<VanillaRecipePagePayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "vanilla_recipe_page"));
    public static final PacketCodec<RegistryByteBuf, VanillaRecipePagePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, VanillaRecipePagePayload::json, VanillaRecipePagePayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
