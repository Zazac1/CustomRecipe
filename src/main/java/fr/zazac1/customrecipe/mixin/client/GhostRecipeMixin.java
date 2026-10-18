package fr.zazac1.customrecipe.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws stack counts for counted ingredients in recipe-book ghost slots. */
@Mixin(GhostRecipe.class)
public abstract class GhostRecipeMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V")
    )
    private void customrecipe$drawCountedGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderFakeItem(stack, x, y);
        if (stack.getCount() > 1) {
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
    }
}
