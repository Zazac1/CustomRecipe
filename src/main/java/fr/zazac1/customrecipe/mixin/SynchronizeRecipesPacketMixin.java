package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The 1.21.1 recipe packet casts each value to the concrete type owned by its
 * serializer. Server-only recipe decorators therefore have to be unwrapped
 * before synchronization; matching and counted consumption stay server-side.
 */
@Mixin(SynchronizeRecipesS2CPacket.class)
public abstract class SynchronizeRecipesPacketMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Collection<RecipeEntry<?>> customrecipe$networkSafeRecipes(Collection<RecipeEntry<?>> recipes) {
        List<RecipeEntry<?>> networkRecipes = new ArrayList<>(recipes.size());
        for (RecipeEntry<?> entry : recipes) {
            Recipe<?> recipe = entry.value();
            boolean disabled = false;
            while (true) {
                if (recipe instanceof DisabledCraftingRecipe) {
                    disabled = true;
                    break;
                }
                if (recipe instanceof CustomItemCraftingRecipe custom) {
                    recipe = custom.delegate();
                    continue;
                }
                if (recipe instanceof VariantFilteredCraftingRecipe filtered) {
                    recipe = filtered.delegate();
                    continue;
                }
                break;
            }
            if (!disabled) networkRecipes.add(new RecipeEntry<>(entry.id(), recipe));
        }
        return networkRecipes;
    }
}
