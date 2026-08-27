package fr.isaac.customrecipe.mixin;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.CustomRecipeEntry;
import fr.isaac.customrecipe.CustomRecipeMod;
import fr.isaac.customrecipe.DisabledCraftingRecipe;
import fr.isaac.customrecipe.ModConfig;
import fr.isaac.customrecipe.RecipeVariantRule;
import fr.isaac.customrecipe.VariantFilteredCraftingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
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

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {

    @ModifyVariable(
            method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private RecipeMap customrecipe$applyConfig(RecipeMap original) {
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();

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
        List<RecipeHolder<?>> customRecipes = new ArrayList<>();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            // Local ModMenu recipes are drafts until an OP explicitly adds them
            // to the server. Null preserves recipes from configurations made
            // before the server publication state existed.
            // The integrated server shares the local ModMenu configuration.
            // A local recipe is deliberately a draft on dedicated servers, but
            // it must still work in a singleplayer world.
            boolean dedicatedServer = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
            if (Boolean.FALSE.equals(entry.enabled)
                    || (dedicatedServer && Boolean.FALSE.equals(entry.server_enabled))) {
                idx++;
                continue;
            }
            RecipeHolder<?> built = buildCustomRecipe(entry, idx++, recipeBookGroup(entry));
            if (built != null) customRecipes.add(built);
        }

        // RecipeManager normally returns the first matching recipe.  Put user
        // recipes first so a custom recipe is not silently hidden by vanilla.
        // Their recipe-book group still lets the player pick any alternative.
        recipes.addAll(0, customRecipes);

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

        // Datapack reload happens before 26.2 binds item data components.  A
        // template deliberately stores only the item holder/count and creates
        // the real ItemStack after registries are ready.
        ItemStackTemplate result = new ItemStackTemplate(
                BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.getValue(resultId)),
                Math.max(1, entry.count), DataComponentPatch.EMPTY);

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

    /**
     * Recipes with the same custom grid must be selectable from one green-book
     * entry. This is calculated from JSON only: at datapack load time 26.2 item
     * components are deliberately not bound yet, so constructing ItemStacks here
     * would prevent a dedicated server from starting.
     */
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
