package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
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
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// In MC 1.21.11 the shaped-recipe pattern class is RawShapedRecipe (not ShapedRecipePattern)

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

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

        // 1b. Non-crafting recipes can be removed. Crafting recipes stay in the manager
        // so the server browser can still show and re-enable them after a restart.
        if (!config.disabled_recipes.isEmpty()) {
            recipes.removeIf(entry -> config.disabled_recipes.contains(entry.id().getValue().toString())
                    && !(entry.value() instanceof CraftingRecipe));
        }

        // 1c. Keep recipes available, but make selected material variants fail to match.
        if (!config.disabled_recipe_variants.isEmpty()) {
            Map<String, Set<String>> variantsByRecipe = new HashMap<>();
            for (RecipeVariantRule rule : config.disabled_recipe_variants) {
                variantsByRecipe.computeIfAbsent(rule.recipe_id, ignored -> new HashSet<>()).add(rule.material_id);
            }
            for (int i = 0; i < recipes.size(); i++) {
                RecipeEntry<?> entry = recipes.get(i);
                if (config.disabled_recipes.contains(entry.id().getValue().toString())
                        && entry.value() instanceof CraftingRecipe recipe) {
                    recipes.set(i, new RecipeEntry<>(entry.id(), new DisabledCraftingRecipe(recipe)));
                    continue;
                }
                Set<String> blocked = variantsByRecipe.get(entry.id().getValue().toString());
                if (blocked != null && entry.value() instanceof CraftingRecipe recipe) {
                    recipes.set(i, new RecipeEntry<>(entry.id(), new VariantFilteredCraftingRecipe(recipe, blocked)));
                }
            }
        } else if (!config.disabled_recipes.isEmpty()) {
            for (int i = 0; i < recipes.size(); i++) {
                RecipeEntry<?> entry = recipes.get(i);
                if (config.disabled_recipes.contains(entry.id().getValue().toString())
                        && entry.value() instanceof CraftingRecipe recipe) {
                    recipes.set(i, new RecipeEntry<>(entry.id(), new DisabledCraftingRecipe(recipe)));
                }
            }
        }

        // 2. Inject user custom recipes
        int idx = 0;
        List<RecipeEntry<?>> customRecipes = new ArrayList<>();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            // Local ModMenu recipes are drafts until an OP explicitly adds them
            // to the server. Null preserves recipes from configurations made
            // before the server publication state existed.
            boolean dedicatedServer = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
            if (Boolean.FALSE.equals(entry.enabled)
                    || (dedicatedServer && Boolean.FALSE.equals(entry.server_enabled))) {
                idx++;
                continue;
            }
            RecipeEntry<?> built = buildCustomRecipe(entry, idx++, recipeBookGroup(entry));
            if (built != null) customRecipes.add(built);
        }

        // The recipe manager uses the first matching entry. Vanilla entries
        // stay first, so a custom recipe only takes effect after the matching
        // vanilla recipe has been disabled. Custom recipes still share groups.
        recipes.addAll(customRecipes);

        return PreparedRecipes.of(recipes);
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    private RecipeEntry<?> buildCustomRecipe(CustomRecipeEntry entry, int idx, String recipeGroup) {
        if (entry == null) return null;
        if (entry.result == null || entry.result.isBlank()) return null;

        Identifier resultId = Identifier.tryParse(entry.result);
        if (resultId == null || !Registries.ITEM.containsId(resultId)) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", entry.result);
            return null;
        }

        ItemStack result = new ItemStack(Registries.ITEM.get(resultId), Math.max(1, entry.count));

        RegistryKey<Recipe<?>> key = RegistryKey.of(
                RegistryKeys.RECIPE,
                entry.serverRecipeId()
        );

        if ("shaped".equalsIgnoreCase(entry.type)) {
            return buildShaped(entry, result, key, recipeGroup);
        } else {
            return buildShapeless(entry, result, key, recipeGroup);
        }
    }

    /** Gives recipes with the same custom input one green-book entry. */
    private String recipeBookGroup(CustomRecipeEntry entry) {
        return "customrecipe_" + Integer.toUnsignedString(recipeInputSignature(entry).hashCode(), 36);
    }

    private String recipeInputSignature(CustomRecipeEntry entry) {
        if ("shaped".equalsIgnoreCase(entry.type)) {
            return "shaped:" + entry.pattern + ":" + entry.keys;
        }
        List<String> ingredients = entry.ingredients == null ? List.of() : new ArrayList<>(entry.ingredients);
        java.util.Collections.sort(ingredients);
        return "shapeless:" + ingredients;
    }

    // ── shapeless ─────────────────────────────────────────────────────────

    private RecipeEntry<ShapelessRecipe> buildShapeless(CustomRecipeEntry entry, ItemStack result,
                                                         RegistryKey<Recipe<?>> key, String recipeGroup) {
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
                recipeGroup,
                CraftingRecipeCategory.MISC,
                result,
                ingredients
        );
        return new RecipeEntry<>(key, recipe);
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeEntry<ShapedRecipe> buildShaped(CustomRecipeEntry entry, ItemStack result,
                                                    RegistryKey<Recipe<?>> key, String recipeGroup) {
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
                recipeGroup,
                CraftingRecipeCategory.MISC,
                rawRecipe,
                result
        );
        return new RecipeEntry<>(key, recipe);
    }
}
