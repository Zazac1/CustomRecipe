package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** Sends the vanilla delegate through Fabric's serializer-based recipe sync. */
@Mixin(targets = "net.fabricmc.fabric.impl.recipe.sync.ClientboundRecipeSyncPayload$Entry", remap = false)
public abstract class FabricRecipeSyncEntryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<RecipeHolder<?>> customrecipe$networkSafeRecipes(List<RecipeHolder<?>> recipes) {
        List<RecipeHolder<?>> networkRecipes = new ArrayList<>(recipes.size());
        for (RecipeHolder<?> holder : recipes) {
            CraftingRecipe recipe = unwrap(holder.value());
            if (recipe != null) {
                networkRecipes.add(rewrap(holder, recipe));
            }
        }
        return networkRecipes;
    }

    private static CraftingRecipe unwrap(Object recipe) {
        while (true) {
            if (recipe instanceof DisabledCraftingRecipe) return null;
            if (recipe instanceof CustomItemCraftingRecipe custom) {
                recipe = custom.delegate();
            } else if (recipe instanceof VariantFilteredCraftingRecipe filtered) {
                recipe = filtered.delegate();
            } else {
                return recipe instanceof CraftingRecipe crafting ? crafting : null;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeHolder<?> rewrap(RecipeHolder<?> holder, CraftingRecipe recipe) {
        return new RecipeHolder(holder.id(), recipe);
    }
}
