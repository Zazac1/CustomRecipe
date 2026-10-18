package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.access.ExactRecipeFinderAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Keeps component-rich stacks that vanilla excludes from recipe-book availability checks. */
@Mixin(RecipeFinder.class)
public abstract class RecipeFinderMixin implements ExactRecipeFinderAccess {
    @Unique private final List<ItemStack> customrecipe$exactStacks = new ArrayList<>();

    @Inject(method = "addInputIfUsable", at = @At("HEAD"))
    private void customrecipe$captureExactStack(ItemStack stack, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty()) customrecipe$exactStacks.add(stack.copy());
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void customrecipe$clearExactStacks(CallbackInfo ci) {
        customrecipe$exactStacks.clear();
    }

    @Override
    public List<ItemStack> customrecipe$exactStacks() {
        return customrecipe$exactStacks;
    }
}
