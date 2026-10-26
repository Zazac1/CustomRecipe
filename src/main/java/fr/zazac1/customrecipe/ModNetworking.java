package fr.zazac1.customrecipe;

import fr.zazac1.customrecipe.client.ClientPacketHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

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
    public static boolean canSend(ServerPlayerEntity player) {
        return player != null && CHANNEL.isRemotePresent(player.connection.connection);
    }
    public static void sendServerConfig(ServerPlayerEntity player, String json) { send(player, new ServerConfigPacket(json)); }
    public static void sendValidatedConfig(ServerPlayerEntity player, String json) { send(player, new ValidatedConfigPacket(json)); }
    public static void sendVanillaPage(ServerPlayerEntity player, String json) { send(player, new VanillaPagePacket(json)); }
    public static void sendVanillaDetails(ServerPlayerEntity player, String json) { send(player, new VanillaDetailsPacket(json)); }
    public static void sendSaveResult(ServerPlayerEntity player, boolean saved, String reason) { send(player, new SaveResultPacket(saved, reason)); }
    private static void send(ServerPlayerEntity player, Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }

    private interface StringPacket {
        String value();
        static void encode(StringPacket packet, PacketBuffer buf) { buf.writeUtf(packet.value(), 1_000_000); }
    }
    private static void handled(NetworkEvent.Context context) { context.setPacketHandled(true); }
    private static final class SaveConfigPacket implements StringPacket {
        private final String value;
        public SaveConfigPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; SaveConfigPacket other=(SaveConfigPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static SaveConfigPacket decode(PacketBuffer b) { return new SaveConfigPacket(b.readUtf(1_000_000)); }
        static void handle(SaveConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleSave(c.getSender(), p.value)); handled(c); }
    }
    private static final class ValidateConfigPacket implements StringPacket {
        private final String value;
        public ValidateConfigPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; ValidateConfigPacket other=(ValidateConfigPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static ValidateConfigPacket decode(PacketBuffer b) { return new ValidateConfigPacket(b.readUtf(1_000_000)); }
        static void handle(ValidateConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleValidate(c.getSender(), p.value)); handled(c); }
    }
    private static final class VanillaQueryPacket implements StringPacket {
        private final String value;
        public VanillaQueryPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaQueryPacket other=(VanillaQueryPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static VanillaQueryPacket decode(PacketBuffer b) { return new VanillaQueryPacket(b.readUtf(1_000_000)); }
        static void handle(VanillaQueryPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleVanillaQuery(c.getSender(), p.value)); handled(c); }
    }
    private static final class VanillaDetailsQueryPacket implements StringPacket {
        private final String value;
        public VanillaDetailsQueryPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaDetailsQueryPacket other=(VanillaDetailsQueryPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static VanillaDetailsQueryPacket decode(PacketBuffer b) { return new VanillaDetailsQueryPacket(b.readUtf(1_000_000)); }
        static void handle(VanillaDetailsQueryPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> ServerConfigNetworking.handleVanillaDetailsQuery(c.getSender(), p.value)); handled(c); }
    }
    private static final class ServerConfigPacket implements StringPacket {
        private final String value;
        public ServerConfigPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; ServerConfigPacket other=(ServerConfigPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static ServerConfigPacket decode(PacketBuffer b) { return new ServerConfigPacket(b.readUtf(1_000_000)); }
        static void handle(ServerConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.serverConfig(p.value))); handled(c); }
    }
    private static final class ValidatedConfigPacket implements StringPacket {
        private final String value;
        public ValidatedConfigPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; ValidatedConfigPacket other=(ValidatedConfigPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static ValidatedConfigPacket decode(PacketBuffer b) { return new ValidatedConfigPacket(b.readUtf(1_000_000)); }
        static void handle(ValidatedConfigPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.validatedConfig(p.value))); handled(c); }
    }
    private static final class VanillaPagePacket implements StringPacket {
        private final String value;
        public VanillaPagePacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaPagePacket other=(VanillaPagePacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static VanillaPagePacket decode(PacketBuffer b) { return new VanillaPagePacket(b.readUtf(1_000_000)); }
        static void handle(VanillaPagePacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.vanillaPage(p.value))); handled(c); }
    }
    private static final class VanillaDetailsPacket implements StringPacket {
        private final String value;
        public VanillaDetailsPacket(String value) { this.value=value; }
        public String value() { return value; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; VanillaDetailsPacket other=(VanillaDetailsPacket)obj; return java.util.Objects.equals(value, other.value); }
        @Override public int hashCode() { return java.util.Objects.hash(value); }

        static VanillaDetailsPacket decode(PacketBuffer b) { return new VanillaDetailsPacket(b.readUtf(1_000_000)); }
        static void handle(VanillaDetailsPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.vanillaDetails(p.value))); handled(c); }
    }
    private static final class SaveResultPacket {
        private final boolean saved;
        private final String reason;
        public SaveResultPacket(boolean saved, String reason) { this.saved=saved; this.reason=reason; }
        public boolean saved() { return saved; }
        public String reason() { return reason; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; SaveResultPacket other=(SaveResultPacket)obj; return java.util.Objects.equals(saved, other.saved) && java.util.Objects.equals(reason, other.reason); }
        @Override public int hashCode() { return java.util.Objects.hash(saved, reason); }

        static void encode(SaveResultPacket p, PacketBuffer b) { b.writeBoolean(p.saved); b.writeUtf(p.reason == null ? "" : p.reason, 512); }
        static SaveResultPacket decode(PacketBuffer b) { return new SaveResultPacket(b.readBoolean(), b.readUtf(512)); }
        static void handle(SaveResultPacket p, Supplier<NetworkEvent.Context> s) { NetworkEvent.Context c=s.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.saveResult(p.saved, p.reason))); handled(c); }
    }
    private ModNetworking() {}
}
