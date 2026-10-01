package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.BuiltinRecipeIds;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

// In Minecraft 1.21.8 the shaped-recipe pattern class is RawShapedRecipe (not ShapedRecipePattern)

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

@Mixin(value = RecipeManager.class, remap = false)
public abstract class ServerRecipeManagerMixin {
    @Shadow(remap = false) public abstract Collection<RecipeHolder<?>> getRecipes();
    @Shadow(remap = false) private RecipeMap recipes;

    @Inject(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"), remap = false)
    private void customrecipe$applyConfig(RecipeMap ignored, ResourceManager resourceManager,
                                          ProfilerFiller profiler, CallbackInfo ci) {
        ConfigLoader.invalidate();
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);

        List<RecipeHolder<?>> recipes = new ArrayList<>(getRecipes());

        // 1. Bundled recipes are templates, not active data-pack recipes by
        // default. A world loads one only after it has selected built-ins in
        // the editor; disabled selections remain absent after that.
        if (!config.builtin_recipes_initialized || !config.disabled_builtin.isEmpty()) {
            recipes.removeIf(entry -> {
                Identifier id = entry.id().identifier();
                if (!id.getNamespace().equals(CustomRecipeMod.MOD_ID)
                        || !BuiltinRecipeIds.contains(id.getPath())) return false;
                return !config.builtin_recipes_initialized || config.disabled_builtin.contains(id.getPath());
            });
        }

        // 1b. Non-crafting recipes can be removed directly. Crafting recipes
        // remain discoverable by management screens but are wrapped so every
        // matching path, including cached menu checks, sees them as disabled.
        if (!config.disabled_recipes.isEmpty()) {
            recipes.removeIf(entry -> config.disabled_recipes.contains(entry.id().identifier().toString())
                    && !(entry.value() instanceof CraftingRecipe));
        }

        // 1c. RecipeManager has more than one crafting lookup path in 1.21.11.
        // Filtering its public overloads alone misses callers that invoke the
        // recipe directly, so encode the disabled state in the crafting recipe
        // itself. This preserves the recipe ID for the editor and recipe book.
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
                recipes.set(i, new RecipeHolder<>(holder.id(), new VariantFilteredCraftingRecipe(crafting, blockedMaterials)));
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
            if (built != null) recipes.add(built);
        }
        if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());

        // The recipe manager uses the first matching entry. Vanilla entries
        // stay first, so a custom recipe only takes effect after the matching
        // vanilla recipe has been disabled. Custom recipes still share groups.
        this.recipes = RecipeMap.create(recipes);
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

        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(resultId), Math.max(1, entry.count));

        Identifier key = entry.serverRecipeId();

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

    private RecipeHolder<ShapelessRecipe> buildShapeless(CustomRecipeEntry entry, ItemStack result,
                                                         Identifier key, String recipeGroup) {
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
                recipeGroup,
                CraftingBookCategory.MISC,
                result,
                NonNullList.copyOf(ingredients)
        );
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, key), recipe);
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeHolder<ShapedRecipe> buildShaped(CustomRecipeEntry entry, ItemStack result,
                                                    Identifier key, String recipeGroup) {
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
                recipeGroup,
                CraftingBookCategory.MISC,
                rawRecipe,
                result
        );
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, key), recipe);
    }
}
