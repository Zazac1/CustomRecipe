package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sends only vanilla-serializable recipes to clients that do not run Custom Recipe. */
@Mixin(ClientboundUpdateRecipesPacket.class)
public abstract class SynchronizeRecipesPacketMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Collection<Recipe<?>> customrecipe$networkSafeRecipes(Collection<Recipe<?>> recipes) {
        List<Recipe<?>> networkRecipes = new ArrayList<>(recipes.size());
        for (Recipe<?> candidate : recipes) {
            Recipe<?> recipe = candidate;
            boolean disabled = false;
            while (true) {
                if (recipe instanceof DisabledCraftingRecipe) { disabled = true; break; }
                if (recipe instanceof CustomItemCraftingRecipe custom) { recipe = custom.delegate(); continue; }
                if (recipe instanceof VariantFilteredCraftingRecipe filtered) { recipe = filtered.delegate(); continue; }
                break;
            }
            if (!disabled) networkRecipes.add(recipe);
        }
        return networkRecipes;
    }
}
