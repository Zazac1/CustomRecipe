package fr.zazac1.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeIntegrity;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Shadow public abstract Collection<Recipe<?>> getRecipes();
    @Shadow public abstract void replaceRecipes(Iterable<Recipe<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void customrecipe$applyConfig(Map<ResourceLocation, JsonElement> ignored, ResourceManager manager,
                                          ProfilerFiller profiler, CallbackInfo ci) {
        ConfigLoader.invalidate();
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
        List<Recipe<?>> recipes = new ArrayList<>(getRecipes());
        recipes.removeIf(recipe -> recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)
                && config.disabled_builtin.contains(recipe.getId().getPath()));
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
        replaceRecipes(recipes);
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
        result.setCount(Math.max(1, Math.min(64, entry.count)));
        ResourceLocation id = entry.serverRecipeId();
        if (!"shaped".equalsIgnoreCase(entry.type)) {
            List<Ingredient> list = new ArrayList<>();
            if (entry.ingredients == null) return null;
            for (int i = 0; i < entry.ingredients.size(); i++) {
                String raw = entry.ingredients.get(i);
                ItemStack stack = resolveStack(raw, customItems);
                if (stack.isEmpty()) return null;
                stack.setCount(Math.max(1, Math.min(entry.ingredientCount(i), stack.getMaxStackSize())));
                list.add(Ingredient.of(stack));
            }
            if (list.isEmpty()) return null;
            NonNullList<Ingredient> input = NonNullList.create();
            input.addAll(list);
            CraftingRecipe recipe = new ShapelessRecipe(id, recipeGroup, CraftingBookCategory.MISC, result, input);
            return exactWrapper(recipe, entry, customItems);
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0);
        int height = entry.pattern.size();
        if (width < 1 || width > 3 || height > 3) return null;
        NonNullList<Ingredient> input = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            char symbol = x < entry.pattern.get(y).length() ? entry.pattern.get(y).charAt(x) : ' ';
            if (symbol == ' ') continue;
            String raw = entry.keys.get(String.valueOf(symbol));
            ItemStack stack = raw == null ? ItemStack.EMPTY : resolveStack(raw, customItems);
            if (stack.isEmpty()) return null;
            stack.setCount(Math.max(1, Math.min(entry.keyCount(String.valueOf(symbol)), stack.getMaxStackSize())));
            input.set(y * width + x, Ingredient.of(stack));
        }
        CraftingRecipe recipe = new ShapedRecipe(id, recipeGroup, CraftingBookCategory.MISC,
                width, height, input, result);
        return exactWrapper(recipe, entry, customItems);
    }

    private ItemStack resolveStack(String value, List<CustomItemEntry> customItems) {
        if (CustomItemLibrary.isReference(value)) {
            return CustomItemLibrary.decode(CustomItemLibrary.find(customItems, value), null);
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private String ingredientBaseId(String value, List<CustomItemEntry> customItems) {
        if (!CustomItemLibrary.isReference(value)) return value;
        return CustomItemLibrary.baseItemId(CustomItemLibrary.find(customItems, value));
    }

    private CraftingRecipe exactWrapper(CraftingRecipe recipe, CustomRecipeEntry entry,
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
        return stacks.isEmpty() && !entry.hasCountedIngredients()
                ? recipe : new CustomItemCraftingRecipe(recipe, entry, stacks);
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
