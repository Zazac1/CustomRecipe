package fr.zazac1.customrecipe.mixin.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.client.ConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the local-world entry point without altering vanilla pause-menu layout. */
@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final Identifier RECIPE_CREATOR_LOGO = Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID,
            "textures/gui/pause_button.png");

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void customrecipe$addRecipeCreator(CallbackInfo ci) {
        // Remote servers remain server-managed through /customrecipe.
        if (minecraft == null || minecraft.getSingleplayerServer() == null) return;
        int buttonX = width - 28;
        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.empty(),
                button -> minecraft.gui.setScreen(ConfigScreen.fromPauseMenu((Screen) (Object) this)))
                .tooltip(Tooltip.create(Component.translatable("customrecipe.tooltip.open")))
                .bounds(buttonX, buttonY, 20, 20).build());
        addRenderableOnly((context, mouseX, mouseY, delta) -> context.blit(
                RenderPipelines.GUI_TEXTURED, RECIPE_CREATOR_LOGO,
                buttonX + 2, buttonY + 2, 0, 0, 16, 16, 16, 16));
    }
}
