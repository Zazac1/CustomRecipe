package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.access.DefaultMaxStackSizeAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers MAX_STACK_SIZE while vanilla and modded items are being registered. */
@Mixin(Item.Properties.class)
public abstract class ItemPropertiesMixin implements DefaultMaxStackSizeAccess {
    @Unique
    private int customrecipe$defaultMaxStackSize = Item.DEFAULT_MAX_STACK_SIZE;

    @Inject(method = "component", at = @At("HEAD"))
    private <T> void customrecipe$captureMaxStackSize(
            DataComponentType<T> type, T value, CallbackInfoReturnable<Item.Properties> cir) {
        if (type == DataComponents.MAX_STACK_SIZE && value instanceof Integer maxStackSize) {
            customrecipe$defaultMaxStackSize = maxStackSize;
        }
    }

    @Override
    public int customrecipe$defaultMaxStackSize() {
        return customrecipe$defaultMaxStackSize;
    }
}
