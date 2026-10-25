package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.access.ExactStackedItemContentsAccess;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Keeps component-rich stacks that vanilla excludes from recipe-book availability checks. */
@Mixin(StackedItemContents.class)
public abstract class StackedItemContentsMixin implements ExactStackedItemContentsAccess {
    @Unique private final List<ItemStack> customrecipe$exactStacks = new ArrayList<>();

    @Inject(method = "accountSimpleStack", at = @At("HEAD"))
    private void customrecipe$captureExactStack(ItemStack stack, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) customrecipe$exactStacks.add(stack.copy());
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void customrecipe$clearExactStacks(CallbackInfo ci) {
        customrecipe$exactStacks.clear();
    }

    @Override
    public List<ItemStack> customrecipe$exactStacks() {
        return customrecipe$exactStacks;
    }
}
