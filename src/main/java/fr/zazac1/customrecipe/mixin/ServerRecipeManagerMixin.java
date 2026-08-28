package fr.zazac1.customrecipe.mixin;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonElement;
import java.util.Collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies this mod's persistent recipe configuration after 1.21.1 has loaded datapack recipes. */
@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {

    @Shadow public abstract Collection<RecipeEntry<?>> values();
    @Shadow public abstract void setRecipes(Iterable<RecipeEntry<?>> recipes);

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("TAIL")
    )
    private void customrecipe$applyConfig(Map<Identifier, JsonElement> ignored, ResourceManager resourceManager,
                                          Profiler profiler, CallbackInfo ci) {
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();

        List<RecipeEntry<?>> recipes = new ArrayList<>(values());

        // 1. Remove disabled built-in recipes (namespace = "customrecipe")
        if (!config.disabled_builtin.isEmpty()) {
            recipes.removeIf(entry -> {
                Identifier id = entry.id();
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
            recipes.removeIf(entry -> config.disabled_recipes.contains(entry.id().toString())
                    && !(entry.value() instanceof CraftingRecipe));
        }

        // 1c. Crafting recipes must stay as their native Minecraft classes so their
        // packet codecs can synchronize them to clients. Their disabled state is
        // enforced by RecipeManagerCraftingFilterMixin when crafting is attempted.

        // 2. Inject user custom recipes
        int idx = 0;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            // Local ModMenu recipes are drafts until an OP explicitly adds them
            // to the server. Null preserves recipes from configurations made
            // before the server publication state existed.
            boolean dedicatedServer = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
            if ((dedicatedServer && Boolean.FALSE.equals(entry.server_enabled)) || Boolean.FALSE.equals(entry.enabled)) {
                idx++;
                continue;
            }
            RecipeEntry<?> built = buildCustomRecipe(entry, idx++, recipeBookGroup(entry));
            if (built != null) recipes.add(built);
        }

        setRecipes(recipes);
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

        Identifier key = entry.serverRecipeId();

        if ("shaped".equalsIgnoreCase(entry.type)) {
            return buildShaped(entry, result, key, recipeGroup);
        } else {
            return buildShapeless(entry, result, key, recipeGroup);
        }
    }

    // ── shapeless ─────────────────────────────────────────────────────────

    private RecipeEntry<ShapelessRecipe> buildShapeless(CustomRecipeEntry entry, ItemStack result,
                                                         Identifier key, String recipeGroup) {
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
                DefaultedList.copyOf(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new))
        );
        return new RecipeEntry<>(key, recipe);
    }

    // ── shaped ────────────────────────────────────────────────────────────

    private RecipeEntry<ShapedRecipe> buildShaped(CustomRecipeEntry entry, ItemStack result,
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

    private String recipeBookGroup(CustomRecipeEntry entry) {
        String signature;
        if ("shaped".equalsIgnoreCase(entry.type)) {
            signature = "shaped:" + String.join("/", entry.pattern == null ? List.of() : entry.pattern)
                    + ":" + (entry.keys == null ? Map.of() : new java.util.TreeMap<>(entry.keys));
        } else {
            List<String> ingredients = new ArrayList<>(entry.ingredients == null ? List.of() : entry.ingredients);
            ingredients.sort(String::compareTo);
            signature = "shapeless:" + String.join(",", ingredients);
        }
        return "customrecipe_" + Integer.toUnsignedString(signature.hashCode(), 36);
    }
}
