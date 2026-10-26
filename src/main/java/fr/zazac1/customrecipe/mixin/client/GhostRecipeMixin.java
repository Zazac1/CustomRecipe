package fr.zazac1.customrecipe.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.gui.recipebook.GhostRecipe;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws stack counts for counted ingredients in recipe-book ghost slots. */
@Mixin(GhostRecipe.class)
public abstract class GhostRecipeMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderAndDecorateFakeItem(Lnet/minecraft/item/ItemStack;II)V")
    )
    private void customrecipe$drawCountedGhostItem(ItemRenderer renderer, ItemStack stack, int x, int y) {
        renderer.renderAndDecorateFakeItem(stack, x, y);
        if (stack.getCount() > 1) {
            renderer.renderGuiItemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
    }
}
