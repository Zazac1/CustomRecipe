package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Optional;

/** Applies disabled recipes and material variants to the 1.21.1 RecipeHolder lookup API. */
@Mixin(value = RecipeManager.class, remap = false)
abstract class RecipeManagerCraftingFilterMixin {
    @Shadow(remap = false) public abstract Collection<RecipeHolder<?>> getRecipes();

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, remap = false)
    private <I extends net.minecraft.world.item.crafting.RecipeInput, T extends Recipe<I>> void customrecipe$filterFirst(RecipeType<T> type, I input, Level world,
                                                                                       CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig();
        filter(type, input, world, config, cir);
    }

    /**
     * CraftingMenu and RecipeManager.CachedCheck use this preferred-recipe
     * overload in 1.21.1.  Filtering only the three-argument overload left
     * disabled vanilla recipes craftable from the normal in-game grid.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, remap = false)
    private <I extends net.minecraft.world.item.crafting.RecipeInput, T extends Recipe<I>> void customrecipe$filterPreferred(
            RecipeType<T> type, I input, Level world, RecipeHolder<T> preferred,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig();
        filter(type, input, world, config, cir);
    }

    private <I extends net.minecraft.world.item.crafting.RecipeInput, T extends Recipe<I>> void filter(
            RecipeType<T> type, I input, Level world, WorldRecipeConfig config,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        if (config.disabled_recipes.isEmpty() && config.disabled_recipe_variants.isEmpty()) return;
        for (RecipeHolder<?> candidate : getRecipes()) {
            if (!type.equals(candidate.value().getType())) continue;
            @SuppressWarnings("unchecked") RecipeHolder<T> entry = (RecipeHolder<T>) candidate;
            if (!blocked(entry, input, config) && entry.value().matches(input, world)) {
                cir.setReturnValue(Optional.of(entry));
                return;
            }
        }
        cir.setReturnValue(Optional.empty());
    }

    private static boolean blocked(RecipeHolder<?> entry, net.minecraft.world.item.crafting.RecipeInput input, WorldRecipeConfig config) {
        if (config.disabled_recipes.contains(entry.id().toString())) return true;
        if (!(entry.value() instanceof CraftingRecipe) || !(input instanceof CraftingInput crafting)) return false;
        for (RecipeVariantRule rule : config.disabled_recipe_variants) {
            if (!entry.id().toString().equals(rule.recipe_id)) continue;
            for (int slot = 0; slot < crafting.size(); slot++) {
                ItemStack stack = crafting.getItem(slot);
                if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(rule.material_id)) return true;
            }
        }
        return false;
    }
}
