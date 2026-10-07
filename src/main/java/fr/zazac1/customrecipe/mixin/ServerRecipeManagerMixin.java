package fr.zazac1.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeIntegrity;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeEntry;
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
   @Shadow
   public abstract Collection<RecipeEntry<?>> values();

   @Shadow
   public abstract void setRecipes(Iterable<RecipeEntry<?>> var1);

   @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
   private void customrecipe$applyConfig(Map<Identifier, JsonElement> ignored, ResourceManager resourceManager, Profiler profiler, CallbackInfo ci) {
      ConfigLoader.invalidate();
      ModConfig rootConfig = ConfigLoader.get();
      WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
      List<RecipeEntry<?>> recipes = new ArrayList<>(this.values());
      if (!config.disabled_builtin.isEmpty()) {
         recipes.removeIf(entryx -> {
            Identifier id = entryx.id();
            if (!id.getNamespace().equals("customrecipe")) {
               return false;
            } else {
               for (String disabled : config.disabled_builtin) {
                  if (id.getPath().equals(disabled)) {
                     return true;
                  }
               }

               return false;
            }
         });
      }

      if (!config.disabled_recipes.isEmpty()) {
         recipes.removeIf(entryx -> config.disabled_recipes.contains(entryx.id().toString()) && !(entryx.value() instanceof CraftingRecipe));
      }

      int idx = 0;
      boolean recipeStateChanged = false;

      for (CustomRecipeEntry entry : config.custom_recipes) {
         if (entry != null && !Boolean.FALSE.equals(entry.enabled) && !Boolean.FALSE.equals(entry.server_enabled)) {
            recipeStateChanged |= RecipeIntegrity.refresh(entry);
            RecipeEntry<?> built = Boolean.TRUE.equals(entry.corrupted) ? null : this.buildCustomRecipe(entry, idx, this.recipeBookGroup(entry));
            if (Boolean.TRUE.equals(entry.corrupted)) {
               CustomRecipeMod.LOGGER
                  .warn(
                     "[CustomRecipe] Disabled corrupted recipe {}: missing {}. Reinstall required mods {} or delete the recipe.",
                     new Object[]{entry.id, entry.missing_items, entry.required_mods}
                  );
               idx++;
            } else {
               idx++;
               if (built != null) {
                  recipes.add(built);
               }
            }
         } else {
            idx++;
         }
      }

      if (recipeStateChanged) {
         ConfigLoader.saveIntegrityState(ConfigLoader.get());
      }

      this.setRecipes(recipes);
   }

   private RecipeEntry<?> buildCustomRecipe(CustomRecipeEntry entry, int idx, String recipeGroup) {
      if (entry == null) {
         return null;
      } else if (entry.result != null && !entry.result.isBlank()) {
         Identifier resultId = Identifier.tryParse(entry.result);
         if (resultId != null && Registries.ITEM.containsId(resultId)) {
            ItemStack result = new ItemStack((ItemConvertible)Registries.ITEM.get(resultId), Math.max(1, entry.count));
            Identifier key = entry.serverRecipeId();
            return "shaped".equalsIgnoreCase(entry.type)
               ? this.buildShaped(entry, result, key, recipeGroup)
               : this.buildShapeless(entry, result, key, recipeGroup);
         } else {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", entry.result);
            return null;
         }
      } else {
         return null;
      }
   }

   private String recipeBookGroup(CustomRecipeEntry entry) {
      return "customrecipe_" + Integer.toUnsignedString(this.recipeInputSignature(entry).hashCode(), 36);
   }

   private String recipeInputSignature(CustomRecipeEntry entry) {
      if ("shaped".equalsIgnoreCase(entry.type)) {
         return "shaped:" + entry.pattern + ":" + entry.keys;
      } else {
         List<String> ingredients = (List<String>)(entry.ingredients == null ? List.of() : new ArrayList<>(entry.ingredients));
         Collections.sort(ingredients);
         return "shapeless:" + ingredients;
      }
   }

   private RecipeEntry<ShapelessRecipe> buildShapeless(CustomRecipeEntry entry, ItemStack result, Identifier key, String recipeGroup) {
      List<String> rawIngredients = entry.ingredients;
      if (rawIngredients != null && !rawIngredients.isEmpty()) {
         List<Ingredient> ingredients = new ArrayList<>();

         for (String itemId : rawIngredients) {
            if (itemId != null && !itemId.isBlank()) {
               Identifier id = Identifier.tryParse(itemId.trim());
               if (id == null || !Registries.ITEM.containsId(id)) {
                  CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shapeless ingredient not found: {}", itemId);
                  return null;
               }

               ingredients.add(Ingredient.ofItems(new ItemConvertible[]{(ItemConvertible)Registries.ITEM.get(id)}));
            }
         }

         if (ingredients.isEmpty()) {
            return null;
         } else {
            ShapelessRecipe recipe = new ShapelessRecipe(
               recipeGroup, CraftingRecipeCategory.MISC, result, DefaultedList.copyOf(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new))
            );
            return new RecipeEntry(key, recipe);
         }
      } else {
         return null;
      }
   }

   private RecipeEntry<ShapedRecipe> buildShaped(CustomRecipeEntry entry, ItemStack result, Identifier key, String recipeGroup) {
      List<String> pattern = entry.pattern;
      Map<String, String> keysMap = entry.keys;
      if (pattern == null || pattern.isEmpty()) {
         return null;
      } else if (keysMap != null && !keysMap.isEmpty()) {
         Map<Character, Ingredient> symbols = new LinkedHashMap<>();

         for (Entry<String, String> kv : keysMap.entrySet()) {
            if (kv.getKey() != null && !kv.getKey().isEmpty()) {
               char sym = kv.getKey().charAt(0);
               Identifier itemId = Identifier.tryParse(kv.getValue());
               if (itemId == null || !Registries.ITEM.containsId(itemId)) {
                  CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shaped key item not found: {}", kv.getValue());
                  return null;
               }

               symbols.put(sym, Ingredient.ofItems(new ItemConvertible[]{(ItemConvertible)Registries.ITEM.get(itemId)}));
            }
         }

         RawShapedRecipe rawRecipe;
         try {
            rawRecipe = RawShapedRecipe.create(symbols, pattern);
         } catch (Exception var12) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Invalid shaped pattern for result: {} — {}", entry.result, var12.getMessage());
            return null;
         }

         if (rawRecipe == null) {
            return null;
         } else {
            ShapedRecipe recipe = new ShapedRecipe(recipeGroup, CraftingRecipeCategory.MISC, rawRecipe, result);
            return new RecipeEntry(key, recipe);
         }
      } else {
         return null;
      }
   }
}

