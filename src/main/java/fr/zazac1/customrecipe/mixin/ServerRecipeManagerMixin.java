package fr.zazac1.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.zazac1.customrecipe.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
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

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Shadow public abstract Collection<Recipe<?>> values();
    @Shadow public abstract void setRecipes(Iterable<Recipe<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
    private void customrecipe$applyConfig(Map<Identifier, JsonElement> ignored, ResourceManager manager,
                                          Profiler profiler, CallbackInfo ci) {
        ConfigLoader.invalidate();
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
        List<Recipe<?>> recipes = new ArrayList<>(values());
        // Bundled JSON files are templates only; Quick Add creates independent custom copies.
        recipes.removeIf(recipe -> recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)
                && BuiltinRecipeTemplates.contains(recipe.getId().getPath()));
        recipes.removeIf(recipe -> config.disabled_recipes.contains(recipe.getId().toString())
                && !(recipe instanceof CraftingRecipe));

        boolean recipeStateChanged = false;
        List<Recipe<?>> exactOrCounted = new ArrayList<>();
        List<Recipe<?>> ordinary = new ArrayList<>();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (entry == null || Boolean.FALSE.equals(entry.server_enabled) || Boolean.FALSE.equals(entry.enabled)) continue;
            recipeStateChanged |= RecipeIntegrity.refresh(entry, rootConfig.custom_items);
            Recipe<?> recipe = Boolean.TRUE.equals(entry.corrupted) ? null
                    : build(entry, recipeBookGroup(entry), rootConfig.custom_items);
            if (recipe != null) {
                if (usesExactOrCountedIngredient(entry)) exactOrCounted.add(recipe);
                else ordinary.add(recipe);
            }
        }
        if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());
        recipes.addAll(0, exactOrCounted);
        recipes.addAll(ordinary);
        setRecipes(recipes);
    }

    private boolean usesExactOrCountedIngredient(CustomRecipeEntry entry) {
        if (entry.hasCountedIngredients()) return true;
        java.util.stream.Stream<String> values = "shaped".equalsIgnoreCase(entry.type)
                ? (entry.keys == null ? java.util.stream.Stream.empty() : entry.keys.values().stream())
                : (entry.ingredients == null ? java.util.stream.Stream.empty() : entry.ingredients.stream());
        return values.anyMatch(CustomItemLibrary::isReference);
    }

    private Recipe<?> build(CustomRecipeEntry entry, String recipeGroup, List<CustomItemEntry> customItems) {
        if (entry.result == null || entry.result.isBlank()) return null;
        ItemStack result = resolveStack(entry.result, customItems);
        if (result.isEmpty()) return null;
        result.setCount(Math.max(1, entry.count));
        Identifier id = entry.serverRecipeId();
        if (!"shaped".equalsIgnoreCase(entry.type)) {
            List<Ingredient> list = new ArrayList<>();
            if (entry.ingredients == null) return null;
            for (int i = 0; i < entry.ingredients.size(); i++) {
                String raw = entry.ingredients.get(i);
                ItemStack stack = resolveStack(raw, customItems);
                if (stack.isEmpty()) return null;
                stack.setCount(Math.max(1, Math.min(entry.ingredientCount(i), stack.getMaxCount())));
                list.add(Ingredient.ofStacks(stack));
            }
            if (list.isEmpty()) return null;
            CraftingRecipe recipe = new ShapelessRecipe(id, recipeGroup, CraftingRecipeCategory.MISC, result,
                    DefaultedList.copyOf(Ingredient.EMPTY, list.toArray(new Ingredient[0])));
            return exactWrapper(recipe, result, entry, customItems);
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        List<String> pattern = CustomItemCraftingRecipe.shrinkPattern(entry.pattern);
        int width = pattern.stream().mapToInt(String::length).max().orElse(0);
        int height = pattern.size();
        if (width < 1 || width > 3 || height > 3) return null;
        DefaultedList<Ingredient> input = DefaultedList.ofSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            char symbol = x < pattern.get(y).length() ? pattern.get(y).charAt(x) : ' ';
            if (symbol == ' ') continue;
            String raw = entry.keys.get(String.valueOf(symbol));
            ItemStack stack = raw == null ? ItemStack.EMPTY : resolveStack(raw, customItems);
            if (stack.isEmpty()) return null;
            stack.setCount(Math.max(1, Math.min(entry.keyCount(String.valueOf(symbol)), stack.getMaxCount())));
            input.set(y * width + x, Ingredient.ofStacks(stack));
        }
        CraftingRecipe recipe = new ShapedRecipe(id, recipeGroup, CraftingRecipeCategory.MISC,
                width, height, input, result);
        return exactWrapper(recipe, result, entry, customItems);
    }

    private ItemStack resolveStack(String value, List<CustomItemEntry> customItems) {
        if (CustomItemLibrary.isReference(value)) {
            return CustomItemLibrary.decode(CustomItemLibrary.find(customItems, value), null);
        }
        Identifier id = Identifier.tryParse(value);
        return id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private String ingredientBaseId(String value, List<CustomItemEntry> customItems) {
        if (!CustomItemLibrary.isReference(value)) return value;
        return CustomItemLibrary.baseItemId(CustomItemLibrary.find(customItems, value));
    }

    private CraftingRecipe exactWrapper(CraftingRecipe recipe, ItemStack result, CustomRecipeEntry entry,
                                        List<CustomItemEntry> customItems) {
        Map<String, ItemStack> stacks = new HashMap<>();
        java.util.stream.Stream<String> values = "shaped".equalsIgnoreCase(entry.type)
                ? (entry.keys == null ? java.util.stream.Stream.empty() : entry.keys.values().stream())
                : (entry.ingredients == null ? java.util.stream.Stream.empty() : entry.ingredients.stream());
        values.filter(CustomItemLibrary::isReference).distinct().forEach(reference -> {
            ItemStack stack = resolveStack(reference, customItems);
            if (!stack.isEmpty()) {
                ItemStack one = stack.copy();
                one.setCount(1);
                stacks.put(reference, one);
            }
        });
        if (stacks.isEmpty() && !entry.hasCountedIngredients()) return recipe;
        if (recipe instanceof ShapedRecipe shaped) {
            return new CustomItemShapedRecipe(shaped, result, entry, stacks);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return new CustomItemShapelessRecipe(shapeless, result, entry, stacks);
        }
        return recipe;
    }

    private String recipeBookGroup(CustomRecipeEntry entry) {
        String signature;
        if ("shaped".equalsIgnoreCase(entry.type)) {
            signature = "shaped:" + String.join("/", entry.pattern == null ? List.of() : entry.pattern)
                    + ":" + (entry.keys == null ? Map.of() : new TreeMap<>(entry.keys))
                    + ":" + (entry.key_counts == null ? Map.of() : new TreeMap<>(entry.key_counts));
        } else {
            List<String> ingredients = new ArrayList<>();
            if (entry.ingredients != null) for (int i = 0; i < entry.ingredients.size(); i++) {
                ingredients.add(entry.ingredients.get(i) + "×" + entry.ingredientCount(i));
            }
            Collections.sort(ingredients);
            signature = "shapeless:" + String.join(",", ingredients);
        }
        return "customrecipe_" + Integer.toUnsignedString(signature.hashCode(), 36);
    }
}
