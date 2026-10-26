package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import fr.zazac1.customrecipe.CustomRecipeMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookMenu.class)
abstract class RecipeBookMenuMixin implements CustomRecipeBookSelection {
    @Unique private Recipe<?> customrecipe$bookRecipe;

    @Inject(method = "handlePlacement", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(boolean craftAll, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    @Inject(method = "handlePlacement", at = @At("RETURN"))
    private void customrecipe$refreshCustomBookResult(boolean craftAll, Recipe<?> recipe, ServerPlayer player, CallbackInfo ci) {
        if (!recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) return;
        customrecipe$fillCountedIngredients(recipe, player, craftAll);
        RecipeBookMenu<?> menu = (RecipeBookMenu<?>) (Object) this;
        menu.getSlot(menu.getResultSlotIndex() + 1).setChanged();
    }

    @Unique
    private void customrecipe$fillCountedIngredients(Recipe<?> recipe, ServerPlayer player, boolean craftAll) {
        if (!(recipe instanceof CountedCraftingRecipe counted)) return;
        RecipeBookMenu<?> menu = (RecipeBookMenu<?>) (Object) this;
        int width = menu.getGridWidth();
        int height = menu.getGridHeight();
        int firstInputSlot = menu.getResultSlotIndex() + 1;
        CraftingContainer input = new CraftingContainer(menu, width, height);
        for (int index = 0; index < width * height; index++) {
            input.setItem(index, menu.getSlot(firstInputSlot + index).getItem().copy());
        }
        int[] required = counted.placementCounts(input);
        if (required.length != input.getContainerSize()) return;

        Inventory inventory = player.getInventory();
        int maxCrafts = 1;
        if (craftAll) {
            maxCrafts = 64;
            for (int index = 0; index < required.length; index++) {
                if (required[index] <= 0) continue;
                maxCrafts = Math.min(maxCrafts, input.getItem(index).getMaxStackSize() / required[index]);
            }
        }

        // Vanilla initially places equal stack sizes. Complete the largest batch
        // allowed by the configured quantities, slot limits, and remaining inventory.
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
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) available[slot] = inventory.getItem(slot).getCount();
            boolean canFill = true;
            for (Target target : requested) {
                int missing = target.missing();
                for (int slot = 0; slot < inventory.getContainerSize() && missing > 0; slot++) {
                    ItemStack candidate = inventory.getItem(slot);
                    if (available[slot] <= 0 || !ItemStack.isSameItemSameTags(candidate, target.stack())) continue;
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
            ItemStack completed = menu.getSlot(target.screenSlot()).getItem().copy();
            int missing = target.missing();
            for (int slot = 0; slot < inventory.getContainerSize() && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate.isEmpty() || !ItemStack.isSameItemSameTags(candidate, target.stack())) continue;
                ItemStack moved = inventory.removeItem(slot, Math.min(missing, candidate.getCount()));
                completed.grow(moved.getCount());
                missing -= moved.getCount();
            }
            menu.getSlot(target.screenSlot()).set(completed);
        }
        inventory.setChanged();
        menu.broadcastChanges();
    }

    @Override public Recipe<?> customrecipe$getBookRecipe() { return customrecipe$bookRecipe; }

    @Unique private record Target(int screenSlot, ItemStack stack, int missing) {}
}
