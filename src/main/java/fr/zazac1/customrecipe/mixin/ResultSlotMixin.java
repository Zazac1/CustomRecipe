package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import fr.zazac1.customrecipe.CountedCraftingGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
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
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow @Final private CraftingContainer craftSlots;
    @Shadow @Final private Player player;

    @Unique private int[] customrecipe$requiredCounts = new int[0];
    @Unique private int customrecipe$inputLeft;
    @Unique private int customrecipe$inputTop;
    @Unique private int customrecipe$inputWidth;

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void customrecipe$rejectIncompleteCountedCraft(int amount,
                                                            CallbackInfoReturnable<ItemStack> cir) {
        if (!(player.level() instanceof ServerLevel)) return;
        Object resultContainer = ((ResultSlot) (Object) this).container;
        if (!(resultContainer instanceof RecipeCraftingHolder recipeHolder)
                || CountedCraftingGuard.canTake(craftSlots, recipeHolder)) return;

        if (resultContainer instanceof net.minecraft.world.Container container) {
            container.setItem(0, ItemStack.EMPTY);
        }
        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void customrecipe$captureRequiredCounts(Player player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
        if (!(player.level() instanceof ServerLevel level)) return;

        CraftingInput.Positioned positioned = craftSlots.asPositionedCraftInput();
        CraftingInput input = positioned.input();
        RecipeHolder<?> holder = ((ResultSlot) (Object) this).container instanceof RecipeCraftingHolder recipeHolder
                ? recipeHolder.getRecipeUsed() : null;
        if (holder == null || !(holder.value() instanceof CraftingRecipe)) {
            holder = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        }
        if (holder == null || !(holder.value() instanceof CountedCraftingRecipe counted)) return;

        int[] required = counted.requiredCounts(input);
        if (required.length != input.size()) return;
        customrecipe$requiredCounts = required;
        customrecipe$inputLeft = positioned.left();
        customrecipe$inputTop = positioned.top();
        customrecipe$inputWidth = input.width();
    }

    @Redirect(
            method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack customrecipe$consumeConfiguredAmount(CraftingContainer inventory, int slot, int vanillaAmount) {
        int gridWidth = inventory.getWidth();
        int x = slot % gridWidth - customrecipe$inputLeft;
        int y = slot / gridWidth - customrecipe$inputTop;
        int inputIndex = x >= 0 && y >= 0 && x < customrecipe$inputWidth
                ? y * customrecipe$inputWidth + x : -1;
        int required = inputIndex >= 0 && inputIndex < customrecipe$requiredCounts.length
                ? customrecipe$requiredCounts[inputIndex] : vanillaAmount;
        return inventory.removeItem(slot, Math.max(vanillaAmount, required));
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void customrecipe$clearRequiredCounts(Player player, ItemStack result, CallbackInfo ci) {
        customrecipe$requiredCounts = new int[0];
    }
}
