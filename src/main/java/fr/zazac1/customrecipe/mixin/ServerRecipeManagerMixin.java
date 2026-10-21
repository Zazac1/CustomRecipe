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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class ServerRecipeManagerMixin {
   @Shadow public abstract Collection<RecipeEntry<?>> values();
   @Shadow public abstract void setRecipes(Iterable<RecipeEntry<?>> recipes);
   @Shadow @Final private RegistryWrapper.WrapperLookup registryLookup;

   @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
   private void customrecipe$applyConfig(Map<Identifier, JsonElement> ignored, ResourceManager resourceManager,
                                         Profiler profiler, CallbackInfo ci) {
      ConfigLoader.invalidate();
      ModConfig rootConfig = ConfigLoader.get();
      WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
      List<RecipeEntry<?>> recipes = new ArrayList<>(values());

      // Bundled JSON files are templates only; Quick Add creates independent custom copies.
      recipes.removeIf(candidate -> {
         Identifier id = candidate.id();
         return id.getNamespace().equals(CustomRecipeMod.MOD_ID)
               && fr.zazac1.customrecipe.BuiltinRecipeTemplates.contains(id.getPath());
      });
      if (!config.disabled_recipes.isEmpty()) {
         recipes.removeIf(candidate -> config.disabled_recipes.contains(candidate.id().toString())
               && !(candidate.value() instanceof CraftingRecipe));
      }

      int index = 0;
      boolean recipeStateChanged = false;
      List<RecipeEntry<?>> exactCustomRecipes = new ArrayList<>();
      List<RecipeEntry<?>> ordinaryCustomRecipes = new ArrayList<>();
      for (CustomRecipeEntry entry : config.custom_recipes) {
         if (entry == null || Boolean.FALSE.equals(entry.enabled) || Boolean.FALSE.equals(entry.server_enabled)) {
            index++;
            continue;
         }
         recipeStateChanged |= RecipeIntegrity.refresh(entry, rootConfig.custom_items);
         RecipeEntry<?> built = Boolean.TRUE.equals(entry.corrupted) ? null
               : buildCustomRecipe(entry, index, recipeBookGroup(entry), rootConfig.custom_items);
         if (Boolean.TRUE.equals(entry.corrupted)) {
            CustomRecipeMod.LOGGER.warn(
                  "[CustomRecipe] Disabled corrupted recipe {}: missing {}. Reinstall required mods {} or delete the recipe.",
                  entry.id, entry.missing_items, entry.required_mods);
         } else if (built != null) {
            if (usesExactOrCountedIngredient(entry)) exactCustomRecipes.add(built);
            else ordinaryCustomRecipes.add(built);
         }
         index++;
      }

      if (recipeStateChanged) ConfigLoader.saveIntegrityState(ConfigLoader.get());
      recipes.addAll(0, exactCustomRecipes);
      recipes.addAll(ordinaryCustomRecipes);
      setRecipes(recipes);
   }

   private boolean usesExactOrCountedIngredient(CustomRecipeEntry entry) {
      if (entry.hasCountedIngredients()) return true;
      java.util.stream.Stream<String> values = "shaped".equalsIgnoreCase(entry.type)
            ? (entry.keys == null ? java.util.stream.Stream.empty() : entry.keys.values().stream())
            : (entry.ingredients == null ? java.util.stream.Stream.empty() : entry.ingredients.stream());
      return values.anyMatch(CustomItemLibrary::isReference);
   }

   private RecipeEntry<?> buildCustomRecipe(CustomRecipeEntry entry, int index, String recipeGroup,
                                             List<CustomItemEntry> customItems) {
      if (entry.result == null || entry.result.isBlank()) return null;
      ItemStack result = resolveStack(entry.result, customItems);
      if (result.isEmpty()) {
         CustomRecipeMod.LOGGER.warn("[CustomRecipe] Unknown result item: {}", entry.result);
         return null;
      }
      result = result.copyWithCount(Math.max(1, entry.count));
      Identifier key = entry.serverRecipeId();
      return "shaped".equalsIgnoreCase(entry.type)
            ? buildShaped(entry, result, key, recipeGroup, customItems)
            : buildShapeless(entry, result, key, recipeGroup, customItems);
   }

   private ItemStack resolveStack(String value, List<CustomItemEntry> customItems) {
      if (CustomItemLibrary.isReference(value)) {
         return CustomItemLibrary.decode(CustomItemLibrary.find(customItems, value), registryLookup);
      }
      Identifier id = Identifier.tryParse(value);
      return id != null && Registries.ITEM.containsId(id)
            ? new ItemStack((ItemConvertible) Registries.ITEM.get(id)) : ItemStack.EMPTY;
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
         if (!stack.isEmpty()) stacks.put(reference, stack.copyWithCount(1));
      });
      return stacks.isEmpty() && !entry.hasCountedIngredients()
            ? recipe : new CustomItemCraftingRecipe(recipe, entry, stacks);
   }

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
            ingredients.add(entry.ingredients.get(i) + "×" + entry.ingredientCount(i));
         }
      }
      Collections.sort(ingredients);
      return "shapeless:" + ingredients;
   }

   private RecipeEntry<?> buildShapeless(CustomRecipeEntry entry, ItemStack result, Identifier key,
                                          String recipeGroup, List<CustomItemEntry> customItems) {
      if (entry.ingredients == null || entry.ingredients.isEmpty()) return null;
      List<Ingredient> ingredients = new ArrayList<>();
      for (int i = 0; i < entry.ingredients.size(); i++) {
         String value = entry.ingredients.get(i);
         if (value == null || value.isBlank()) continue;
         ItemStack stack = resolveStack(value.trim(), customItems);
         if (stack.isEmpty()) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shapeless ingredient not found: {}", value);
            return null;
         }
         stack.setCount(Math.max(1, Math.min(entry.ingredientCount(i), stack.getMaxCount())));
         ingredients.add(Ingredient.ofStacks(stack));
      }
      if (ingredients.isEmpty()) return null;
      ShapelessRecipe recipe = new ShapelessRecipe(recipeGroup, CraftingRecipeCategory.MISC, result,
            DefaultedList.copyOf(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new)));
      return new RecipeEntry<>(key, exactWrapper(recipe, entry, customItems));
   }

   private RecipeEntry<?> buildShaped(CustomRecipeEntry entry, ItemStack result, Identifier key,
                                       String recipeGroup, List<CustomItemEntry> customItems) {
      if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null || entry.keys.isEmpty()) return null;
      List<String> pattern = CustomItemCraftingRecipe.shrinkPattern(entry.pattern);
      if (pattern.isEmpty()) return null;
      Map<Character, Ingredient> symbols = new LinkedHashMap<>();
      for (Map.Entry<String, String> pair : entry.keys.entrySet()) {
         if (pair.getKey() == null || pair.getKey().isEmpty()) continue;
         ItemStack stack = resolveStack(pair.getValue(), customItems);
         if (stack.isEmpty()) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shaped key item not found: {}", pair.getValue());
            return null;
         }
         stack.setCount(Math.max(1, Math.min(entry.keyCount(pair.getKey()), stack.getMaxCount())));
         symbols.put(pair.getKey().charAt(0), Ingredient.ofStacks(stack));
      }
      RawShapedRecipe raw;
      try {
         raw = RawShapedRecipe.create(symbols, pattern);
      } catch (RuntimeException error) {
         CustomRecipeMod.LOGGER.warn("[CustomRecipe] Invalid shaped pattern for result: {} — {}",
               entry.result, error.getMessage());
         return null;
      }
      if (raw == null) return null;
      ShapedRecipe recipe = new ShapedRecipe(recipeGroup, CraftingRecipeCategory.MISC, raw, result);
      return new RecipeEntry<>(key, exactWrapper(recipe, entry, customItems));
   }
}
