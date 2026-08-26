package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.ModConfig;
import fr.isaac.customrecipe.RecipeVariantRule;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * In 1.21.1 recipe packets encode each concrete vanilla recipe class directly.
 * Keep entries native for networking and filter them only when the server looks
 * for a matching craft.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerCraftingFilterMixin {

    @Shadow public abstract <I extends RecipeInput, T extends Recipe<I>> List<RecipeEntry<T>> listAllOfType(RecipeType<T> type);

    @Inject(
            method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void customrecipe$findAllowedMatch(
            RecipeType<T> type, I input, World world, CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        ModConfig config = ConfigLoader.get();
        if (config.disabled_recipes.isEmpty() && config.disabled_recipe_variants.isEmpty()) return;

        cir.setReturnValue(findAllowedMatch(type, input, world, config));
    }

    /** The crafting table normally calls this cached-recipe overload on every slot update. */
    @Inject(
            method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void customrecipe$findAllowedCachedMatch(
            RecipeType<T> type, I input, World world, RecipeEntry<T> cachedRecipe,
            CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        ModConfig config = ConfigLoader.get();
        if (config.disabled_recipes.isEmpty() && config.disabled_recipe_variants.isEmpty()) return;

        cir.setReturnValue(findAllowedMatch(type, input, world, config));
    }

    @Inject(
            method = "getAllMatches(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void customrecipe$filterAllMatches(
            RecipeType<T> type, I input, World world, CallbackInfoReturnable<List<RecipeEntry<T>>> cir) {
        ModConfig config = ConfigLoader.get();
        if (config.disabled_recipes.isEmpty() && config.disabled_recipe_variants.isEmpty()) return;
        cir.setReturnValue(cir.getReturnValue().stream()
                .filter(entry -> !isBlocked(entry, input, config))
                .toList());
    }

    private static boolean isBlocked(RecipeEntry<?> entry, RecipeInput input, ModConfig config) {
        if (config.disabled_recipes.contains(entry.id().toString())) return true;
        if (!(entry.value() instanceof CraftingRecipe) || !(input instanceof CraftingRecipeInput craftingInput)) return false;

        for (RecipeVariantRule rule : config.disabled_recipe_variants) {
            if (!entry.id().toString().equals(rule.recipe_id)) continue;
            for (ItemStack stack : craftingInput.getStacks()) {
                if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(rule.material_id)) return true;
            }
        }
        return false;
    }

    private <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeEntry<T>> findAllowedMatch(
            RecipeType<T> type, I input, World world, ModConfig config) {
        for (RecipeEntry<T> entry : listAllOfType(type)) {
            if (!isBlocked(entry, input, config) && entry.value().matches(input, world)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
