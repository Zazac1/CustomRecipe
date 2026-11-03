package fr.zazac1.customrecipe.mixin;

import com.google.gson.JsonElement;
import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomItemCraftingRecipe;
import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeIntegrity;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
      List<String> disabledRecipes = ConfigLoader.effectiveDisabledRecipes(rootConfig, config);
      List<RecipeVariantRule> disabledVariants =
            ConfigLoader.effectiveDisabledRecipeVariants(rootConfig, config);
      List<RecipeEntry<?>> recipes = new ArrayList<>(values());

      // Bundled JSON files are templates only; Quick Add creates independent custom copies.
      recipes.removeIf(candidate -> {
         Identifier id = candidate.id();
         return id.getNamespace().equals(CustomRecipeMod.MOD_ID)
               && fr.zazac1.customrecipe.BuiltinRecipeTemplates.contains(id.getPath());
      });
      if (!disabledRecipes.isEmpty()) {
         recipes.removeIf(candidate -> disabledRecipes.contains(candidate.id().toString())
               && !(candidate.value() instanceof CraftingRecipe));
      }

      // Keep crafting recipes in the manager so the editor can re-enable them,
      // but decorate them so every recipe lookup path rejects disabled entries.
      // This is the same strategy used by the working 1.21.11 implementation.
      Map<String, Set<String>> variantsByRecipe = new HashMap<>();
      for (RecipeVariantRule rule : disabledVariants) {
         variantsByRecipe.computeIfAbsent(rule.recipe_id, ignoredId -> new HashSet<>())
               .add(rule.material_id);
      }
      int disabledCraftingCount = 0;
      int filteredVariantCount = 0;
      for (int i = 0; i < recipes.size(); i++) {
         RecipeEntry<?> entry = recipes.get(i);
         String recipeId = entry.id().toString();
         if (disabledRecipes.contains(recipeId) && entry.value() instanceof CraftingRecipe recipe) {
            recipes.set(i, new RecipeEntry<>(entry.id(), new DisabledCraftingRecipe(recipe)));
            disabledCraftingCount++;
            continue;
         }
         Set<String> blockedMaterials = variantsByRecipe.get(recipeId);
         if (blockedMaterials != null && entry.value() instanceof CraftingRecipe recipe) {
            recipes.set(i, new RecipeEntry<>(entry.id(),
                  new VariantFilteredCraftingRecipe(recipe, blockedMaterials)));
            filteredVariantCount++;
         }
      }
      if (disabledCraftingCount > 0 || filteredVariantCount > 0) {
         CustomRecipeMod.LOGGER.info(
               "[CustomRecipe] Applied default recipe rules: {} crafting recipe(s) disabled, {} recipe(s) filtered by material.",
               disabledCraftingCount, filteredVariantCount);
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
      if (entry.hasCountedIngredients() || entry.hasDistinctIngredients()) return true;
      return allInputValues(entry).anyMatch(CustomItemLibrary::isReference);
   }

   private java.util.stream.Stream<String> allInputValues(CustomRecipeEntry entry) {
      if ("shaped".equalsIgnoreCase(entry.type)) {
         return entry.keys == null ? java.util.stream.Stream.empty()
               : entry.keys.keySet().stream().flatMap(key -> entry.keyOptions(key).stream());
      }
      return entry.ingredients == null ? java.util.stream.Stream.empty()
            : java.util.stream.IntStream.range(0, entry.ingredients.size())
                  .boxed().flatMap(index -> entry.ingredientOptions(index).stream());
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
      allInputValues(entry).filter(CustomItemLibrary::isReference).distinct().forEach(reference -> {
         ItemStack stack = resolveStack(reference, customItems);
         if (!stack.isEmpty()) stacks.put(reference, stack.copyWithCount(1));
      });
      return stacks.isEmpty() && !entry.hasCountedIngredients() && !entry.hasDistinctIngredients()
            ? recipe : new CustomItemCraftingRecipe(recipe, entry, stacks);
   }

   private String recipeBookGroup(CustomRecipeEntry entry) {
      return "customrecipe_" + Integer.toUnsignedString(recipeInputSignature(entry).hashCode(), 36);
   }

   private String recipeInputSignature(CustomRecipeEntry entry) {
      if ("shaped".equalsIgnoreCase(entry.type)) {
         Map<String, List<String>> options = new LinkedHashMap<>();
         if (entry.keys != null) entry.keys.keySet().forEach(key -> options.put(key, entry.keyOptions(key)));
         return "shaped:" + entry.pattern + ":" + options + ":" + entry.key_counts;
      }
      List<String> ingredients = new ArrayList<>();
      if (entry.ingredients != null) {
         for (int i = 0; i < entry.ingredients.size(); i++) {
            ingredients.add(entry.ingredientOptions(i) + "×" + entry.ingredientCount(i));
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
         Ingredient ingredient = resolveIngredient(entry.ingredientOptions(i), entry.ingredientCount(i), customItems);
         if (ingredient == null) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shapeless ingredient option not found: {}", entry.ingredientOptions(i));
            return null;
         }
         ingredients.add(ingredient);
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
         Ingredient ingredient = resolveIngredient(entry.keyOptions(pair.getKey()), entry.keyCount(pair.getKey()), customItems);
         if (ingredient == null) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Shaped key option not found: {}", entry.keyOptions(pair.getKey()));
            return null;
         }
         symbols.put(pair.getKey().charAt(0), ingredient);
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

   private Ingredient resolveIngredient(List<String> options, int count, List<CustomItemEntry> customItems) {
      List<ItemStack> stacks = new ArrayList<>();
      for (String option : options) {
         ItemStack stack = resolveStack(option, customItems);
         if (stack.isEmpty()) return null;
         stack.setCount(Math.max(1, Math.min(count, stack.getMaxCount())));
         stacks.add(stack);
      }
      return stacks.isEmpty() ? null : Ingredient.ofStacks(stacks.toArray(ItemStack[]::new));
   }
}
