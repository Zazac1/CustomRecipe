package fr.zazac1.customrecipe;

import net.neoforged.fml.ModList;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates saved item IDs without deleting the recipe that references them. */
public final class RecipeIntegrity {
    private RecipeIntegrity() {}

    public static boolean refresh(CustomRecipeEntry recipe) {
        rememberRequiredMods(recipe);
        List<String> missing = missingItems(recipe);
        boolean corrupted = !missing.isEmpty();
        boolean changed = corrupted != Boolean.TRUE.equals(recipe.corrupted)
                || !missing.equals(recipe.missing_items);
        recipe.corrupted = corrupted ? Boolean.TRUE : null;
        recipe.missing_items = missing;
        return changed;
    }

    public static List<String> missingItems(CustomRecipeEntry recipe) {
        List<String> missing = new ArrayList<>();
        for (String rawId : referencedItemIds(recipe)) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
                missing.add(rawId);
            }
        }
        return missing;
    }

    /** Records non-vanilla mod IDs and their versions when the recipe is created or saved. */
    public static void rememberRequiredMods(CustomRecipeEntry recipe) {
        if (recipe.required_mods == null) recipe.required_mods = new LinkedHashMap<>();
        for (String rawId : referencedItemIds(recipe)) {
            ResourceLocation itemId = ResourceLocation.tryParse(rawId);
            if (itemId == null || "minecraft".equals(itemId.getNamespace())) continue;
            recipe.required_mods.computeIfAbsent(itemId.getNamespace(), RecipeIntegrity::installedVersion);
        }
    }

    public static Set<String> requiredModIds(CustomRecipeEntry recipe) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (recipe.required_mods != null) ids.addAll(recipe.required_mods.keySet());
        for (String rawId : referencedItemIds(recipe)) {
            ResourceLocation itemId = ResourceLocation.tryParse(rawId);
            if (itemId != null && !"minecraft".equals(itemId.getNamespace())) ids.add(itemId.getNamespace());
        }
        return ids;
    }

    private static String installedVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static List<String> referencedItemIds(CustomRecipeEntry recipe) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        add(ids, recipe.result);
        if (recipe.ingredients != null) for (String item : recipe.ingredients) add(ids, item);
        if (recipe.keys != null) for (String item : recipe.keys.values()) add(ids, item);
        return new ArrayList<>(ids);
    }

    private static void add(Set<String> ids, String id) {
        if (id != null && !id.isBlank()) ids.add(id.trim());
    }
}
