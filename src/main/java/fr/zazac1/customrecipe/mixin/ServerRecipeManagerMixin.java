package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
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

@Mixin(ServerRecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Shadow @Final private RegistryWrapper.WrapperLookup registries;

    @ModifyVariable(
            method = "apply(Lnet/minecraft/recipe/PreparedRecipes;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private PreparedRecipes customrecipe$applyConfig(PreparedRecipes original) {
        ConfigLoader.invalidate();
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);

        List<RecipeEntry<?>> recipes = new ArrayList<>(original.recipes());

        // Bundled JSON files are templates only; Quick Add creates independent custom copies.
        recipes.removeIf(entry -> {
            Identifier id = entry.id().getValue();
            return id.getNamespace().equals(CustomRecipeMod.MOD_ID)
                    && fr.zazac1.customrecipe.BuiltinRecipeTemplates.contains(id.getPath());
        });

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
        boolean recipeStateChanged = false;
        List<RecipeEntry<?>> exactCustomRecipes = new ArrayList<>();
        List<RecipeEntry<?>> customRecipes = new ArrayList<>();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (entry == null || Boolean.FALSE.equals(entry.enabled) || Boolean.FALSE.equals(entry.server_enabled)) {
                idx++;
                continue;
            }
            recipeStateChanged |= fr.zazac1.customrecipe.RecipeIntegrity.refresh(entry, rootConfig.custom_items);
            RecipeEntry<?> built = Boolean.TRUE.equals(entry.corrupted) ? null
                    : buildCustomRecipe(entry, idx, recipeBookGroup(entry), rootConfig.custom_items);
            if (Boolean.TRUE.equals(entry.corrupted)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Disabled corrupted recipe {}: missing {}. Reinstall required mods {} or delete the recipe.",
                        entry.id, entry.missing_items, entry.required_mods);
                idx++;
                continue;
            }
            idx++;
            if (built != null) {
                if (usesExactOrCountedIngredient(entry)) exactCustomRecipes.add(built);
                else customRecipes.add(built);
            }
        }
        if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());

        // Exact full-stack ingredients are deliberately checked before generic
        // vanilla ingredients. This lets a component-rich fishing rod select its
        // custom recipe without changing how ordinary fishing rods craft.
        recipes.addAll(0, exactCustomRecipes);
        // Other custom recipes retain the established conflict behaviour.
        recipes.addAll(customRecipes);

        return PreparedRecipes.of(recipes);
    }

    private boolean usesExactOrCountedIngredient(CustomRecipeEntry entry) {
        if (entry.hasCountedIngredients()) return true;
        java.util.stream.Stream<String> values = "shaped".equalsIgnoreCase(entry.type)
                ? (entry.keys == null ? java.util.stream.Stream.empty() : entry.keys.values().stream())
                : (entry.ingredients == null ? java.util.stream.Stream.empty() : entry.ingredients.stream());
        return values.anyMatch(fr.zazac1.customrecipe.CustomItemLibrary::isReference);
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    private RecipeEntry<?> buildCustomRecipe(CustomRecipeEntry entry, int idx, String recipeGroup,
                                             List<fr.zazac1.customrecipe.CustomItemEntry> customItems) {
        if (entry == null) return null;
        if (entry.result == null || entry.result.isBlank()) return null;

        ItemStack result = resolveStack(entry.result, customItems);
        if (result.isEmpty()) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", entry.result);
            return null;
        }
        result = result.copyWithCount(Math.max(1, entry.count));

        RegistryKey<Recipe<?>> key = RegistryKey.of(
                RegistryKeys.RECIPE,
                entry.serverRecipeId()
        );

        if ("shaped".equalsIgnoreCase(entry.type)) {
            return buildShaped(entry, result, key, recipeGroup, customItems);
        } else {
            return buildShapeless(entry, result, key, recipeGroup, customItems);
        }
    }

    private ItemStack resolveStack(String value,
                                   List<fr.zazac1.customrecipe.CustomItemEntry> customItems) {
        if (fr.zazac1.customrecipe.CustomItemLibrary.isReference(value)) {
            return fr.zazac1.customrecipe.CustomItemLibrary.decode(
                    fr.zazac1.customrecipe.CustomItemLibrary.find(customItems, value), registries);
        }
        Identifier id = Identifier.tryParse(value);
        return id != null && Registries.ITEM.containsId(id)
                ? new ItemStack(Registries.ITEM.get(id))
                : ItemStack.EMPTY;
    }

    private String ingredientBaseId(String value, List<fr.zazac1.customrecipe.CustomItemEntry> customItems) {
        if (!fr.zazac1.customrecipe.CustomItemLibrary.isReference(value)) return value;
        return fr.zazac1.customrecipe.CustomItemLibrary.baseItemId(
                fr.zazac1.customrecipe.CustomItemLibrary.find(customItems, value));
    }

    private CraftingRecipe exactWrapper(CraftingRecipe recipe, CustomRecipeEntry entry,
                                        List<fr.zazac1.customrecipe.CustomItemEntry> customItems) {
        Map<String, ItemStack> stacks = new HashMap<>();
        java.util.stream.Stream<String> values = "shaped".equalsIgnoreCase(entry.type)
                ? (entry.keys == null ? java.util.stream.Stream.empty() : entry.keys.values().stream())
                : (entry.ingredients == null ? java.util.stream.Stream.empty() : entry.ingredients.stream());
        values.filter(fr.zazac1.customrecipe.CustomItemLibrary::isReference).distinct().forEach(reference -> {
            ItemStack stack = resolveStack(reference, customItems);
            if (!stack.isEmpty()) stacks.put(reference, stack.copyWithCount(1));
        });
        return stacks.isEmpty() && !entry.hasCountedIngredients()
                ? recipe : new fr.zazac1.customrecipe.CustomItemCraftingRecipe(recipe, entry, stacks);
    }


    /** Gives recipes with the same custom input one green-book entry. */
    private String recipeBookGroup(CustomRecipeEntry entry) {
        return "customrecipe_" + Integer.toUnsignedString(recipeInputSignature(entry).hashCode(), 36);
    }

    private String recipeInputSignature(CustomRecipeEntry entry) {
        if ("shaped".equalsIgnoreCase(entry.type)) {
            return "shaped:" + entry.pattern + ":" + entry.keys + ":" + entry.key_counts;
        }
        List<String> ingredients = new ArrayList<>();
        if (entry.ingredients != null) {
            for (int i = 0; i < entry.ingredients.size(); i++) {
                ingredients.add(entry.ingredients.get(i) + "×" + entry.ingredientCount(i));
            }
        }
        java.util.Collections.sort(ingredients);
        return "shapeless:" + ingredients;
    }

    // ── shapeless ─────────────────────────────────────────────────────────

    private RecipeEntry<?> buildShapeless(CustomRecipeEntry entry, ItemStack result,
                                           RegistryKey<Recipe<?>> key, String recipeGroup,
                                           List<fr.zazac1.customrecipe.CustomItemEntry> customItems) {
        List<String> rawIngredients = entry.ingredients;
        if (rawIngredients == null || rawIngredients.isEmpty()) return null;

        List<Ingredient> ingredients = new ArrayList<>();
        for (String itemId : rawIngredients) {
            if (itemId == null || itemId.isBlank()) continue;
            String baseId = ingredientBaseId(itemId.trim(), customItems);
            Identifier id = Identifier.tryParse(baseId);
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
        return new RecipeEntry<>(key, exactWrapper(recipe, entry, customItems));
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeEntry<?> buildShaped(CustomRecipeEntry entry, ItemStack result,
                                        RegistryKey<Recipe<?>> key, String recipeGroup,
                                        List<fr.zazac1.customrecipe.CustomItemEntry> customItems) {
        List<String> pattern = entry.pattern;
        Map<String, String> keysMap = entry.keys;
        if (pattern == null || pattern.isEmpty()) return null;
        if (keysMap == null || keysMap.isEmpty()) return null;

        // Build symbol → Ingredient map
        Map<Character, Ingredient> symbols = new LinkedHashMap<>();
        for (Map.Entry<String, String> kv : keysMap.entrySet()) {
            if (kv.getKey() == null || kv.getKey().isEmpty()) continue;
            char sym = kv.getKey().charAt(0);
            Identifier itemId = Identifier.tryParse(ingredientBaseId(kv.getValue(), customItems));
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
        return new RecipeEntry<>(key, exactWrapper(recipe, entry, customItems));
    }
}
