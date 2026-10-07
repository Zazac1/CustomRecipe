package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
final class WorldRecipesScreen extends Screen {
   private final ConfigScreen config;
   private final RecipeWorldsScreen parent;
   private final String worldId;
   private final String worldName;
   private int scroll;

   WorldRecipesScreen(ConfigScreen config, RecipeWorldsScreen parent, String worldId, String worldName) {
      super(
         Text.translatable(
            "customrecipe.world.recipes_in",
            new Object[]{worldName != null && !worldName.isBlank() ? worldName : Text.translatable("customrecipe.world.default_name")}
         )
      );
      this.config = config;
      this.parent = parent;
      this.worldId = worldId;
      this.worldName = worldName;
   }

   protected void init() {
      int x = this.width / 2 - 170;
      int y = 40;
      List<CustomRecipeEntry> recipes = this.orderedRecipes();
      int visible = Math.max(1, (this.height - 92) / 22);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, recipes.size() - visible)));
      boolean wroteEnabledHeader = false;
      boolean wroteDisabledHeader = false;

      for (int i = this.scroll; i < Math.min(recipes.size(), this.scroll + visible); i++) {
         CustomRecipeEntry recipe = recipes.get(i);
         boolean enabled = this.isEnabled(recipe);
         if (enabled && !wroteEnabledHeader) {
            this.addDrawableChild(new MultilineTextWidget(x, y, Text.translatable("customrecipe.world.enabled_recipes").withColor(5635925), this.textRenderer));
            y += 16;
            wroteEnabledHeader = true;
         }

         if (!enabled && !wroteDisabledHeader) {
            this.addDrawableChild(
               new MultilineTextWidget(x, y, Text.translatable("customrecipe.world.available_recipes").withColor(11184810), this.textRenderer)
            );
            y += 16;
            wroteDisabledHeader = true;
         }

         int rowY = y;
         this.addDrawableChild(ButtonWidget.builder(Text.literal(this.recipeName(recipe)), b -> {
            this.toggle(recipe);
            this.clearAndInit();
         }).dimensions(x + 24, rowY, 316, 18).build());
         this.addDrawable(
            (ctx, mouseX, mouseY, delta) -> CustomRecipeSprites.draw(
               ctx, this.isEnabled(recipe) ? CustomRecipeSprites.ACCEPT : CustomRecipeSprites.REJECT, x, rowY, 18, 18
            )
         );
         y += 22;
      }

      this.addDrawableChild(
         ButtonWidget.builder(Text.translatable("customrecipe.button.back"), b -> this.client.setScreen(this.parent))
            .dimensions(this.width / 2 - 55, this.height - 28, 110, 20)
            .build()
      );
   }

   private List<CustomRecipeEntry> orderedRecipes() {
      List<CustomRecipeEntry> recipes = new ArrayList<>();

      for (CustomRecipeEntry recipe : this.config.recipes) {
         if (recipe != null && !Boolean.TRUE.equals(recipe.corrupted)) {
            recipes.add(recipe);
         }
      }

      recipes.sort(Comparator.comparing(this::isEnabled).reversed().thenComparing(this::recipeName, String.CASE_INSENSITIVE_ORDER));
      return recipes;
   }

   private boolean isEnabled(CustomRecipeEntry recipe) {
      return this.worldId != null && recipe.world_ids != null && recipe.world_ids.contains(this.worldId);
   }

   private void toggle(CustomRecipeEntry recipe) {
      if (this.worldId != null) {
         this.config.setWorldAssigned(recipe, this.worldId, this.worldName, !this.isEnabled(recipe));
         this.config.persistLocalWorldAssignments();
      }
   }

   private String recipeName(CustomRecipeEntry recipe) {
      String id = recipe.result != null && !recipe.result.isBlank() ? recipe.result : recipe.id;
      if (id != null && !id.isBlank()) {
         String path = id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
         StringBuilder name = new StringBuilder();

         for (String word : path.split("_")) {
            if (!word.isEmpty()) {
               name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
         }

         return name.toString().trim();
      } else {
         return "Unnamed recipe";
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int max = Math.max(0, this.orderedRecipes().size() - Math.max(1, (this.height - 92) / 22));
      int next = Math.max(0, Math.min(max, this.scroll - (int)verticalAmount));
      if (next != this.scroll) {
         this.scroll = next;
         this.clearAndInit();
      }

      return true;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 16777215);
      super.render(context, mouseX, mouseY, delta);
   }

   public boolean shouldPause() {
      return true;
   }

   public void close() {
      this.client.setScreen(this.parent);
   }
}
