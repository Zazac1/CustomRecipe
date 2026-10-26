package fr.zazac1.customrecipe.development;

import com.mojang.authlib.minecraft.OfflineSocialInteractions;
import com.mojang.authlib.minecraft.SocialInteractionsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.GameConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses vanilla's offline service only for ForgeGradle's synthetic session. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class DevelopmentOfflineSessionMixin {
    @Inject(
            method = "createSocialInteractions(Lcom/mojang/authlib/yggdrasil/YggdrasilAuthenticationService;Lnet/minecraft/client/GameConfiguration;)Lcom/mojang/authlib/minecraft/SocialInteractionsService;",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void customrecipe$offlineDevelopmentSession(
            YggdrasilAuthenticationService authentication, GameConfiguration configuration,
            CallbackInfoReturnable<SocialInteractionsService> cir) {
        if (!FMLEnvironment.production
                && "DONT_CRASH".equals(configuration.user.user.getAccessToken())) {
            cir.setReturnValue(new OfflineSocialInteractions());
        }
    }
}
