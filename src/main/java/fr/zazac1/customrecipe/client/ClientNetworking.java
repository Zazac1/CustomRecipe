package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ModNetworking;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-only endpoints registered as NeoForge S2C play payload handlers. */
public final class ClientNetworking {
    public static void handle(ModNetworking.S2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.action()) {
                case SERVER_CONFIG -> ClientInit.receiveServerConfig(payload.data());
                case VALIDATED_CONFIG -> ClientInit.receiveValidatedServerConfig(payload.data());
                case VANILLA_PAGE -> ClientInit.receiveVanillaRecipePage(payload.data());
                case VANILLA_DETAILS_PAGE -> ClientInit.receiveVanillaRecipeDetails(payload.data());
                case SAVE_RESULT -> ClientInit.receiveSaveResult(payload.data());
                case REFRESH_REI -> ReiCompat.refreshAfterRecipeCatalogueSync();
                default -> { }
            }
        });
    }
    private ClientNetworking() { }
}
