package fr.zazac1.customrecipe.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.recipebook.RecipeBookGhostSlots;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws stack counts for counted ingredients in recipe-book ghost slots. */
@Mixin(RecipeBookGhostSlots.class)
public abstract class RecipeBookGhostSlotsMixin {
    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItemWithoutEntity(Lnet/minecraft/item/ItemStack;II)V")
    )
    private void customrecipe$drawCountedGhostItem(DrawContext context, ItemStack stack, int x, int y) {
        context.drawItemWithoutEntity(stack, x, y);
        if (stack.getCount() > 1) {
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x, y);
        }
    }
}
