package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Completes recipe-book placement from vanilla's one item to each configured per-slot amount. */
@Mixin(AbstractRecipeScreenHandler.class)
public abstract class RecipeBookPlacementMixin {
   @Shadow public abstract int getCraftingResultSlotIndex();
   @Shadow public abstract int getCraftingWidth();
   @Shadow public abstract int getCraftingHeight();

   @Inject(method = "fillInputSlots", at = @At("RETURN"))
   private void customrecipe$fillCountedIngredients(boolean useMaxItems, RecipeEntry<?> recipe,
                                                     ServerPlayerEntity player, CallbackInfo ci) {
      if (!(recipe.value() instanceof CountedCraftingRecipe counted)) return;
      int width = getCraftingWidth();
      int height = getCraftingHeight();
      int firstInputSlot = getCraftingResultSlotIndex() + 1;
      List<ItemStack> grid = new ArrayList<>(width * height);
      for (int index = 0; index < width * height; index++) {
         grid.add(customrecipe$handler().getSlot(firstInputSlot + index).getStack());
      }

      CraftingRecipeInput.Positioned positioned = CraftingRecipeInput.createPositioned(width, height, grid);
      CraftingRecipeInput input = positioned.input();
      int[] required = counted.placementCounts(input);
      if (required.length != input.getSize()) return;

      List<Target> targets = new ArrayList<>();
      for (int index = 0; index < required.length; index++) {
         int amount = required[index];
         if (amount <= 1) continue;
         int x = index % input.getWidth() + positioned.left();
         int y = index / input.getWidth() + positioned.top();
         int screenSlot = firstInputSlot + y * width + x;
         ItemStack placed = customrecipe$handler().getSlot(screenSlot).getStack();
         if (placed.isEmpty() || placed.getCount() >= amount || amount > placed.getMaxCount()) continue;
         targets.add(new Target(screenSlot, placed.copy(), amount - placed.getCount()));
      }
      if (targets.isEmpty()) return;

      PlayerInventory inventory = player.getInventory();
      int[] available = new int[inventory.size()];
      for (int slot = 0; slot < available.length; slot++) available[slot] = inventory.getStack(slot).getCount();
      for (Target target : targets) {
         int missing = target.missing();
         for (int slot = 0; slot < available.length && missing > 0; slot++) {
            ItemStack candidate = inventory.getStack(slot);
            if (available[slot] <= 0 || !ItemStack.areItemsAndComponentsEqual(candidate, target.stack())) continue;
            int reserved = Math.min(missing, available[slot]);
            available[slot] -= reserved;
            missing -= reserved;
         }
         if (missing > 0) return;
      }

      for (Target target : targets) {
         ItemStack completed = customrecipe$handler().getSlot(target.screenSlot()).getStack().copy();
         int missing = target.missing();
         for (int slot = 0; slot < inventory.size() && missing > 0; slot++) {
            ItemStack candidate = inventory.getStack(slot);
            if (candidate.isEmpty() || !ItemStack.areItemsAndComponentsEqual(candidate, target.stack())) continue;
            ItemStack moved = inventory.removeStack(slot, Math.min(missing, candidate.getCount()));
            completed.increment(moved.getCount());
            missing -= moved.getCount();
         }
         customrecipe$handler().getSlot(target.screenSlot()).setStack(completed);
      }
      inventory.markDirty();
      customrecipe$handler().sendContentUpdates();
   }

   private AbstractRecipeScreenHandler<?, ?> customrecipe$handler() {
      return (AbstractRecipeScreenHandler<?, ?>) (Object) this;
   }

   private record Target(int screenSlot, ItemStack stack, int missing) {}
}
