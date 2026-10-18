package fr.zazac1.customrecipe.access;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Full stacks seen while Minecraft builds recipe-book inventory contents. */
public interface ExactStackedItemContentsAccess {
    List<ItemStack> customrecipe$exactStacks();
}
