package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Completes recipe-book placement from vanilla's one item to each configured per-slot amount. */
@Mixin(AbstractCraftingScreenHandler.class)
public abstract class RecipeBookPlacementMixin {
    @Shadow @Final protected RecipeInputInventory craftingInventory;

    @Inject(method = "fillInputSlots", at = @At("RETURN"))
    private void customrecipe$fillCountedIngredients(boolean useMaxItems, boolean bypassClearCheck,
                                                      RecipeEntry<?> recipe, ServerWorld level,
                                                      PlayerInventory inventory,
                                                      CallbackInfoReturnable<AbstractRecipeScreenHandler.PostFillAction> cir) {
        if (!(recipe.value() instanceof CountedCraftingRecipe counted)) return;

        CraftingRecipeInput.Positioned positioned = craftingInventory.createPositionedRecipeInput();
        CraftingRecipeInput input = positioned.input();
        int[] required = counted.placementCounts(input);
        if (required.length != input.size()) return;

        List<Target> targets = new ArrayList<>();
        int gridWidth = craftingInventory.getWidth();
        for (int index = 0; index < required.length; index++) {
            int amount = required[index];
            if (amount <= 1) continue;
            int x = index % input.getWidth() + positioned.left();
            int y = index / input.getWidth() + positioned.top();
            int gridIndex = y * gridWidth + x;
            ItemStack placed = craftingInventory.getStack(gridIndex);
            if (placed.isEmpty() || placed.getCount() >= amount || amount > placed.getMaxCount()) continue;
            targets.add(new Target(gridIndex, placed, amount - placed.getCount()));
        }
        if (targets.isEmpty()) return;

        // Reserve inventory contents first so an incomplete inventory never leaves
        // a misleading half-filled custom recipe behind.
        int inventorySize = inventory.size();
        int[] available = new int[inventorySize];
        for (int slot = 0; slot < inventorySize; slot++) available[slot] = inventory.getStack(slot).getCount();
        for (Target target : targets) {
            int missing = target.missing();
            for (int slot = 0; slot < inventorySize && missing > 0; slot++) {
                ItemStack candidate = inventory.getStack(slot);
                if (available[slot] <= 0 || !ItemStack.areItemsAndComponentsEqual(candidate, target.stack())) continue;
                int reserved = Math.min(missing, available[slot]);
                available[slot] -= reserved;
                missing -= reserved;
            }
            if (missing > 0) return;
        }

        int lastChangedGridSlot = -1;
        for (Target target : targets) {
            int missing = target.missing();
            for (int slot = 0; slot < inventorySize && missing > 0; slot++) {
                ItemStack candidate = inventory.getStack(slot);
                if (candidate.isEmpty() || !ItemStack.areItemsAndComponentsEqual(candidate, target.stack())) continue;
                ItemStack moved = inventory.removeStack(slot, Math.min(missing, candidate.getCount()));
                target.stack().increment(moved.getCount());
                missing -= moved.getCount();
            }
            lastChangedGridSlot = target.gridIndex();
        }
        inventory.markDirty();
        if (lastChangedGridSlot >= 0) {
            // The stacks were mutated without intermediate menu updates. Notify once
            // after every slot is complete so the counted recipe can match immediately.
            craftingInventory.setStack(lastChangedGridSlot, craftingInventory.getStack(lastChangedGridSlot));
        }
    }

    private record Target(int gridIndex, ItemStack stack, int missing) {}
}
