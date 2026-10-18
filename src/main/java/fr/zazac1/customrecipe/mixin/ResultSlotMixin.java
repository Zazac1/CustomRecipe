package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes one craft consume each custom ingredient's configured stack amount. */
@Mixin(CraftingResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow @Final private RecipeInputInventory input;
    @Unique private int[] customrecipe$requiredCounts = new int[0];

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void customrecipe$captureRequiredCounts(PlayerEntity player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        Recipe<?> recipe = ((CraftingResultSlot) (Object) this).inventory instanceof CraftingResultInventory resultInventory
                ? resultInventory.getLastRecipe() : null;
        if (!(recipe instanceof CountedCraftingRecipe)) {
            recipe = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).orElse(null);
        }
        if (!(recipe instanceof CountedCraftingRecipe counted)) return;
        int[] required = counted.requiredCounts(input);
        if (required.length == input.size()) customrecipe$requiredCounts = required;
    }

    @Redirect(method = "onTakeItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/RecipeInputInventory;removeStack(II)Lnet/minecraft/item/ItemStack;"))
    private ItemStack customrecipe$consumeConfiguredAmount(RecipeInputInventory inventory, int slot, int vanillaAmount) {
        int required = slot >= 0 && slot < customrecipe$requiredCounts.length
                ? customrecipe$requiredCounts[slot] : vanillaAmount;
        return inventory.removeStack(slot, Math.max(vanillaAmount, required));
    }

    @Inject(method = "onTakeItem", at = @At("RETURN"))
    private void customrecipe$clearRequiredCounts(PlayerEntity player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
    }
}
