package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeVariantRule;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Optional;

/** Applies disabled recipes and material variants to the 1.21.1 RecipeEntry lookup API. */
@Mixin(RecipeManager.class)
abstract class RecipeManagerCraftingFilterMixin {
    @Shadow public abstract Collection<RecipeEntry<?>> values();

    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void customrecipe$filterFirst(RecipeType<T> type, I input, World world,
                                                                                       CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir) {
        ModConfig config = ConfigLoader.get();
        if (config.disabled_recipes.isEmpty() && config.disabled_recipe_variants.isEmpty()) return;
        for (RecipeEntry<?> candidate : values()) {
            if (!type.equals(candidate.value().getType())) continue;
            @SuppressWarnings("unchecked") RecipeEntry<T> entry = (RecipeEntry<T>) candidate;
            if (!blocked(entry, input, config) && entry.value().matches(input, world)) {
                cir.setReturnValue(Optional.of(entry));
                return;
            }
        }
        cir.setReturnValue(Optional.empty());
    }

    private static boolean blocked(RecipeEntry<?> entry, RecipeInput input, ModConfig config) {
        if (config.disabled_recipes.contains(entry.id().toString())) return true;
        if (!(entry.value() instanceof CraftingRecipe) || !(input instanceof CraftingRecipeInput crafting)) return false;
        for (RecipeVariantRule rule : config.disabled_recipe_variants) {
            if (!entry.id().toString().equals(rule.recipe_id)) continue;
            for (int slot = 0; slot < crafting.getSize(); slot++) {
                ItemStack stack = crafting.getStackInSlot(slot);
                if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(rule.material_id)) return true;
            }
        }
        return false;
    }
}
