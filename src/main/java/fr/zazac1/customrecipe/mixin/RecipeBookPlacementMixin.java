package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Completes recipe-book placement from vanilla's one item to each configured per-slot amount. */
@Mixin(value = AbstractCraftingMenu.class, remap = false)
public abstract class RecipeBookPlacementMixin {
    @Shadow @Final protected CraftingContainer craftSlots;

    @Inject(method = "handlePlacement", at = @At("RETURN"), remap = false)
    private void customrecipe$fillCountedIngredients(boolean useMaxItems, boolean bypassClearCheck,
                                                      RecipeHolder<?> recipe, ServerLevel level,
                                                      Inventory inventory,
                                                      CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir) {
        if (!(recipe.value() instanceof CountedCraftingRecipe counted)) return;

        CraftingInput.Positioned positioned = craftSlots.asPositionedCraftInput();
        CraftingInput input = positioned.input();
        int[] required = counted.placementCounts(input);
        if (required.length != input.size()) return;

        List<Target> targets = new ArrayList<>();
        int gridWidth = craftSlots.getWidth();
        for (int index = 0; index < required.length; index++) {
            int amount = required[index];
            if (amount <= 1) continue;
            int x = index % input.width() + positioned.left();
            int y = index / input.width() + positioned.top();
            int gridIndex = y * gridWidth + x;
            ItemStack placed = craftSlots.getItem(gridIndex);
            if (placed.isEmpty() || placed.getCount() >= amount || amount > placed.getMaxStackSize()) continue;
            targets.add(new Target(gridIndex, placed, amount - placed.getCount()));
        }
        if (targets.isEmpty()) return;

        int inventorySize = inventory.getContainerSize();
        int[] available = new int[inventorySize];
        for (int slot = 0; slot < inventorySize; slot++) available[slot] = inventory.getItem(slot).getCount();
        for (Target target : targets) {
            int missing = target.missing();
            for (int slot = 0; slot < inventorySize && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (available[slot] <= 0 || !ItemStack.isSameItemSameComponents(candidate, target.stack())) continue;
                int reserved = Math.min(missing, available[slot]);
                available[slot] -= reserved;
                missing -= reserved;
            }
            if (missing > 0) return;
        }

        for (Target target : targets) {
            int missing = target.missing();
            ItemStack completed = target.stack().copy();
            for (int slot = 0; slot < inventorySize && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(candidate, target.stack())) continue;
                ItemStack moved = inventory.removeItem(slot, Math.min(missing, candidate.getCount()));
                completed.grow(moved.getCount());
                missing -= moved.getCount();
            }
            craftSlots.setItem(target.gridIndex(), completed);
        }
        inventory.setChanged();
        ((AbstractContainerMenu) (Object) this).broadcastChanges();
    }

    private record Target(int gridIndex, ItemStack stack, int missing) {}
}
