package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.access.DefaultMaxStackSizeAccess;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the registration-time stack limit available before components are bound. */
@Mixin(Item.class)
public abstract class ItemMixin implements DefaultMaxStackSizeAccess {
    @Unique
    private int customrecipe$defaultMaxStackSize = Item.DEFAULT_MAX_STACK_SIZE;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void customrecipe$captureDefaultMaxStackSize(Item.Properties properties, CallbackInfo ci) {
        customrecipe$defaultMaxStackSize =
                ((DefaultMaxStackSizeAccess) (Object) properties).customrecipe$defaultMaxStackSize();
    }

    @Override
    public int customrecipe$defaultMaxStackSize() {
        return customrecipe$defaultMaxStackSize;
    }
}
