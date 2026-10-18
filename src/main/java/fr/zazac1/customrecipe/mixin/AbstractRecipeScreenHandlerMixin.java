package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeBookSelection;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.CountedCraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeScreenHandler.class)
public abstract class AbstractRecipeScreenHandlerMixin implements CustomRecipeBookSelection {
    @Unique
    private Recipe<?> customrecipe$bookRecipe;

    @Inject(method = "fillInputSlots", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(boolean craftAll, Recipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    /**
     * InputSlotFiller uses no-callback slot writes. Refresh one crafting input
     * after a custom selection so the result is recalculated immediately.
     */
    @Inject(method = "fillInputSlots", at = @At("RETURN"))
    private void customrecipe$refreshCustomBookResult(boolean craftAll, Recipe<?> recipe, ServerPlayerEntity player, CallbackInfo ci) {
        if (!recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) return;
        customrecipe$fillCountedIngredients(recipe, player);
        AbstractRecipeScreenHandler<?> handler = (AbstractRecipeScreenHandler<?>) (Object) this;
        handler.getSlot(handler.getCraftingResultSlotIndex() + 1).markDirty();
    }

    @Unique
    private void customrecipe$fillCountedIngredients(Recipe<?> recipe, ServerPlayerEntity player) {
        if (!(recipe instanceof CountedCraftingRecipe counted)) return;
        AbstractRecipeScreenHandler<?> handler = (AbstractRecipeScreenHandler<?>) (Object) this;
        int width = handler.getCraftingWidth();
        int height = handler.getCraftingHeight();
        int firstInputSlot = handler.getCraftingResultSlotIndex() + 1;
        CraftingInventory input = new CraftingInventory(handler, width, height);
        for (int index = 0; index < width * height; index++) {
            input.setStack(index, handler.getSlot(firstInputSlot + index).getStack().copy());
        }
        int[] required = counted.placementCounts(input);
        if (required.length != input.size()) return;

        List<Target> targets = new ArrayList<>();
        for (int index = 0; index < required.length; index++) {
            ItemStack placed = input.getStack(index);
            int amount = required[index];
            if (placed.isEmpty() || amount <= placed.getCount() || amount > placed.getMaxCount()) continue;
            targets.add(new Target(firstInputSlot + index, placed, amount - placed.getCount()));
        }
        if (targets.isEmpty()) return;

        PlayerInventory inventory = player.getInventory();
        int[] available = new int[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) available[slot] = inventory.getStack(slot).getCount();
        for (Target target : targets) {
            int missing = target.missing();
            for (int slot = 0; slot < inventory.size() && missing > 0; slot++) {
                ItemStack candidate = inventory.getStack(slot);
                if (available[slot] <= 0 || !ItemStack.canCombine(candidate, target.stack())) continue;
                int reserved = Math.min(missing, available[slot]);
                available[slot] -= reserved;
                missing -= reserved;
            }
            if (missing > 0) return;
        }

        for (Target target : targets) {
            ItemStack completed = handler.getSlot(target.screenSlot()).getStack().copy();
            int missing = target.missing();
            for (int slot = 0; slot < inventory.size() && missing > 0; slot++) {
                ItemStack candidate = inventory.getStack(slot);
                if (candidate.isEmpty() || !ItemStack.canCombine(candidate, target.stack())) continue;
                ItemStack moved = inventory.removeStack(slot, Math.min(missing, candidate.getCount()));
                completed.increment(moved.getCount());
                missing -= moved.getCount();
            }
            handler.getSlot(target.screenSlot()).setStack(completed);
        }
        inventory.markDirty();
        handler.sendContentUpdates();
    }

    @Override
    public Recipe<?> customrecipe$getBookRecipe() {
        return customrecipe$bookRecipe;
    }

    @Unique
    private record Target(int screenSlot, ItemStack stack, int missing) {}
}
