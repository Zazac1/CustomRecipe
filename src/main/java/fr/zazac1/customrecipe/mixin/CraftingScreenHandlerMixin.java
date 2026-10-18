package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.CountedCraftingGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Keeps a custom recipe chosen from the green book selected during shift-crafting. */
@Mixin(CraftingMenu.class)
public abstract class CraftingScreenHandlerMixin {
    @Unique
    private RecipeHolder<CraftingRecipe> customrecipe$bookRecipe;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void customrecipe$rejectIncompleteCountedCraft(Player player, int slotIndex,
                                                            CallbackInfoReturnable<ItemStack> cir) {
        if (slotIndex != 0 || !(player.level() instanceof ServerLevel)) return;
        CraftingMenu handler = (CraftingMenu) (Object) this;
        Object inputContainer = handler.getSlot(1).container;
        Object resultContainer = handler.getSlot(0).container;
        if (!(inputContainer instanceof CraftingContainer craftSlots)
                || !(resultContainer instanceof net.minecraft.world.inventory.RecipeCraftingHolder recipeHolder)
                || CountedCraftingGuard.canTake(craftSlots, recipeHolder)) return;

        ((net.minecraft.world.Container) resultContainer).setItem(0, ItemStack.EMPTY);
        ((AbstractContainerMenu) (Object) this).broadcastChanges();
        cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "finishPlacingRecipe", at = @At("HEAD"))
    private void customrecipe$rememberBookRecipe(ServerLevel world, RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        customrecipe$bookRecipe = recipe.id().identifier().getNamespace().equals(CustomRecipeMod.MOD_ID) ? recipe : null;
    }

    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;")
    )
    private static Optional<RecipeHolder<CraftingRecipe>> customrecipe$keepBookRecipe(
            RecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, Level world,
            RecipeHolder<CraftingRecipe> requested, AbstractContainerMenu handler, ServerLevel serverWorld,
            Player player, CraftingContainer inventory, ResultContainer resultInventory,
            RecipeHolder<CraftingRecipe> currentRecipe) {
        RecipeHolder<CraftingRecipe> preferred = requested;
        if (preferred == null && handler instanceof CraftingMenu) {
            CraftingScreenHandlerMixin mixin = (CraftingScreenHandlerMixin) (Object) handler;
            RecipeHolder<CraftingRecipe> saved = mixin.customrecipe$bookRecipe;
            if (saved != null && input instanceof CraftingInput craftingInput && saved.value().matches(craftingInput, world)) {
                preferred = saved;
            } else if (saved != null) {
                mixin.customrecipe$bookRecipe = null;
            }
        }
        CraftingInput craftingInput = (CraftingInput) input;
        return manager.getRecipeFor(type, craftingInput, world, preferred)
                .filter(recipe -> CountedCraftingGuard.matches(craftingInput, recipe));
    }
}
