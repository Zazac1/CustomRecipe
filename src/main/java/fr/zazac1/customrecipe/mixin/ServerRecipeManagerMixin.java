package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.BuiltinRecipeIds;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import net.minecraft.core.NonNullList;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

@Mixin(value = RecipeManager.class, remap = false)
public abstract class ServerRecipeManagerMixin {
    @Shadow(remap = false) @Final private net.minecraft.core.HolderLookup.Provider registries;

    @ModifyVariable(
            method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false
    )
    private RecipeMap customrecipe$applyConfig(RecipeMap original) {
        ConfigLoader.invalidate();
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);

        List<RecipeHolder<?>> recipes = new ArrayList<>(original.values());
        List<RecipeHolder<?>> priorityCustomRecipes = new ArrayList<>();
        List<RecipeHolder<?>> ordinaryCustomRecipes = new ArrayList<>();

        // Bundled recipes are templates until this world explicitly initializes them.
        if (!config.builtin_recipes_initialized || !config.disabled_builtin.isEmpty()) {
            recipes.removeIf(entry -> {
                Identifier id = entry.id().identifier();
                if (!id.getNamespace().equals(CustomRecipeMod.MOD_ID)
                        || !BuiltinRecipeIds.contains(id.getPath())) return false;
                return !config.builtin_recipes_initialized || config.disabled_builtin.contains(id.getPath());
            });
        }

        // Keep disabled crafting recipes discoverable by management screens,
        // but encode their disabled state in the recipe itself.
        if (!config.disabled_recipes.isEmpty()) {
            recipes.removeIf(entry -> config.disabled_recipes.contains(entry.id().identifier().toString())
                    && !(entry.value() instanceof CraftingRecipe));
        }
        Map<String, Set<String>> variantsByRecipe = new HashMap<>();
        for (RecipeVariantRule rule : config.disabled_recipe_variants) {
            if (rule == null || rule.recipe_id == null || rule.material_id == null) continue;
            variantsByRecipe.computeIfAbsent(rule.recipe_id, key -> new HashSet<>()).add(rule.material_id);
        }
        for (int i = 0; i < recipes.size(); i++) {
            RecipeHolder<?> holder = recipes.get(i);
            if (!(holder.value() instanceof CraftingRecipe crafting)) continue;
            String recipeId = holder.id().identifier().toString();
            if (config.disabled_recipes.contains(recipeId)) {
                recipes.set(i, new RecipeHolder<>(holder.id(), new DisabledCraftingRecipe(crafting)));
                continue;
            }
            Set<String> blockedMaterials = variantsByRecipe.get(recipeId);
            if (blockedMaterials != null && !blockedMaterials.isEmpty()) {
                recipes.set(i, new RecipeHolder<>(holder.id(),
                        new VariantFilteredCraftingRecipe(crafting, blockedMaterials)));
            }
        }

