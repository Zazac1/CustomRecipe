package fr.zazac1.customrecipe;

import fr.zazac1.customrecipe.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** The nine Fabric payload flows, kept as individually directed Forge packets. */
public final class ModNetworking {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CustomRecipeMod.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    public static void initialize() {
        CHANNEL.registerMessage(nextId++, SaveConfigPacket.class, StringPacket::encode, SaveConfigPacket::decode,
                SaveConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, ValidateConfigPacket.class, StringPacket::encode, ValidateConfigPacket::decode,
                ValidateConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, VanillaQueryPacket.class, StringPacket::encode, VanillaQueryPacket::decode,
                VanillaQueryPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, VanillaDetailsQueryPacket.class, StringPacket::encode, VanillaDetailsQueryPacket::decode,
                VanillaDetailsQueryPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, ServerConfigPacket.class, StringPacket::encode, ServerConfigPacket::decode,
                ServerConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, ValidatedConfigPacket.class, StringPacket::encode, ValidatedConfigPacket::decode,
                ValidatedConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, VanillaPagePacket.class, StringPacket::encode, VanillaPagePacket::decode,
                VanillaPagePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, VanillaDetailsPacket.class, StringPacket::encode, VanillaDetailsPacket::decode,
                VanillaDetailsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, SaveResultPacket.class, SaveResultPacket::encode, SaveResultPacket::decode,
                SaveResultPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendSave(String json) { CHANNEL.sendToServer(new SaveConfigPacket(json)); }
    public static void sendValidate(String json) { CHANNEL.sendToServer(new ValidateConfigPacket(json)); }
    public static void sendVanillaQuery(String json) { CHANNEL.sendToServer(new VanillaQueryPacket(json)); }
    public static void sendVanillaDetailsQuery(String id) { CHANNEL.sendToServer(new VanillaDetailsQueryPacket(id)); }
    /** Mirrors Fabric's canSend guard before any server-to-client editor packet. */
    public static boolean canSend(ServerPlayer player) {
        return player != null && CHANNEL.isRemotePresent(player.connection.connection);
    }
    public static void sendServerConfig(ServerPlayer player, String json) { send(player, new ServerConfigPacket(json)); }
    public static void sendValidatedConfig(ServerPlayer player, String json) { send(player, new ValidatedConfigPacket(json)); }
    public static void sendVanillaPage(ServerPlayer player, String json) { send(player, new VanillaPagePacket(json)); }
    public static void sendVanillaDetails(ServerPlayer player, String json) { send(player, new VanillaDetailsPacket(json)); }
    public static void sendSaveResult(ServerPlayer player, boolean saved, String reason) { send(player, new SaveResultPacket(saved, reason)); }
    private static void send(ServerPlayer player, Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }

    private interface StringPacket {
        String value();
        static void encode(StringPacket packet, FriendlyByteBuf buf) { buf.writeUtf(packet.value(), 500_000); }
    }
    private static void handled(NetworkEvent.Context context) { context.setPacketHandled(true); }
    private record SaveConfigPacket(String value) implements StringPacket {
        static SaveConfigPacket decode(FriendlyByteBuf b) { return new SaveConfigPacket(b.readUtf(500_000)); }
        static void handle(SaveConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleSave(c.getSender(), p.value)); handled(c); }
    }
    private record ValidateConfigPacket(String value) implements StringPacket {
        static ValidateConfigPacket decode(FriendlyByteBuf b) { return new ValidateConfigPacket(b.readUtf(500_000)); }
        static void handle(ValidateConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleValidate(c.getSender(), p.value)); handled(c); }
    }
    private record VanillaQueryPacket(String value) implements StringPacket {
        static VanillaQueryPacket decode(FriendlyByteBuf b) { return new VanillaQueryPacket(b.readUtf(500_000)); }
        static void handle(VanillaQueryPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleVanillaQuery(c.getSender(), p.value)); handled(c); }
    }
    private record VanillaDetailsQueryPacket(String value) implements StringPacket {
        static VanillaDetailsQueryPacket decode(FriendlyByteBuf b) { return new VanillaDetailsQueryPacket(b.readUtf(500_000)); }
        static void handle(VanillaDetailsQueryPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleVanillaDetailsQuery(c.getSender(), p.value)); handled(c); }
    }
    private record ServerConfigPacket(String value) implements StringPacket {
        static ServerConfigPacket decode(FriendlyByteBuf b) { return new ServerConfigPacket(b.readUtf(500_000)); }
        static void handle(ServerConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.serverConfig(p.value))); handled(c); }
    }
    private record ValidatedConfigPacket(String value) implements StringPacket {
        static ValidatedConfigPacket decode(FriendlyByteBuf b) { return new ValidatedConfigPacket(b.readUtf(500_000)); }
        static void handle(ValidatedConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.validatedConfig(p.value))); handled(c); }
    }
    private record VanillaPagePacket(String value) implements StringPacket {
        static VanillaPagePacket decode(FriendlyByteBuf b) { return new VanillaPagePacket(b.readUtf(500_000)); }
        static void handle(VanillaPagePacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.vanillaPage(p.value))); handled(c); }
    }
    private record VanillaDetailsPacket(String value) implements StringPacket {
        static VanillaDetailsPacket decode(FriendlyByteBuf b) { return new VanillaDetailsPacket(b.readUtf(500_000)); }
        static void handle(VanillaDetailsPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.vanillaDetails(p.value))); handled(c); }
    }
    private record SaveResultPacket(boolean saved, String reason) {
        static void encode(SaveResultPacket p, FriendlyByteBuf b) { b.writeBoolean(p.saved); b.writeUtf(p.reason == null ? "" : p.reason, 512); }
        static SaveResultPacket decode(FriendlyByteBuf b) { return new SaveResultPacket(b.readBoolean(), b.readUtf(512)); }
        static void handle(SaveResultPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.saveResult(p.saved, p.reason))); handled(c); }
    }
    private ModNetworking() {}
}
