package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** Sends the vanilla delegate through Fabric's serializer-based recipe sync. */
@Mixin(targets = "net.fabricmc.fabric.impl.recipe.sync.RecipeSyncPayloadS2C$Entry", remap = false)
public abstract class FabricRecipeSyncEntryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<RecipeEntry<?>> customrecipe$networkSafeRecipes(List<RecipeEntry<?>> recipes) {
        List<RecipeEntry<?>> networkRecipes = new ArrayList<>(recipes.size());
        for (RecipeEntry<?> entry : recipes) {
            CraftingRecipe recipe = unwrap(entry.value());
            if (recipe != null) {
                networkRecipes.add(rewrap(entry, recipe));
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
    private static RecipeEntry<?> rewrap(RecipeEntry<?> entry, CraftingRecipe recipe) {
        return new RecipeEntry(entry.id(), recipe);
    }
}
