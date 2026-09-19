package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// In Minecraft 1.21.8 the shaped-recipe pattern class is RawShapedRecipe (not ShapedRecipePattern)

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {

    @ModifyVariable(
            method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private RecipeMap customrecipe$applyConfig(RecipeMap original) {
        ConfigLoader.invalidate();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig();

        List<RecipeHolder<?>> recipes = new ArrayList<>(original.values());

        // 1. Remove disabled built-in recipes (namespace = "customrecipe")
        if (!config.disabled_builtin.isEmpty()) {
            recipes.removeIf(entry -> {
                Identifier id = entry.id().identifier();
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
            recipes.removeIf(entry -> config.disabled_recipes.contains(entry.id().identifier().toString())
                    && !(entry.value() instanceof CraftingRecipe));
        }

        // 1c. Keep recipes available, but make selected material variants fail to match.
        if (!config.disabled_recipe_variants.isEmpty()) {
            Map<String, Set<String>> variantsByRecipe = new HashMap<>();
            for (RecipeVariantRule rule : config.disabled_recipe_variants) {
                variantsByRecipe.computeIfAbsent(rule.recipe_id, ignored -> new HashSet<>()).add(rule.material_id);
            }
            for (int i = 0; i < recipes.size(); i++) {
                RecipeHolder<?> entry = recipes.get(i);
                if (config.disabled_recipes.contains(entry.id().identifier().toString())
                        && entry.value() instanceof CraftingRecipe recipe) {
                    recipes.set(i, new RecipeHolder<>(entry.id(), new DisabledCraftingRecipe(recipe)));
                    continue;
                }
                Set<String> blocked = variantsByRecipe.get(entry.id().identifier().toString());
                if (blocked != null && entry.value() instanceof CraftingRecipe recipe) {
                    recipes.set(i, new RecipeHolder<>(entry.id(), new VariantFilteredCraftingRecipe(recipe, blocked)));
                }
            }
        } else if (!config.disabled_recipes.isEmpty()) {
            for (int i = 0; i < recipes.size(); i++) {
                RecipeHolder<?> entry = recipes.get(i);
                if (config.disabled_recipes.contains(entry.id().identifier().toString())
                        && entry.value() instanceof CraftingRecipe recipe) {
                    recipes.set(i, new RecipeHolder<>(entry.id(), new DisabledCraftingRecipe(recipe)));
                }
            }
        }

        // 2. Inject user custom recipes
        int idx = 0;
        boolean recipeStateChanged = false;
        List<RecipeHolder<?>> customRecipes = new ArrayList<>();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (entry == null || Boolean.FALSE.equals(entry.enabled) || Boolean.FALSE.equals(entry.server_enabled)) {
                idx++;
                continue;
            }
            recipeStateChanged |= fr.zazac1.customrecipe.RecipeIntegrity.refresh(entry);
            RecipeHolder<?> built = Boolean.TRUE.equals(entry.corrupted) ? null
                    : buildCustomRecipe(entry, idx, recipeBookGroup(entry));
            if (Boolean.TRUE.equals(entry.corrupted)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Disabled corrupted recipe {}: missing {}. Reinstall required mods {} or delete the recipe.",
                        entry.id, entry.missing_items, entry.required_mods);
                idx++;
                continue;
            }
            idx++;
            if (built != null) customRecipes.add(built);
        }
        if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());

        // The recipe manager uses the first matching entry. Vanilla entries
        // stay first, so a custom recipe only takes effect after the matching
        // vanilla recipe has been disabled. Custom recipes still share groups.
        recipes.addAll(customRecipes);

        return RecipeMap.create(recipes);
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    private RecipeHolder<?> buildCustomRecipe(CustomRecipeEntry entry, int idx, String recipeGroup) {
        if (entry == null) return null;
        if (entry.result == null || entry.result.isBlank()) return null;

        Identifier resultId = Identifier.tryParse(entry.result);
        if (resultId == null || !BuiltInRegistries.ITEM.containsKey(resultId)) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", entry.result);
            return null;
        }

        ItemStackTemplate result = new ItemStackTemplate(BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.getValue(resultId)), Math.max(1, entry.count), DataComponentPatch.EMPTY);

        ResourceKey<Recipe<?>> key = ResourceKey.create(
                Registries.RECIPE,
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

    private RecipeHolder<ShapelessRecipe> buildShapeless(CustomRecipeEntry entry, ItemStackTemplate result,
                                                         ResourceKey<Recipe<?>> key, String recipeGroup) {
        List<String> rawIngredients = entry.ingredients;
        if (rawIngredients == null || rawIngredients.isEmpty()) return null;

        List<Ingredient> ingredients = new ArrayList<>();
        for (String itemId : rawIngredients) {
            if (itemId == null || itemId.isBlank()) continue;
            Identifier id = Identifier.tryParse(itemId.trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shapeless ingredient not found: {}", itemId);
                return null;
            }
            ingredients.add(Ingredient.of(BuiltInRegistries.ITEM.getValue(id)));
        }
        if (ingredients.isEmpty()) return null;

        ShapelessRecipe recipe = new ShapelessRecipe(
                new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, recipeGroup),
                result,
                ingredients
        );
        return new RecipeHolder<>(key, recipe);
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeHolder<ShapedRecipe> buildShaped(CustomRecipeEntry entry, ItemStackTemplate result,
                                                    ResourceKey<Recipe<?>> key, String recipeGroup) {
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
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shaped key item not found: {}", kv.getValue());
                return null;
            }
            symbols.put(sym, Ingredient.of(BuiltInRegistries.ITEM.getValue(itemId)));
        }

        ShapedRecipePattern rawRecipe;
        try {
            rawRecipe = ShapedRecipePattern.of(symbols, pattern);
        } catch (Exception e) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Invalid shaped pattern for result: {} — {}", entry.result, e.getMessage());
            return null;
        }
        if (rawRecipe == null) return null;

        ShapedRecipe recipe = new ShapedRecipe(
                new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, recipeGroup),
                rawRecipe,
                result
        );
        return new RecipeHolder<>(key, recipe);
    }
}
