package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Resolves item components from the active client registry when a world is loaded. */
final class ClientItemStacks {
    private ClientItemStacks() {}

    static ItemStack fromItem(Item item) {
        if (item == null) return ItemStack.EMPTY;
        return fromId(Registries.ITEM.getId(item));
    }

    static ItemStack fromId(Identifier id) {
        if (id == null) return ItemStack.EMPTY;

        return Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    static ItemStack fromReference(String value, ModConfig config) {
        if (!CustomItemLibrary.isReference(value)) return fromId(Identifier.tryParse(value));
        CustomItemEntry entry = CustomItemLibrary.find(config == null ? null : config.custom_items, value);
        if (entry == null) return ItemStack.EMPTY;
        var connection = MinecraftClient.getInstance().getNetworkHandler();
        if (connection != null) {
            ItemStack decoded = CustomItemLibrary.decode(entry, connection.getRegistryManager());
            if (!decoded.isEmpty()) return decoded;
        }
        return fromId(Identifier.tryParse(entry.item_id));
    }
}
