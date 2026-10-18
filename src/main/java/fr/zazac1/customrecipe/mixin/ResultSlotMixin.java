package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import fr.zazac1.customrecipe.CountedCraftingGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes one craft consume each custom ingredient's configured stack amount. */
@Mixin(CraftingResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow @Final private RecipeInputInventory input;
    @Shadow @Final private PlayerEntity player;

    @Unique private int[] customrecipe$requiredCounts = new int[0];
    @Unique private int customrecipe$inputLeft;
    @Unique private int customrecipe$inputTop;
    @Unique private int customrecipe$inputWidth;

    @Inject(method = "takeStack", at = @At("HEAD"), cancellable = true)
    private void customrecipe$rejectIncompleteCountedCraft(int amount,
                                                            CallbackInfoReturnable<ItemStack> cir) {
        if (!(player.getWorld() instanceof ServerWorld)) return;
        if (!(((CraftingResultSlot) (Object) this).inventory instanceof CraftingResultInventory resultInventory)) return;
        if (!CountedCraftingGuard.canTake(input, resultInventory)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void customrecipe$captureRequiredCounts(PlayerEntity player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
        if (!(player.getWorld() instanceof ServerWorld level)) return;

        CraftingRecipeInput.Positioned positioned = input.createPositionedRecipeInput();
        CraftingRecipeInput recipeInput = positioned.input();
        RecipeEntry<?> holder = ((CraftingResultSlot) (Object) this).inventory instanceof CraftingResultInventory resultInventory
                ? resultInventory.getLastRecipe() : null;
        if (holder == null || !(holder.value() instanceof CraftingRecipe)) {
            holder = level.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, recipeInput, level).orElse(null);
        }
        if (holder == null || !(holder.value() instanceof CountedCraftingRecipe counted)) return;

        int[] required = counted.requiredCounts(recipeInput);
        if (required.length != recipeInput.size()) return;
        customrecipe$requiredCounts = required;
        customrecipe$inputLeft = positioned.left();
        customrecipe$inputTop = positioned.top();
        customrecipe$inputWidth = recipeInput.getWidth();
    }

    @Redirect(
            method = "onTakeItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/RecipeInputInventory;removeStack(II)Lnet/minecraft/item/ItemStack;")
    )
    private ItemStack customrecipe$consumeConfiguredAmount(RecipeInputInventory inventory, int slot, int vanillaAmount) {
        int gridWidth = inventory.getWidth();
        int x = slot % gridWidth - customrecipe$inputLeft;
        int y = slot / gridWidth - customrecipe$inputTop;
        int inputIndex = x >= 0 && y >= 0 && x < customrecipe$inputWidth
                ? y * customrecipe$inputWidth + x : -1;
        int required = inputIndex >= 0 && inputIndex < customrecipe$requiredCounts.length
                ? customrecipe$requiredCounts[inputIndex] : vanillaAmount;
        return inventory.removeStack(slot, Math.max(vanillaAmount, required));
    }

    @Inject(method = "onTakeItem", at = @At("RETURN"))
    private void customrecipe$clearRequiredCounts(PlayerEntity player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
    }
}
