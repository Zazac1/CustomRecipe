package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.CountedCraftingRecipe;
import fr.zazac1.customrecipe.Java8;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.RecipeBookContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookContainer.class)
abstract class RecipeBookMenuMixin implements CustomRecipeBookSelection {
    @Unique private IRecipe<?> customrecipe$bookRecipe;

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(boolean craftAll, IRecipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    @Inject(method = "handlePlacement", at = @At("RETURN"))
    private void customrecipe$refreshCustomBookResult(boolean craftAll, IRecipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        if (!recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) return;
        customrecipe$fillCountedIngredients(recipe, player, craftAll);
        RecipeBookContainer<?> menu = (RecipeBookContainer<?>) (Object) this;
        menu.getSlot(menu.getResultSlotIndex() + 1).setChanged();
    }

    @Unique
    private void customrecipe$fillCountedIngredients(IRecipe<?> recipe, ServerPlayerEntity player, boolean craftAll) {
        if (!(recipe instanceof CountedCraftingRecipe)) return;
        CountedCraftingRecipe counted = (CountedCraftingRecipe) recipe;
        RecipeBookContainer<?> menu = (RecipeBookContainer<?>) (Object) this;
        int width = menu.getGridWidth();
        int height = menu.getGridHeight();
        int firstInputSlot = menu.getResultSlotIndex() + 1;
        CraftingInventory input = new CraftingInventory(menu, width, height);
        for (int index = 0; index < width * height; index++) {
            input.setItem(index, menu.getSlot(firstInputSlot + index).getItem().copy());
        }
        int[] required = counted.placementCounts(input);
        if (required.length != input.getContainerSize()) return;

        PlayerInventory inventory = player.inventory;
        int maxCrafts = 1;
        if (craftAll) {
            maxCrafts = 64;
            for (int index = 0; index < required.length; index++) {
                if (required[index] <= 0) continue;
                maxCrafts = Math.min(maxCrafts, input.getItem(index).getMaxStackSize() / required[index]);
            }
        }

        List<Target> targets = null;
        for (int crafts = maxCrafts; crafts > 0; crafts--) {
            List<Target> requested = new ArrayList<>();
            for (int index = 0; index < required.length; index++) {
                ItemStack placed = input.getItem(index);
                int amount = required[index] * crafts;
                if (!placed.isEmpty() && amount > placed.getCount()) {
                    requested.add(new Target(firstInputSlot + index, placed, amount - placed.getCount()));
                }
            }

            int[] available = new int[inventory.getContainerSize()];
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                available[slot] = inventory.getItem(slot).getCount();
            }
            boolean canFill = true;
            for (Target target : requested) {
                int missing = target.missing;
                for (int slot = 0; slot < inventory.getContainerSize() && missing > 0; slot++) {
                    ItemStack candidate = inventory.getItem(slot);
                    if (available[slot] <= 0 || !Java8.sameItemAndTag(candidate, target.stack)) continue;
                    int reserved = Math.min(missing, available[slot]);
                    available[slot] -= reserved;
                    missing -= reserved;
                }
                if (missing > 0) {
                    canFill = false;
                    break;
                }
            }
            if (canFill) {
                targets = requested;
                break;
            }
        }
        if (targets == null || targets.isEmpty()) return;

        for (Target target : targets) {
            ItemStack completed = menu.getSlot(target.screenSlot).getItem().copy();
            int missing = target.missing;
            for (int slot = 0; slot < inventory.getContainerSize() && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate.isEmpty() || !Java8.sameItemAndTag(candidate, target.stack)) continue;
                ItemStack moved = inventory.removeItem(slot, Math.min(missing, candidate.getCount()));
                completed.grow(moved.getCount());
                missing -= moved.getCount();
            }
            menu.getSlot(target.screenSlot).set(completed);
        }
        inventory.setChanged();
        menu.broadcastChanges();
    }

    @Unique
    private static final class Target {
        private final int screenSlot;
        private final ItemStack stack;
        private final int missing;
        private Target(int screenSlot, ItemStack stack, int missing) {
            this.screenSlot = screenSlot;
            this.stack = stack;
            this.missing = missing;
        }
    }

    @Override public IRecipe<?> customrecipe$getBookRecipe() { return customrecipe$bookRecipe; }
}
