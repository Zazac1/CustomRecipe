package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedRecipeBook;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.RecipeItemHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeItemHelper.class)
public abstract class RecipeItemHelperMixin {
    @Unique private final List<ItemStack> customrecipe$stacks = new ArrayList<>();

    @Inject(method = "clear", at = @At("HEAD"))
    private void customrecipe$clear(CallbackInfo ci) { customrecipe$stacks.clear(); }

    @Inject(method = "accountSimpleStack", at = @At("HEAD"))
    private void customrecipe$keepExactStacks(ItemStack stack, CallbackInfo ci) {
        // These are rejected before accountStack; accepted stacks are captured there.
        if (!stack.isEmpty() && (stack.isDamaged() || stack.isEnchanted() || stack.hasCustomHoverName())) {
            customrecipe$stacks.add(stack.copy());
        }
    }

    @Inject(method = "accountStack(Lnet/minecraft/item/ItemStack;I)V", at = @At("HEAD"))
    private void customrecipe$account(ItemStack stack, int limit, CallbackInfo ci) {
        if (stack.isEmpty() || limit < 1) return;
        ItemStack copy = stack.copy();
        copy.setCount(Math.min(stack.getCount(), limit));
        customrecipe$stacks.add(copy);
    }

    @Inject(method = "canCraft(Lnet/minecraft/item/crafting/IRecipe;Lit/unimi/dsi/fastutil/ints/IntList;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void customrecipe$canCraft(IRecipe<?> recipe, IntList indices, int crafts, CallbackInfoReturnable<Boolean> cir) {
        if (!CountedRecipeBook.handles(recipe)) return;
        ItemStack[] allocation = CountedRecipeBook.allocate(recipe, customrecipe$stacks, crafts);
        if (allocation != null && indices != null) {
            indices.clear();
            for (ItemStack stack : allocation) indices.add(stack.isEmpty() ? 0 : RecipeItemHelper.getStackingIndex(stack));
        }
        cir.setReturnValue(allocation != null);
    }
}
