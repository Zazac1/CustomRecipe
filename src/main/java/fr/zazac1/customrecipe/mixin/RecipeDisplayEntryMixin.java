package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.access.ExactRecipeFinderAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Uses counted ingredient displays and exact custom stacks for recipe-book availability. */
@Mixin(RecipeDisplayEntry.class)
public abstract class RecipeDisplayEntryMixin {
    @Shadow public abstract RecipeDisplay display();

    @Inject(method = "isCraftable", at = @At("HEAD"), cancellable = true)
    private void customrecipe$isCraftable(RecipeFinder finder, CallbackInfoReturnable<Boolean> cir) {
        List<SlotDisplay> slots = switch (display()) {
            case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
            case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
            default -> null;
        };
        if (slots == null || !(finder instanceof ExactRecipeFinderAccess exactFinder)) return;

        List<ItemStack> requirements = new ArrayList<>();
        for (SlotDisplay slot : slots) {
            if (slot instanceof SlotDisplay.EmptySlotDisplay) continue;
            if (!(slot instanceof SlotDisplay.StackSlotDisplay itemStack)) return;
            requirements.add(itemStack.stack().copy());
        }

        if (!requirements.isEmpty()) {
            cir.setReturnValue(customrecipe$canCraftExact(requirements, exactFinder.customrecipe$exactStacks()));
        }
    }

    private static boolean customrecipe$canCraftExact(List<ItemStack> requirements, List<ItemStack> availableStacks) {
        int[] available = new int[availableStacks.size()];
        for (int i = 0; i < available.length; i++) available[i] = availableStacks.get(i).getCount();

        for (ItemStack expected : requirements) {
            int missing = expected.getCount();
            for (int i = 0; i < availableStacks.size() && missing > 0; i++) {
                if (available[i] <= 0 || !CustomItemLibrary.exactMatch(availableStacks.get(i), expected)) continue;
                int used = Math.min(missing, available[i]);
                available[i] -= used;
                missing -= used;
            }
            if (missing > 0) return false;
        }
        return true;
    }
}
