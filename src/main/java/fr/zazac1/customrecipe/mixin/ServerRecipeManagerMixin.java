package fr.zazac1.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.BuiltinRecipeTemplates;
import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomItemShapedRecipe;
import fr.zazac1.customrecipe.CustomItemShapelessRecipe;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeIntegrity;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.util.NonNullList;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.resources.IResourceManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.item.ItemStack;

import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Shadow private Map<IRecipeType<?>, Map<ResourceLocation, IRecipe<?>>> recipes;
    @Shadow public abstract Collection<IRecipe<?>> getRecipes();

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resources/IResourceManager;Lnet/minecraft/profiler/IProfiler;)V", at = @At("TAIL"))
    private void customrecipe$applyConfig(Map<ResourceLocation, JsonElement> ignored, IResourceManager manager,
                                          IProfiler profiler, CallbackInfo ci) {
        ConfigLoader.invalidate();
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
        List<String> disabledRecipes = ConfigLoader.effectiveDisabledRecipes(rootConfig, config);
        List<IRecipe<?>> recipes = new ArrayList<>(getRecipes());
        // Bundled JSON files are templates only; Quick Add creates independent custom copies.
        recipes.removeIf(recipe -> recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)
                && BuiltinRecipeTemplates.contains(recipe.getId().getPath()));
        recipes.removeIf(recipe -> disabledRecipes.contains(recipe.getId().toString())
                && !(recipe instanceof ICraftingRecipe));

        boolean recipeStateChanged = false;
        List<IRecipe<?>> exactOrCounted = new ArrayList<>();
        List<IRecipe<?>> ordinary = new ArrayList<>();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (entry == null || Boolean.FALSE.equals(entry.server_enabled) || Boolean.FALSE.equals(entry.enabled)) continue;
            recipeStateChanged |= RecipeIntegrity.refresh(entry, rootConfig.custom_items);
            IRecipe<?> recipe = Boolean.TRUE.equals(entry.corrupted) ? null
                    : build(entry, recipeBookGroup(entry), rootConfig.custom_items);
            if (recipe != null) {
                if (usesExactOrCountedIngredient(entry)) exactOrCounted.add(recipe);
                else ordinary.add(recipe);
            }
        }
        if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());
        recipes.addAll(0, exactOrCounted);
        recipes.addAll(ordinary);
        // Minecraft 1.16 removes replaceRecipes on a dedicated server. Rebuild
        // its sole recipe index directly, preserving the priority above.
        Map<IRecipeType<?>, Map<ResourceLocation, IRecipe<?>>> orderedRecipes = new LinkedHashMap<>();
        for (IRecipe<?> recipe : recipes) {
            Map<ResourceLocation, IRecipe<?>> byType = orderedRecipes.computeIfAbsent(
                    recipe.getType(), type -> new LinkedHashMap<>());
            if (byType.put(recipe.getId(), recipe) != null) {
                throw new IllegalStateException("Duplicate recipe ignored with ID " + recipe.getId());
            }
        }
        this.recipes = Collections.unmodifiableMap(orderedRecipes);
    }

    private boolean usesExactOrCountedIngredient(CustomRecipeEntry entry) {
        if (entry.hasCountedIngredients() || entry.hasDistinctIngredients()) return true;
        return allInputValues(entry).stream().anyMatch(CustomItemLibrary::isReference);
    }

    private IRecipe<?> build(CustomRecipeEntry entry, String recipeGroup, List<CustomItemEntry> customItems) {
        if (entry.result == null || entry.result.trim().isEmpty()) return null;
        ItemStack result = resolveStack(entry.result, customItems);
        if (result.isEmpty()) return null;
        result.setCount(Math.max(1, Math.min(64, entry.count)));
        ResourceLocation id = entry.serverRecipeId();
        if (!"shaped".equalsIgnoreCase(entry.type)) {
            List<Ingredient> list = new ArrayList<>();
            if (entry.ingredients == null) return null;
            for (int i = 0; i < entry.ingredients.size(); i++) {
                Ingredient ingredient = resolveIngredient(entry.ingredientOptions(i), entry.ingredientCount(i), customItems);
                if (ingredient.isEmpty()) return null;
                list.add(ingredient);
            }
            if (list.isEmpty()) return null;
            NonNullList<Ingredient> input = NonNullList.create();
            input.addAll(list);
            ICraftingRecipe recipe = new ShapelessRecipe(id, recipeGroup, result, input);
            return exactWrapper(recipe, result, entry, customItems);
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        List<String> pattern = CustomItemCraftingRecipe.shrinkPattern(entry.pattern);
        int width = pattern.stream().mapToInt(String::length).max().orElse(0);
        int height = pattern.size();
        if (width < 1 || width > 3 || height > 3) return null;
        NonNullList<Ingredient> input = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            char symbol = x < pattern.get(y).length() ? pattern.get(y).charAt(x) : ' ';
            if (symbol == ' ') continue;
            String key = String.valueOf(symbol);
            Ingredient ingredient = resolveIngredient(entry.keyOptions(key), entry.keyCount(key), customItems);
            if (ingredient.isEmpty()) return null;
            input.set(y * width + x, ingredient);
        }
        ICraftingRecipe recipe = new ShapedRecipe(id, recipeGroup,
                width, height, input, result);
        return exactWrapper(recipe, result, entry, customItems);
    }

    private ItemStack resolveStack(String value, List<CustomItemEntry> customItems) {
        if (CustomItemLibrary.isReference(value)) {
            return CustomItemLibrary.decode(CustomItemLibrary.find(customItems, value), null);
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && fr.zazac1.customrecipe.Java8.registryContains(Registry.ITEM, id)
                ? new ItemStack(Registry.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private Ingredient resolveIngredient(List<String> values, int count, List<CustomItemEntry> customItems) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String value : values) {
            ItemStack stack = resolveStack(value, customItems);
            if (stack.isEmpty()) return Ingredient.EMPTY;
            stack.setCount(Math.max(1, Math.min(count, stack.getMaxStackSize())));
            stacks.add(stack);
        }
        return stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.of(stacks.stream());
    }

    private List<String> allInputValues(CustomRecipeEntry entry) {
        List<String> values = new ArrayList<>();
        if ("shaped".equalsIgnoreCase(entry.type)) {
            if (entry.keys != null) for (String key : entry.keys.keySet()) values.addAll(entry.keyOptions(key));
        } else if (entry.ingredients != null) {
            for (int i = 0; i < entry.ingredients.size(); i++) values.addAll(entry.ingredientOptions(i));
        }
        return values;
    }

    private String ingredientBaseId(String value, List<CustomItemEntry> customItems) {
        if (!CustomItemLibrary.isReference(value)) return value;
        return CustomItemLibrary.baseItemId(CustomItemLibrary.find(customItems, value));
    }

    private ICraftingRecipe exactWrapper(ICraftingRecipe recipe, ItemStack result, CustomRecipeEntry entry,
                                        List<CustomItemEntry> customItems) {
        Map<String, ItemStack> stacks = new HashMap<>();
        allInputValues(entry).stream().filter(CustomItemLibrary::isReference).distinct().forEach(reference -> {
            ItemStack stack = resolveStack(reference, customItems);
            if (!stack.isEmpty()) {
                ItemStack one = stack.copy();
                one.setCount(1);
                stacks.put(reference, one);
            }
        });
        if (stacks.isEmpty() && !entry.hasCountedIngredients() && !entry.hasDistinctIngredients()) return recipe;
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
            signature = "shaped:" + String.join("/", entry.pattern == null ? fr.zazac1.customrecipe.Java8.list() : entry.pattern)
                    + ":" + (entry.keys == null ? fr.zazac1.customrecipe.Java8.map() : new TreeMap<>(entry.keys))
                    + ":" + (entry.key_alternatives == null ? fr.zazac1.customrecipe.Java8.map() : new TreeMap<>(entry.key_alternatives))
                    + ":" + (entry.key_counts == null ? fr.zazac1.customrecipe.Java8.map() : new TreeMap<>(entry.key_counts));
        } else {
            List<String> ingredients = new ArrayList<>();
            if (entry.ingredients != null) for (int i = 0; i < entry.ingredients.size(); i++) {
                ingredients.add(String.join("|", entry.ingredientOptions(i)) + "×" + entry.ingredientCount(i));
            }
            Collections.sort(ingredients);
            signature = "shapeless:" + String.join(",", ingredients);
        }
        return "customrecipe_" + Integer.toUnsignedString(signature.hashCode(), 36);
    }
}
