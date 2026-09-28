package fr.zazac1.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.zazac1.customrecipe.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Shadow public abstract Collection<Recipe<?>> getRecipes();
    @Shadow public abstract void replaceRecipes(Iterable<Recipe<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void customrecipe$applyConfig(Map<ResourceLocation, JsonElement> ignored, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        ConfigLoader.invalidate();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig();
        for (CustomRecipeEntry entry : config.custom_recipes) RecipeIntegrity.refresh(entry);
        List<Recipe<?>> recipes = new ArrayList<>(getRecipes());
        recipes.removeIf(recipe -> recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)
                && config.disabled_builtin.contains(recipe.getId().getPath()));
        recipes.removeIf(recipe -> config.disabled_recipes.contains(recipe.getId().toString()) && !(recipe instanceof CraftingRecipe));
        int index = 0;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (!Boolean.FALSE.equals(entry.server_enabled) && !Boolean.FALSE.equals(entry.enabled)
                    && !Boolean.TRUE.equals(entry.corrupted)) {
                Recipe<?> recipe = build(entry, recipeBookGroup(entry)); if (recipe != null) recipes.add(recipe);
            }
            index++;
        }
        replaceRecipes(recipes);
    }

    private Recipe<?> build(CustomRecipeEntry entry, String recipeGroup) {
        if (entry == null || entry.result == null) return null;
        ResourceLocation resultId = ResourceLocation.tryParse(entry.result);
        if (resultId == null || !BuiltInRegistries.ITEM.containsKey(resultId)) return null;
        ResourceLocation id = entry.serverRecipeId();
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(resultId), Math.max(1, entry.count));
        if (!"shaped".equalsIgnoreCase(entry.type)) {
            List<Ingredient> list = new ArrayList<>();
            for (String raw : entry.ingredients) { ResourceLocation item = ResourceLocation.tryParse(raw); if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) return null; list.add(Ingredient.of(BuiltInRegistries.ITEM.get(item))); }
            if (list.isEmpty()) return null;
            NonNullList<Ingredient> input = NonNullList.create();
            input.addAll(list);
            return new ShapelessRecipe(id, recipeGroup, CraftingBookCategory.MISC, result, input);
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0), height = entry.pattern.size();
        if (width < 1 || width > 3 || height > 3) return null;
        NonNullList<Ingredient> input = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            char symbol = x < entry.pattern.get(y).length() ? entry.pattern.get(y).charAt(x) : ' ';
            if (symbol == ' ') continue;
            String raw = entry.keys.get(String.valueOf(symbol)); ResourceLocation item = raw == null ? null : ResourceLocation.tryParse(raw);
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) return null;
            input.set(y * width + x, Ingredient.of(BuiltInRegistries.ITEM.get(item)));
        }
        return new ShapedRecipe(id, recipeGroup, CraftingBookCategory.MISC, width, height, input, result);
    }

    private String recipeBookGroup(CustomRecipeEntry entry) {
        String signature;
        if ("shaped".equalsIgnoreCase(entry.type)) {
            signature = "shaped:" + String.join("/", entry.pattern == null ? List.of() : entry.pattern)
                    + ":" + (entry.keys == null ? Map.of() : new TreeMap<>(entry.keys));
        } else {
            List<String> ingredients = new ArrayList<>(entry.ingredients == null ? List.of() : entry.ingredients);
            ingredients.sort(String::compareTo);
            signature = "shapeless:" + String.join(",", ingredients);
        }
        return "customrecipe_" + Integer.toUnsignedString(signature.hashCode(), 36);
    }
}
