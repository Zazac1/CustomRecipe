package fr.zazac1.customrecipe;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Directed play channels, handled on the game thread by NeoForge. */
public final class ModNetworking {
    public static void initialize(IEventBus modBus) { modBus.addListener(ModNetworking::register); }
    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SaveServerConfigPayload.ID, SaveServerConfigPayload.CODEC, ServerConfigNetworking::handleSave);
        registrar.playToServer(ValidateServerConfigPayload.ID, ValidateServerConfigPayload.CODEC, ServerConfigNetworking::handleValidate);
        registrar.playToServer(VanillaRecipeQueryPayload.ID, VanillaRecipeQueryPayload.CODEC, ServerConfigNetworking::handleVanillaQuery);
        registrar.playToServer(VanillaRecipeDetailsQueryPayload.ID, VanillaRecipeDetailsQueryPayload.CODEC, ServerConfigNetworking::handleVanillaDetailsQuery);
        registrar.playToClient(ServerConfigPayload.ID, ServerConfigPayload.CODEC, (payload, context) -> fr.zazac1.customrecipe.client.ClientInit.receiveServerConfig(payload));
        registrar.playToClient(ValidatedServerConfigPayload.ID, ValidatedServerConfigPayload.CODEC, (payload, context) -> fr.zazac1.customrecipe.client.ClientInit.receiveValidatedServerConfig(payload));
        registrar.playToClient(ServerConfigSaveResultPayload.ID, ServerConfigSaveResultPayload.CODEC, (payload, context) -> fr.zazac1.customrecipe.client.ClientInit.receiveSaveResult(payload));
        registrar.playToClient(VanillaRecipePagePayload.ID, VanillaRecipePagePayload.CODEC, (payload, context) -> fr.zazac1.customrecipe.client.ClientInit.receiveVanillaRecipePage(payload));
        registrar.playToClient(VanillaRecipeDetailsPayload.ID, VanillaRecipeDetailsPayload.CODEC, (payload, context) -> fr.zazac1.customrecipe.client.ClientInit.receiveVanillaRecipeDetails(payload));
    }
    private ModNetworking() {}
}
