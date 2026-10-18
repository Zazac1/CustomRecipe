package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CountedCraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Completes recipe-book placement from vanilla's one item to each configured per-slot amount. */
@Mixin(value = RecipeBookMenu.class, remap = false)
public abstract class RecipeBookPlacementMixin {
    @Shadow public abstract int getResultSlotIndex();
    @Shadow public abstract int getGridWidth();
    @Shadow public abstract int getGridHeight();

    @Inject(method = "handlePlacement", at = @At("RETURN"), remap = false)
    private void customrecipe$fillCountedIngredients(boolean useMaxItems, RecipeHolder<?> recipe,
                                                      ServerPlayer player, CallbackInfo ci) {
        if (!(recipe.value() instanceof CountedCraftingRecipe counted)) return;
        int width = getGridWidth();
        int height = getGridHeight();
        int firstInputSlot = getResultSlotIndex() + 1;
        List<ItemStack> grid = new ArrayList<>(width * height);
        for (int index = 0; index < width * height; index++) {
            grid.add(customrecipe$menu().getSlot(firstInputSlot + index).getItem());
        }

        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(width, height, grid);
        CraftingInput input = positioned.input();
        int[] required = counted.placementCounts(input);
        if (required.length != input.size()) return;

        List<Target> targets = new ArrayList<>();
        for (int index = 0; index < required.length; index++) {
            int amount = required[index];
            if (amount <= 1) continue;
            int x = index % input.width() + positioned.left();
            int y = index / input.width() + positioned.top();
            int menuSlot = firstInputSlot + y * width + x;
            ItemStack placed = customrecipe$menu().getSlot(menuSlot).getItem();
            if (placed.isEmpty() || placed.getCount() >= amount || amount > placed.getMaxStackSize()) continue;
            targets.add(new Target(menuSlot, placed.copy(), amount - placed.getCount()));
        }
        if (targets.isEmpty()) return;

        Inventory inventory = player.getInventory();
        int[] available = new int[inventory.getContainerSize()];
        for (int slot = 0; slot < available.length; slot++) available[slot] = inventory.getItem(slot).getCount();
        for (Target target : targets) {
            int missing = target.missing();
            for (int slot = 0; slot < available.length && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (available[slot] <= 0 || !ItemStack.isSameItemSameComponents(candidate, target.stack())) continue;
                int reserved = Math.min(missing, available[slot]);
                available[slot] -= reserved;
                missing -= reserved;
            }
            if (missing > 0) return;
        }

        for (Target target : targets) {
            ItemStack completed = customrecipe$menu().getSlot(target.menuSlot()).getItem().copy();
            int missing = target.missing();
            for (int slot = 0; slot < inventory.getContainerSize() && missing > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(candidate, target.stack())) continue;
                ItemStack moved = inventory.removeItem(slot, Math.min(missing, candidate.getCount()));
                completed.grow(moved.getCount());
                missing -= moved.getCount();
            }
            customrecipe$menu().getSlot(target.menuSlot()).set(completed);
        }
        inventory.setChanged();
        customrecipe$menu().broadcastChanges();
    }

    @Unique
    private RecipeBookMenu<?, ?> customrecipe$menu() {
        return (RecipeBookMenu<?, ?>) (Object) this;
    }

    @Unique
    private record Target(int menuSlot, ItemStack stack, int missing) {}
}
