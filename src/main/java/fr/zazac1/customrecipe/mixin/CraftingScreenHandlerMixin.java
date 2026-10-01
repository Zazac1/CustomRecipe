package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * Keeps a custom recipe explicitly selected by the recipe book during
 * shift-crafting. 1.21.1 moved this lookup into slotChangedCraftingGrid.
 */
@Mixin(value = CraftingMenu.class, remap = false)
public abstract class CraftingScreenHandlerMixin {
    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"),
            remap = false
    )
    private static Optional<RecipeHolder<CraftingRecipe>> customrecipe$keepBookRecipe(
            RecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, Level level,
            RecipeHolder<CraftingRecipe> requested) {
        if (input instanceof CraftingInput craftingInput
                && requested != null
                && requested.id().getNamespace().equals(CustomRecipeMod.MOD_ID)
                && requested.value().matches(craftingInput, level)) {
            return Optional.of(requested);
        }
        // CraftingMenu always passes CraftingInput here; the instance check keeps the
        // redirect compatible with RecipeManager's erased RecipeInput signature.
        return input instanceof CraftingInput craftingInput
                ? manager.getRecipeFor(type, craftingInput, level, requested)
                : Optional.empty();
    }
}
