package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.CustomRecipeMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Keeps a custom recipe chosen from the green book selected during shift-crafting. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Unique
    private RecipeHolder<CraftingRecipe> customrecipe$bookRecipe;

    @Inject(method = "finishPlacingRecipe", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.id().identifier().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;")
    )
    private static Optional<RecipeHolder<CraftingRecipe>> customrecipe$keepBookRecipe(
            RecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, Level level,
            RecipeHolder<CraftingRecipe> requested, AbstractContainerMenu menu, ServerLevel serverLevel,
            Player player, CraftingContainer crafting, ResultContainer result,
            RecipeHolder<CraftingRecipe> currentRecipe) {
        RecipeHolder<CraftingRecipe> preferred = requested;
        if (preferred == null && menu instanceof CraftingMenu) {
            CraftingMenuMixin mixin = (CraftingMenuMixin) (Object) menu;
            RecipeHolder<CraftingRecipe> saved = mixin.customrecipe$bookRecipe;
            if (saved != null && input instanceof CraftingInput craftingInput
                    && saved.value().matches(craftingInput, level)) {
                preferred = saved;
            } else if (saved != null) {
                mixin.customrecipe$bookRecipe = null;
            }
        }
        return manager.getRecipeFor(type, (CraftingInput) input, level, preferred);
    }
}