        // 2. Inject user custom recipes
        int idx = 0;
        boolean recipeStateChanged = false;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (entry == null || Boolean.FALSE.equals(entry.enabled) || Boolean.FALSE.equals(entry.server_enabled)) {
                idx++;
                continue;
            }
            recipeStateChanged |= fr.zazac1.customrecipe.RecipeIntegrity.refresh(entry, rootConfig.custom_items);
            RecipeHolder<?> built = Boolean.TRUE.equals(entry.corrupted) ? null
                    : buildCustomRecipe(entry, idx, recipeBookGroup(entry), rootConfig.custom_items);
            if (Boolean.TRUE.equals(entry.corrupted)) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Disabled corrupted recipe {}: missing {}. Reinstall required mods {} or delete the recipe.",
                        entry.id, entry.missing_items, entry.required_mods);
                idx++;
                continue;
            }
            idx++;
            if (built != null) {
                if (usesExactOrCountedIngredient(entry)) priorityCustomRecipes.add(built);
                else ordinaryCustomRecipes.add(built);
            }
        }
        if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());

        recipes.addAll(0, priorityCustomRecipes);
        recipes.addAll(ordinaryCustomRecipes);
        return RecipeMap.create(recipes);
    }

    // ── dispatch ─────────────────────────────────────────────────────────

    private boolean usesExactOrCountedIngredient(CustomRecipeEntry entry) {
        if (entry == null) return false;
        if (entry.ingredients != null && entry.ingredients.stream().anyMatch(CustomItemLibrary::isReference)) return true;
        if (entry.keys != null && entry.keys.values().stream().anyMatch(CustomItemLibrary::isReference)) return true;
        return entry.hasCountedIngredients();
    }

    private RecipeHolder<?> buildCustomRecipe(CustomRecipeEntry entry, int idx, String recipeGroup,
                                               List<CustomItemEntry> customItems) {
        if (entry == null) return null;
        if (entry.result == null || entry.result.isBlank()) return null;

        ItemStack result = resolveResult(entry.result, customItems);
        if (result.isEmpty()) return null;
        result = result.copyWithCount(Math.max(1, Math.min(64, entry.count)));

        Identifier key = entry.serverRecipeId();

        if ("shaped".equalsIgnoreCase(entry.type)) {
            return buildShaped(entry, result, key, recipeGroup, customItems);
        } else {
            return buildShapeless(entry, result, key, recipeGroup, customItems);
        }
    }

    private ItemStack resolveResult(String token, List<CustomItemEntry> customItems) {
        CustomItemEntry custom = CustomItemLibrary.find(customItems, token);
        if (custom != null) return CustomItemLibrary.decode(custom, registries);
        Identifier id = Identifier.tryParse(token);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", token);
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
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
                int count = entry.ingredient_counts != null && i < entry.ingredient_counts.size()
                        ? entry.ingredient_counts.get(i) : 1;
                ingredients.add(entry.ingredients.get(i) + "×" + Math.max(1, Math.min(64, count)));
            }
        }
        java.util.Collections.sort(ingredients);
        return "shapeless:" + ingredients;
    }

    // ── shapeless ─────────────────────────────────────────────────────────

    private RecipeHolder<CraftingRecipe> buildShapeless(CustomRecipeEntry entry, ItemStack result,
                                                         Identifier key, String recipeGroup,
                                                         List<CustomItemEntry> customItems) {
        List<String> rawIngredients = entry.ingredients;
        if (rawIngredients == null || rawIngredients.isEmpty()) return null;

        List<Ingredient> ingredients = new ArrayList<>();
        Map<String, ItemStack> customStacks = new LinkedHashMap<>();
        for (String itemId : rawIngredients) {
            if (itemId == null || itemId.isBlank()) continue;
            Ingredient ingredient = ingredientFor(itemId.trim(), customItems, customStacks);
            if (ingredient == null) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shapeless ingredient not found: {}", itemId);
                return null;
            }
            ingredients.add(ingredient);
        }
        if (ingredients.isEmpty()) return null;

        CraftingRecipe recipe = new ShapelessRecipe(
                recipeGroup,
                CraftingBookCategory.MISC,
                result,
                NonNullList.copyOf(ingredients)
        );
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, key), exactWrapper(recipe, entry, customStacks));
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeHolder<CraftingRecipe> buildShaped(CustomRecipeEntry entry, ItemStack result,
                                                      Identifier key, String recipeGroup,
                                                      List<CustomItemEntry> customItems) {
        List<String> pattern = CustomItemCraftingRecipe.shrinkPattern(entry.pattern);
        Map<String, String> keysMap = entry.keys;
        if (pattern == null || pattern.isEmpty()) return null;
        if (keysMap == null || keysMap.isEmpty()) return null;

        // Build symbol → Ingredient map
        Map<Character, Ingredient> symbols = new LinkedHashMap<>();
        Map<String, ItemStack> customStacks = new LinkedHashMap<>();
        for (Map.Entry<String, String> kv : keysMap.entrySet()) {
            if (kv.getKey() == null || kv.getKey().isEmpty()) continue;
            char sym = kv.getKey().charAt(0);
            Ingredient ingredient = ingredientFor(kv.getValue(), customItems, customStacks);
            if (ingredient == null) {
                CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shaped key item not found: {}", kv.getValue());
                return null;
            }
            symbols.put(sym, ingredient);
        }

        ShapedRecipePattern rawRecipe;
        try {
            rawRecipe = ShapedRecipePattern.of(symbols, pattern);
        } catch (Exception e) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Invalid shaped pattern for result: {} — {}", entry.result, e.getMessage());
            return null;
        }
        if (rawRecipe == null) return null;

        CraftingRecipe recipe = new ShapedRecipe(
                recipeGroup,
                CraftingBookCategory.MISC,
                rawRecipe,
                result
        );
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, key), exactWrapper(recipe, entry, customStacks));
    }

    private Ingredient ingredientFor(String token, List<CustomItemEntry> customItems,
                                     Map<String, ItemStack> customStacks) {
        CustomItemEntry custom = CustomItemLibrary.find(customItems, token);
        if (custom != null) {
            ItemStack stack = CustomItemLibrary.decode(custom, registries);
            if (stack.isEmpty()) return null;
            customStacks.put(CustomItemLibrary.reference(custom.id), stack.copyWithCount(1));
            return Ingredient.of(stack.getItem());
        }
        Identifier id = Identifier.tryParse(token);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? Ingredient.of(BuiltInRegistries.ITEM.getValue(id)) : null;
    }

    private CraftingRecipe exactWrapper(CraftingRecipe recipe, CustomRecipeEntry entry,
                                        Map<String, ItemStack> customStacks) {
        return usesExactOrCountedIngredient(entry)
                ? new CustomItemCraftingRecipe(recipe, entry, customStacks) : recipe;
    }
}
