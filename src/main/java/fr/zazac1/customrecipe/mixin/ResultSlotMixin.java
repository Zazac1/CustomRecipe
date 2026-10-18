package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Consumes each configured ingredient amount once when a counted recipe is crafted. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow @Final private CraftingContainer craftSlots;

    @Unique private int[] customrecipe$requiredCounts = new int[0];

    @Inject(method = "onTake", at = @At("HEAD"))
    private void customrecipe$captureRequiredCounts(Player player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
        if (!(player.level() instanceof ServerLevel level)) return;
        Recipe<?> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftSlots, level).orElse(null);
        if (recipe instanceof CountedCraftingRecipe counted) {
            int[] required = counted.requiredCounts(craftSlots);
            if (required.length == craftSlots.getContainerSize()) customrecipe$requiredCounts = required;
        }
    }

    @Redirect(
            method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack customrecipe$consumeConfiguredAmount(CraftingContainer inventory, int slot, int vanillaAmount) {
        int amount = slot >= 0 && slot < customrecipe$requiredCounts.length
                ? customrecipe$requiredCounts[slot] : vanillaAmount;
        return inventory.removeItem(slot, Math.max(vanillaAmount, amount));
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void customrecipe$clearRequiredCounts(Player player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
    }
}
