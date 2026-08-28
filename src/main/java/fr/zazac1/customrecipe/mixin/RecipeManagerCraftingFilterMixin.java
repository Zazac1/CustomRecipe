package fr.zazac1.customrecipe.mixin;

import com.mojang.datafixers.util.Pair;
import fr.zazac1.customrecipe.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerCraftingFilterMixin {
    @Shadow public abstract <C extends Inventory, T extends Recipe<C>> List<T> listAllOfType(RecipeType<T> type);
    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <C extends Inventory, T extends Recipe<C>> void filterFirst(RecipeType<T> type, C input, World world, CallbackInfoReturnable<Optional<T>> cir) {
        ModConfig c = ConfigLoader.get(); if (c.disabled_recipes.isEmpty() && c.disabled_recipe_variants.isEmpty()) return;
        for (T r : listAllOfType(type)) if (!blocked(r, input, c) && r.matches(input, world)) { cir.setReturnValue(Optional.of(r)); return; } cir.setReturnValue(Optional.empty());
    }
    /** Crafting tables use this cached overload in 1.20.1. */
    @Inject(method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;Lnet/minecraft/util/Identifier;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <C extends Inventory, T extends Recipe<C>> void filterCachedFirst(RecipeType<T> type, C input, World world, Identifier ignoredId, CallbackInfoReturnable<Optional<Pair<Identifier, T>>> cir) {
        ModConfig c = ConfigLoader.get(); if (c.disabled_recipes.isEmpty() && c.disabled_recipe_variants.isEmpty()) return;
        for (T r : listAllOfType(type)) if (!blocked(r, input, c) && r.matches(input, world)) { cir.setReturnValue(Optional.of(Pair.of(r.getId(), r))); return; } cir.setReturnValue(Optional.empty());
    }
    @Inject(method = "getAllMatches(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private <C extends Inventory, T extends Recipe<C>> void filterAll(RecipeType<T> type, C input, World world, CallbackInfoReturnable<List<T>> cir) {
        ModConfig c = ConfigLoader.get(); if (!c.disabled_recipes.isEmpty() || !c.disabled_recipe_variants.isEmpty()) cir.setReturnValue(cir.getReturnValue().stream().filter(r -> !blocked(r, input, c)).toList());
    }
    private static boolean blocked(Recipe<?> r, Inventory input, ModConfig c) {
        if (c.disabled_recipes.contains(r.getId().toString())) return true;
        if (!(r instanceof CraftingRecipe)) return false;
        for (RecipeVariantRule rule : c.disabled_recipe_variants) if (r.getId().toString().equals(rule.recipe_id)) for (int i=0;i<input.size();i++) { ItemStack s=input.getStack(i); if(!s.isEmpty() && Registries.ITEM.getId(s.getItem()).toString().equals(rule.material_id)) return true; }
        return false;
    }
}
