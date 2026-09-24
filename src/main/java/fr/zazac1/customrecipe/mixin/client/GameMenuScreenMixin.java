package fr.zazac1.customrecipe.mixin.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.client.ConfigScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the local-world entry point without altering vanilla pause-menu layout. */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final Identifier RECIPE_CREATOR_LOGO = new Identifier(CustomRecipeMod.MOD_ID,
            "textures/gui/pause_button.png");

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void customrecipe$addRecipeCreator(CallbackInfo ci) {
        // Remote servers remain server-managed through /customrecipe.
        if (client == null || client.getServer() == null) return;
        int buttonX = width - 28;
        int buttonY = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.empty(),
                button -> client.setScreen(ConfigScreen.fromPauseMenu((Screen) (Object) this)))
                .tooltip(Tooltip.of(Text.translatable("customrecipe.tooltip.open")))
                .dimensions(buttonX, buttonY, 20, 20).build());
        addDrawable((context, mouseX, mouseY, delta) -> context.drawTexture(
                RECIPE_CREATOR_LOGO,
                buttonX + 2, buttonY + 2, 0, 0, 16, 16, 16, 16));
    }
}
