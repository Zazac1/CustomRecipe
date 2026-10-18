package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves item components from the active client registry when a world is loaded. */
final class ClientItemStacks {
    private ClientItemStacks() {}

    static ItemStack fromItem(Item item) {
        if (item == null) return ItemStack.EMPTY;
        return fromId(BuiltInRegistries.ITEM.getKey(item));
    }

    static ItemStack fromId(ResourceLocation id) {
        if (id == null) return ItemStack.EMPTY;

        return BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    static ItemStack fromReference(String value, ModConfig config) {
        if (!CustomItemLibrary.isReference(value)) return fromId(ResourceLocation.tryParse(value));
        CustomItemEntry entry = CustomItemLibrary.find(config == null ? null : config.custom_items, value);
        if (entry == null) return ItemStack.EMPTY;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            ItemStack decoded = CustomItemLibrary.decode(entry, connection.registryAccess());
            if (!decoded.isEmpty()) return decoded;
        }
        return fromId(ResourceLocation.tryParse(entry.item_id));
    }
}
