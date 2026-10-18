package fr.zazac1.customrecipe.access;

import net.minecraft.item.ItemStack;

import java.util.List;

/** Full stacks seen while Minecraft builds recipe-book inventory contents. */
public interface ExactRecipeFinderAccess {
    List<ItemStack> customrecipe$exactStacks();
}
