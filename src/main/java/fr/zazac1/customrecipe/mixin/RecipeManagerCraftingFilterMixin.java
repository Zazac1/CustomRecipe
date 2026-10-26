package fr.zazac1.customrecipe.mixin;

import com.mojang.datafixers.util.Pair;
import fr.zazac1.customrecipe.*;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerCraftingFilterMixin {
    @Shadow public abstract <C extends IInventory, T extends IRecipe<C>> List<T> getAllRecipesFor(IRecipeType<T> type);
    @Inject(method = "getRecipeFor(Lnet/minecraft/item/crafting/IRecipeType;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/world/World;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <C extends IInventory, T extends IRecipe<C>> void filterFirst(IRecipeType<T> type, C input, World world, CallbackInfoReturnable<Optional<T>> cir) {
        ModConfig root = ConfigLoader.get();
        WorldRecipeConfig worldConfig = ConfigLoader.activeWorldConfig(root);
        List<String> disabledRecipes = ConfigLoader.effectiveDisabledRecipes(root, worldConfig);
        List<RecipeVariantRule> disabledVariants = ConfigLoader.effectiveDisabledRecipeVariants(root, worldConfig);
        if (disabledRecipes.isEmpty() && disabledVariants.isEmpty()) return;
        for (T r : getAllRecipesFor(type)) if (!blocked(r, input, disabledRecipes, disabledVariants) && r.matches(input, world)) { cir.setReturnValue(Optional.of(r)); return; } cir.setReturnValue(Optional.empty());
    }
    @Inject(method = "getRecipesFor(Lnet/minecraft/item/crafting/IRecipeType;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/world/World;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private <C extends IInventory, T extends IRecipe<C>> void filterAll(IRecipeType<T> type, C input, World world, CallbackInfoReturnable<List<T>> cir) {
        ModConfig root = ConfigLoader.get();
        WorldRecipeConfig worldConfig = ConfigLoader.activeWorldConfig(root);
        List<String> disabledRecipes = ConfigLoader.effectiveDisabledRecipes(root, worldConfig);
        List<RecipeVariantRule> disabledVariants = ConfigLoader.effectiveDisabledRecipeVariants(root, worldConfig);
        if (!disabledRecipes.isEmpty() || !disabledVariants.isEmpty()) cir.setReturnValue(cir.getReturnValue().stream().filter(r -> !blocked(r, input, disabledRecipes, disabledVariants)).collect(java.util.stream.Collectors.toList()));
    }
    private static boolean blocked(IRecipe<?> r, IInventory input, List<String> disabledRecipes,
                                   List<RecipeVariantRule> disabledVariants) {
        if (disabledRecipes.contains(r.getId().toString())) return true;
        if (!(r instanceof ICraftingRecipe)) return false;
        for (RecipeVariantRule rule : disabledVariants) if (r.getId().toString().equals(rule.recipe_id)) for (int i=0;i<input.getContainerSize();i++) { ItemStack s=input.getItem(i); if(!s.isEmpty() && Registry.ITEM.getKey(s.getItem()).toString().equals(rule.material_id)) return true; }
        return false;
    }
}
