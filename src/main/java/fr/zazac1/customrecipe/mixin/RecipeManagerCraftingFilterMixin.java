package fr.zazac1.customrecipe.mixin;

import com.mojang.datafixers.util.Pair;
import fr.zazac1.customrecipe.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerCraftingFilterMixin {
    @Shadow public abstract <C extends Container, T extends Recipe<C>> List<T> getAllRecipesFor(RecipeType<T> type);
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void filterFirst(RecipeType<T> type, C input, Level world, CallbackInfoReturnable<Optional<T>> cir) {
        WorldRecipeConfig c = ConfigLoader.activeWorldConfig(); if (c.disabled_recipes.isEmpty() && c.disabled_recipe_variants.isEmpty()) return;
        for (T r : getAllRecipesFor(type)) if (!blocked(r, input, c) && r.matches(input, world)) { cir.setReturnValue(Optional.of(r)); return; } cir.setReturnValue(Optional.empty());
    }
    /** Crafting tables use this cached overload in 1.20.1. */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void filterCachedFirst(RecipeType<T> type, C input, Level world, ResourceLocation ignoredId, CallbackInfoReturnable<Optional<Pair<ResourceLocation, T>>> cir) {
        WorldRecipeConfig c = ConfigLoader.activeWorldConfig(); if (c.disabled_recipes.isEmpty() && c.disabled_recipe_variants.isEmpty()) return;
        for (T r : getAllRecipesFor(type)) if (!blocked(r, input, c) && r.matches(input, world)) { cir.setReturnValue(Optional.of(Pair.of(r.getId(), r))); return; } cir.setReturnValue(Optional.empty());
    }
    @Inject(method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void filterAll(RecipeType<T> type, C input, Level world, CallbackInfoReturnable<List<T>> cir) {
        WorldRecipeConfig c = ConfigLoader.activeWorldConfig(); if (!c.disabled_recipes.isEmpty() || !c.disabled_recipe_variants.isEmpty()) cir.setReturnValue(cir.getReturnValue().stream().filter(r -> !blocked(r, input, c)).toList());
    }
    private static boolean blocked(Recipe<?> r, Container input, WorldRecipeConfig c) {
        if (c.disabled_recipes.contains(r.getId().toString())) return true;
        if (!(r instanceof CraftingRecipe)) return false;
        for (RecipeVariantRule rule : c.disabled_recipe_variants) if (r.getId().toString().equals(rule.recipe_id)) for (int i=0;i<input.getContainerSize();i++) { ItemStack s=input.getItem(i); if(!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(rule.material_id)) return true; }
        return false;
    }
}
