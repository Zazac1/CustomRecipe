package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.CraftingResultSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Consumes each configured ingredient amount once when a counted recipe is crafted. */
@Mixin(CraftingResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow @Final private CraftingInventory craftSlots;

    @Unique private int[] customrecipe$requiredCounts = new int[0];

    @Inject(method = "onTake", at = @At("HEAD"))
    private void customrecipe$captureRequiredCounts(PlayerEntity player, ItemStack result, CallbackInfoReturnable<ItemStack> cir) {
        customrecipe$requiredCounts = new int[0];
        if (!(player.level instanceof ServerWorld level)) return;
        IRecipe<?> recipe = level.getRecipeManager()
                .getRecipeFor(IRecipeType.CRAFTING, craftSlots, level).orElse(null);
        if (recipe instanceof CountedCraftingRecipe counted) {
            int[] required = counted.requiredCounts(craftSlots);
            if (required.length == craftSlots.getContainerSize()) customrecipe$requiredCounts = required;
        }
    }

    @Redirect(
            method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/CraftingInventory;removeItem(II)Lnet/minecraft/item/ItemStack;")
    )
    private ItemStack customrecipe$consumeConfiguredAmount(CraftingInventory inventory, int slot, int vanillaAmount) {
        int amount = slot >= 0 && slot < customrecipe$requiredCounts.length
                ? customrecipe$requiredCounts[slot] : vanillaAmount;
        return inventory.removeItem(slot, Math.max(vanillaAmount, amount));
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void customrecipe$clearRequiredCounts(PlayerEntity player, ItemStack result, CallbackInfoReturnable<ItemStack> cir) {
        customrecipe$requiredCounts = new int[0];
    }
}
