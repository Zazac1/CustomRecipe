package fr.zazac1.customrecipe.mixin;

import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Makes the recipe book account for the amount encoded in counted ingredient displays. */
@Mixin(RecipeDisplayEntry.class)
public abstract class RecipeDisplayEntryMixin {
    @Shadow public abstract RecipeDisplay display();

    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private void customrecipe$canCraftCountedDisplay(StackedItemContents contents,
                                                      CallbackInfoReturnable<Boolean> cir) {
        List<SlotDisplay> slots = switch (display()) {
            case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
            case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
            default -> null;
        };
        if (slots == null) return;

        boolean counted = false;
        List<Ingredient> requirements = new ArrayList<>();
        for (SlotDisplay slot : slots) {
            if (slot instanceof SlotDisplay.Empty) continue;
            if (!(slot instanceof SlotDisplay.ItemStackSlotDisplay itemStack)) return;

            int count = itemStack.stack().count();
            counted |= count > 1;
            Ingredient ingredient = Ingredient.of(itemStack.stack().item().value());
            for (int item = 0; item < count; item++) requirements.add(ingredient);
        }

        if (counted) cir.setReturnValue(contents.canCraft(requirements, null));
    }
}
