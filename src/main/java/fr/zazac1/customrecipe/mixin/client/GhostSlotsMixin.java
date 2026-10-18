package fr.zazac1.customrecipe.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws stack counts for counted ingredients in recipe-book ghost slots. */
@Mixin(GhostSlots.class)
public abstract class GhostSlotsMixin {
    @Redirect(
            method = "lambda$extractRenderState$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;II)V")
    )
    private void customrecipe$drawCountedGhostItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.fakeItem(stack, x, y);
        if (stack.getCount() > 1) {
            graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
    }
}
