package fr.isaac.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.isaac.customrecipe.*;
import net.minecraft.item.ItemStack;
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
import java.util.*;

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Shadow public abstract Collection<Recipe<?>> values();
    @Shadow public abstract void setRecipes(Iterable<Recipe<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
    private void customrecipe$applyConfig(Map<Identifier, JsonElement> ignored, ResourceManager manager, Profiler profiler, CallbackInfo ci) {
        ConfigLoader.invalidate(); ModConfig config = ConfigLoader.get();
        List<Recipe<?>> recipes = new ArrayList<>(values());
        recipes.removeIf(recipe -> recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)
                && config.disabled_builtin.contains(recipe.getId().getPath()));
        recipes.removeIf(recipe -> config.disabled_recipes.contains(recipe.getId().toString()) && !(recipe instanceof CraftingRecipe));
        int index = 0;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (!Boolean.FALSE.equals(entry.server_enabled) && !Boolean.FALSE.equals(entry.enabled)) {
                Recipe<?> recipe = build(entry, recipeBookGroup(entry)); if (recipe != null) recipes.add(recipe);
            }
            index++;
        }
        setRecipes(recipes);
    }

    private Recipe<?> build(CustomRecipeEntry entry, String recipeGroup) {
        if (entry == null || entry.result == null) return null;
        Identifier resultId = Identifier.tryParse(entry.result);
        if (resultId == null || !Registries.ITEM.containsId(resultId)) return null;
        Identifier id = entry.serverRecipeId();
        ItemStack result = new ItemStack(Registries.ITEM.get(resultId), Math.max(1, entry.count));
        if (!"shaped".equalsIgnoreCase(entry.type)) {
            List<Ingredient> list = new ArrayList<>();
            for (String raw : entry.ingredients) { Identifier item = Identifier.tryParse(raw); if (item == null || !Registries.ITEM.containsId(item)) return null; list.add(Ingredient.ofItems(Registries.ITEM.get(item))); }
            return list.isEmpty() ? null : new ShapelessRecipe(id, recipeGroup, CraftingRecipeCategory.MISC, result, DefaultedList.copyOf(Ingredient.EMPTY, list.toArray(new Ingredient[0])));
        }
        if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
        int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0), height = entry.pattern.size();
        if (width < 1 || width > 3 || height > 3) return null;
        DefaultedList<Ingredient> input = DefaultedList.ofSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            char symbol = x < entry.pattern.get(y).length() ? entry.pattern.get(y).charAt(x) : ' ';
            if (symbol == ' ') continue;
            String raw = entry.keys.get(String.valueOf(symbol)); Identifier item = raw == null ? null : Identifier.tryParse(raw);
            if (item == null || !Registries.ITEM.containsId(item)) return null;
            input.set(y * width + x, Ingredient.ofItems(Registries.ITEM.get(item)));
        }
        return new ShapedRecipe(id, recipeGroup, CraftingRecipeCategory.MISC, width, height, input, result);
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
