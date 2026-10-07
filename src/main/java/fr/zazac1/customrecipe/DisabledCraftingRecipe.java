package fr.zazac1.customrecipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class DisabledCraftingRecipe implements CraftingRecipe {
   private final CraftingRecipe delegate;

   public DisabledCraftingRecipe(CraftingRecipe delegate) {
      this.delegate = delegate;
   }

   public CraftingRecipe delegate() {
      return this.delegate;
   }

   public boolean matches(CraftingRecipeInput input, World world) {
      return false;
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

