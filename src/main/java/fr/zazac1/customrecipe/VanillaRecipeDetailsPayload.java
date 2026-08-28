package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server response containing lightweight previews for each allowed material variant. */
public record VanillaRecipeDetailsPayload(String json) implements CustomPayload {
    public static final Id<VanillaRecipeDetailsPayload> ID = new Id<>(Identifier.of(CustomRecipeMod.MOD_ID, "vanilla_recipe_details"));
    public static final PacketCodec<RegistryByteBuf, VanillaRecipeDetailsPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, VanillaRecipeDetailsPayload::json, VanillaRecipeDetailsPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
