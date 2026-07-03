package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.CustomRecipeEntry;
import fr.isaac.customrecipe.CustomRecipeMod;
import fr.isaac.customrecipe.ModConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// In MC 1.21.11 the shaped-recipe pattern class is RawShapedRecipe (not ShapedRecipePattern)

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(ServerRecipeManager.class)
public abstract class ServerRecipeManagerMixin {

    @ModifyVariable(
            method = "apply(Lnet/minecraft/recipe/PreparedRecipes;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private PreparedRecipes customrecipe$applyConfig(PreparedRecipes original) {
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();

        List<RecipeEntry<?>> recipes = new ArrayList<>(original.recipes());

        // 1. Remove disabled built-in recipes (namespace = "customrecipe")
        if (!config.disabled_builtin.isEmpty()) {
            recipes.removeIf(entry -> {
                Identifier id = entry.id().getValue();
                if (!id.getNamespace().equals(CustomRecipeMod.MOD_ID)) return false;
                for (String disabled : config.disabled_builtin) {
                    if (id.getPath().equals(disabled)) return true;
                }
                return false;
            });
        }

        // 2. Inject user custom recipes
        int idx = 0;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (Boolean.FALSE.equals(entry.enabled)) { idx++; continue; } // skip disabled
            RecipeEntry<?> built = buildCustomRecipe(entry, idx++);
            if (built != null) recipes.add(built);
        }

        return PreparedRecipes.of(recipes);
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    private RecipeEntry<?> buildCustomRecipe(CustomRecipeEntry entry, int idx) {
        if (entry == null) return null;
        if (entry.result == null || entry.result.isBlank()) return null;

        Identifier resultId = Identifier.tryParse(entry.result);
        if (resultId == null || !Registries.ITEM.containsId(resultId)) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", entry.result);
            return null;
        }

        ItemStack result = new ItemStack(Registries.ITEM.get(resultId), Math.max(1, entry.count));

        // Stable recipe key: use result path + index
        String safeName = entry.result.replace(':', '_').replace('/', '_') + "_" + idx;
        RegistryKey<Recipe<?>> key = RegistryKey.of(
                RegistryKeys.RECIPE,
                Identifier.of(CustomRecipeMod.MOD_ID, "custom/" + safeName)
        );

        if ("shaped".equalsIgnoreCase(entry.type)) {
            return buildShaped(entry, result, key);
        } else {
            return buildShapeless(entry, result, key);
        }
    }

    // ── shapeless ─────────────────────────────────────────────────────────

    private RecipeEntry<ShapelessRecipe> buildShapeless(CustomRecipeEntry entry, ItemStack result,
                                                         RegistryKey<Recipe<?>> key) {
        List<String> rawIngredients = entry.ingredients;
        if (rawIngredients == null || rawIngredients.isEmpty()) return null;

        List<Ingredient> ingredients = new ArrayList<>();
        for (String itemId : rawIngredients) {
            if (itemId == null || itemId.isBlank()) continue;
            Identifier id = Identifier.tryParse(itemId.trim());
            if (id == null || !Registries.ITEM.containsId(id)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shapeless ingredient not found: {}", itemId);
                return null;
            }
            ingredients.add(Ingredient.ofItems(Registries.ITEM.get(id)));
        }
        if (ingredients.isEmpty()) return null;

        ShapelessRecipe recipe = new ShapelessRecipe(
                CustomRecipeMod.MOD_ID,
                CraftingRecipeCategory.MISC,
                result,
                ingredients
        );
        return new RecipeEntry<>(key, recipe);
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeEntry<ShapedRecipe> buildShaped(CustomRecipeEntry entry, ItemStack result,
                                                    RegistryKey<Recipe<?>> key) {
        List<String> pattern = entry.pattern;
        Map<String, String> keysMap = entry.keys;
        if (pattern == null || pattern.isEmpty()) return null;
        if (keysMap == null || keysMap.isEmpty()) return null;

        // Build symbol → Ingredient map
        Map<Character, Ingredient> symbols = new LinkedHashMap<>();
        for (Map.Entry<String, String> kv : keysMap.entrySet()) {
            if (kv.getKey() == null || kv.getKey().isEmpty()) continue;
            char sym = kv.getKey().charAt(0);
            Identifier itemId = Identifier.tryParse(kv.getValue());
            if (itemId == null || !Registries.ITEM.containsId(itemId)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shaped key item not found: {}", kv.getValue());
                return null;
            }
            symbols.put(sym, Ingredient.ofItems(Registries.ITEM.get(itemId)));
        }

        RawShapedRecipe rawRecipe;
        try {
            rawRecipe = RawShapedRecipe.create(symbols, pattern);
        } catch (Exception e) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Invalid shaped pattern for result: {} — {}", entry.result, e.getMessage());
            return null;
        }
        if (rawRecipe == null) return null;

        ShapedRecipe recipe = new ShapedRecipe(
                CustomRecipeMod.MOD_ID,
                CraftingRecipeCategory.MISC,
                rawRecipe,
                result
        );
        return new RecipeEntry<>(key, recipe);
    }
}
