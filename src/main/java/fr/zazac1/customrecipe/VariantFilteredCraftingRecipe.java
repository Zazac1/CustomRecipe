package fr.zazac1.customrecipe;

import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class VariantFilteredCraftingRecipe implements CraftingRecipe {
   private final CraftingRecipe delegate;
   private final Set<String> blockedMaterials;

   public VariantFilteredCraftingRecipe(CraftingRecipe delegate, Set<String> blockedMaterials) {
      this.delegate = delegate;
      this.blockedMaterials = Set.copyOf(blockedMaterials);
   }

   public CraftingRecipe delegate() {
      return this.delegate;
   }

   public boolean matches(CraftingRecipeInput input, World world) {
      return !this.delegate.matches(input, world)
         ? false
         : input.getStacks()
            .stream()
            .filter(stack -> !stack.isEmpty())
            .map(stack -> Registries.ITEM.getId(stack.getItem()).toString())
            .noneMatch(this.blockedMaterials::contains);
   }

   public ItemStack craft(CraftingRecipeInput input, WrapperLookup registries) {
      return this.delegate.craft(input, registries);
   }

   public boolean fits(int width, int height) {
      return this.delegate.fits(width, height);
   }

   public ItemStack getResult(WrapperLookup registries) {
      return this.delegate.getResult(registries);
   }

   public RecipeSerializer<?> getSerializer() {
      return this.delegate.getSerializer();
   }

   public CraftingRecipeCategory getCategory() {
      return this.delegate.getCategory();
   }

   public DefaultedList<Ingredient> getIngredients() {
      return this.delegate.getIngredients();
   }

   public String getGroup() {
      return this.delegate.getGroup();
   }

   public boolean showNotification() {
      return this.delegate.showNotification();
   }
}

