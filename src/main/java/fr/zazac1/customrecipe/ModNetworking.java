package fr.zazac1.customrecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Directed NeoForge play payloads for every server-editor exchange. */
public final class ModNetworking {
    public static final int MAX_JSON_CHARS = 500_000;

    public static void initialize(IEventBus modBus) { modBus.addListener(ModNetworking::register); }
    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(C2SPayload.TYPE, C2SPayload.STREAM_CODEC, ModNetworking::handleServer);
        registrar.playToClient(S2CPayload.TYPE, S2CPayload.STREAM_CODEC, fr.zazac1.customrecipe.client.ClientNetworking::handle);
    }

    private static void handleServer(C2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            switch (payload.action()) {
                case SAVE -> ServerConfigNetworking.handleSave(player, payload.data());
                case VALIDATE -> ServerConfigNetworking.handleValidate(player, payload.data());
                case VANILLA_QUERY -> ServerConfigNetworking.handleVanillaQuery(player, payload.data());
                case VANILLA_DETAILS -> ServerConfigNetworking.handleVanillaDetailsQuery(player, payload.data());
                default -> { }
            }
        });
    }

    public static void save(String json) { PacketDistributor.sendToServer(new C2SPayload(Action.SAVE, json)); }
    public static void validate(String json) { PacketDistributor.sendToServer(new C2SPayload(Action.VALIDATE, json)); }
    public static void queryVanilla(String json) { PacketDistributor.sendToServer(new C2SPayload(Action.VANILLA_QUERY, json)); }
    public static void queryVanillaDetails(String id) { PacketDistributor.sendToServer(new C2SPayload(Action.VANILLA_DETAILS, id)); }
    public static boolean canSend(ServerPlayer player) { return player != null && player.connection.hasChannel(S2CPayload.TYPE); }
    public static void send(ServerPlayer player, Action action, String data) { if (canSend(player)) PacketDistributor.sendToPlayer(player, new S2CPayload(action, data)); }

    public enum Action { SAVE, VALIDATE, VANILLA_QUERY, VANILLA_DETAILS, SERVER_CONFIG, VALIDATED_CONFIG, VANILLA_PAGE, VANILLA_DETAILS_PAGE, SAVE_RESULT, REFRESH_REI }
    private static <T extends Packet> T read(RegistryFriendlyByteBuf b, java.util.function.BiFunction<Action, String, T> factory) { return factory.apply(Action.values()[b.readVarInt()], b.readUtf(MAX_JSON_CHARS)); }
    private static void write(RegistryFriendlyByteBuf b, Packet payload) { b.writeVarInt(payload.action().ordinal()); b.writeUtf(payload.data() == null ? "" : payload.data(), MAX_JSON_CHARS); }
    public interface Packet extends CustomPacketPayload { Action action(); String data(); }
    public record C2SPayload(Action action, String data) implements Packet {
        public static final Type<C2SPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "editor_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SPayload> STREAM_CODEC = StreamCodec.of((b,p)->write(b,p), b->read(b,C2SPayload::new));
        @Override public Type<C2SPayload> type() { return TYPE; }
    }
    public record S2CPayload(Action action, String data) implements Packet {
        public static final Type<S2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "editor_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CPayload> STREAM_CODEC = StreamCodec.of((b,p)->write(b,p), b->read(b,S2CPayload::new));
        @Override public Type<S2CPayload> type() { return TYPE; }
    }
    private ModNetworking() { }
}
