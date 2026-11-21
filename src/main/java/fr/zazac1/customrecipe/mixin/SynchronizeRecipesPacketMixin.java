package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.network.play.server.SUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sends only vanilla-serializable recipes to clients that do not run Custom Recipe. */
@Mixin(SUpdateRecipesPacket.class)
public abstract class SynchronizeRecipesPacketMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Collection<IRecipe<?>> customrecipe$networkSafeRecipes(Collection<IRecipe<?>> recipes) {
        List<IRecipe<?>> networkRecipes = new ArrayList<IRecipe<?>>(recipes.size());
        for (IRecipe<?> candidate : recipes) {
            IRecipe<?> recipe = candidate;
            boolean disabled = false;
            while (true) {
                if (recipe instanceof DisabledCraftingRecipe) {
                    disabled = true;
                    break;
                }
                if (recipe instanceof CustomItemCraftingRecipe) {
                    recipe = ((CustomItemCraftingRecipe) recipe).delegate();
                    continue;
                }
                if (recipe instanceof VariantFilteredCraftingRecipe) {
                    recipe = ((VariantFilteredCraftingRecipe) recipe).delegate();
                    continue;
                }
                break;
            }
            if (!disabled) networkRecipes.add(recipe);
        }
        return networkRecipes;
    }
}
