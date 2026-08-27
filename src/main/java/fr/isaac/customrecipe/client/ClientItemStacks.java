package fr.isaac.customrecipe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves item components from the active client registry when a world is loaded. */
final class ClientItemStacks {
    private ClientItemStacks() {}

    static ItemStack fromItem(Item item) {
        if (item == null) return ItemStack.EMPTY;
        return fromId(BuiltInRegistries.ITEM.getKey(item));
    }

    static ItemStack fromId(Identifier id) {
        if (id == null) return ItemStack.EMPTY;

        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            ItemStack fromServerRegistry = connection.registryAccess()
                    .lookup(Registries.ITEM)
                    .flatMap(registry -> registry.get(id))
                    .map(ItemStack::new)
                    .orElse(ItemStack.EMPTY);
            if (!fromServerRegistry.isEmpty()) return fromServerRegistry;
        }

        return BuiltInRegistries.ITEM.get(id).map(holder -> {
            // At the title screen 26.2 has not loaded datapack-driven components yet.
            // Bind the same base model and name selected by Item.Properties so its icon
            // remains visible. A joined world later replaces this temporary map with
            // the complete datapack-driven component map from its registry.
            if (!holder.areComponentsBound()) {
                holder.bindComponents(DataComponentMap.builder()
                        .set(DataComponents.ITEM_MODEL, id)
                        .set(DataComponents.ITEM_NAME, Component.translatable(holder.value().getDescriptionId()))
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .build());
            }
            return new ItemStack(holder);
        }).orElse(ItemStack.EMPTY);
    }
}
