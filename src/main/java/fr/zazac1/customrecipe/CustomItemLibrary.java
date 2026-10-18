package fr.zazac1.customrecipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/** Shared identifiers and codec helpers for full-stack custom items. */
public final class CustomItemLibrary {
    public static final String REFERENCE_PREFIX = CustomRecipeMod.MOD_ID + ":custom_item/";

    private CustomItemLibrary() {}

    public static boolean isReference(String value) {
        return value != null && value.startsWith(REFERENCE_PREFIX);
    }

    public static String reference(String id) {
        return REFERENCE_PREFIX + sanitizeId(id);
    }

    public static String idFromReference(String reference) {
        return isReference(reference) ? reference.substring(REFERENCE_PREFIX.length()) : "";
    }

    public static CustomItemEntry find(List<CustomItemEntry> items, String referenceOrId) {
        if (items == null || referenceOrId == null) return null;
        String id = isReference(referenceOrId) ? idFromReference(referenceOrId) : sanitizeId(referenceOrId);
        for (CustomItemEntry entry : items) {
            if (entry != null && id.equals(entry.id)) return entry;
        }
        return null;
    }

    public static String uniqueId(List<CustomItemEntry> items, String requested) {
        String base = sanitizeId(requested);
        if (base.isBlank()) base = "custom_item";
        String candidate = base;
        int suffix = 2;
        while (find(items, candidate) != null) candidate = base + "_" + suffix++;
        return candidate;
    }

    public static String sanitizeId(String value) {
        if (value == null) return "";
        String id = value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        return id.length() > 64 ? id.substring(0, 64) : id;
    }

    public static String encode(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return "";
        JsonElement json = ItemStack.CODEC.encodeStart(
                        RegistryOps.create(JsonOps.INSTANCE, registries), stack.copyWithCount(1))
                .result().orElseThrow(() -> new IllegalArgumentException("Unable to encode ItemStack"));
        return json.toString();
    }

    public static ItemStack decode(CustomItemEntry entry, HolderLookup.Provider registries) {
        if (entry == null || entry.stack_data == null || entry.stack_data.isBlank()) return ItemStack.EMPTY;
        try {
            return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries),
                            JsonParser.parseString(entry.stack_data))
                    .result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static String baseItemId(CustomItemEntry entry) {
        if (entry == null || entry.item_id == null) return "";
        ResourceLocation id = ResourceLocation.tryParse(entry.item_id);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != Items.AIR ? id.toString() : "";
    }

    public static boolean exactMatch(ItemStack actual, ItemStack expected) {
        return actual != null && expected != null && !actual.isEmpty() && !expected.isEmpty()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }
}
