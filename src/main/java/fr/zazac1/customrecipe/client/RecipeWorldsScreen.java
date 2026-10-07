package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
   private static final int ROW = 56;
   private static final DateTimeFormatter LAST_PLAYED_FORMAT = DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US).withZone(ZoneId.systemDefault());
   private final ConfigScreen config;
   private final CustomRecipesScreen returnScreen;
   private final int recipeIndex;
   private final List<RecipeWorldsScreen.LocalWorld> worlds = new ArrayList<>();
   private TextFieldWidget search;
   private int scroll;

   RecipeWorldsScreen(ConfigScreen config, CustomRecipesScreen returnScreen, int recipeIndex) {
      super(Text.literal("Select worlds"));
      this.config = config;
      this.returnScreen = returnScreen;
      this.recipeIndex = recipeIndex;
   }

   protected void init() {
      if (this.recipeIndex < 0 || this.recipeIndex >= this.config.recipes.size()) {
         this.client.setScreen(this.returnScreen);
      } else if (this.config.isServerManaged()) {
         this.addDrawableChild(ButtonWidget.builder(Text.literal("Add current server world"), b -> {
            this.config.addToCurrentWorld(this.recipe());
            this.client.setScreen(this.returnScreen);
         }).dimensions(this.width / 2 - 110, this.height / 2 - 12, 220, 20).build());
         this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Back"), b -> this.client.setScreen(this.returnScreen))
               .dimensions(this.width / 2 - 55, this.height / 2 + 16, 110, 20)
               .build()
         );
      } else {
         this.scanWorlds();
         this.search = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 28, 200, 20, Text.literal("Search worlds"));
         this.search.setPlaceholder(Text.literal("Search..."));
         this.search.setChangedListener(value -> this.scroll = 0);
         this.addDrawableChild(this.search);
         this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Back"), b -> this.client.setScreen(this.returnScreen))
               .dimensions(this.width / 2 - 55, this.height - 28, 110, 20)
               .build()
         );
      }
   }

   private void scanWorlds() {
      this.worlds.clear();

      try {
         Path saves = this.client.getLevelStorage().getSavesDirectory();
         if (saves == null || !Files.isDirectory(saves)) {
            return;
         }

         try (Stream<Path> entries = Files.list(saves)) {
            entries.filter(x$0 -> Files.isDirectory(x$0))
               .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
               .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
               .forEach(path -> this.worlds.add(this.createWorld(path)));
         }
      } catch (RuntimeException | IOException var7) {
      }
   }

   private RecipeWorldsScreen.LocalWorld createWorld(Path directory) {
      String id = WorldRecipeAssignments.worldId(directory);
      String folderName = directory.getFileName().toString();
      RecipeWorldsScreen.WorldDetails details = this.readWorldDetails(directory, folderName);
      String name = id.equals(WorldRecipeAssignments.activeWorldId()) && !WorldRecipeAssignments.activeWorldName().isBlank()
         ? WorldRecipeAssignments.activeWorldName()
         : details.name;
      return new RecipeWorldsScreen.LocalWorld(id, name, details.lastPlayed, details.description, this.loadIcon(directory, id));
   }

   private RecipeWorldsScreen.WorldDetails readWorldDetails(Path directory, String fallbackName) {
      try {
         NbtCompound data = NbtIo.readCompressed(directory.resolve("level.dat"), NbtSizeTracker.of(104857600L)).getCompound("Data");
         String name = data.contains("LevelName", 8) ? data.getString("LevelName") : fallbackName;
         long lastPlayed = data.contains("LastPlayed", 4) ? data.getLong("LastPlayed") : 0L;
         int gameType = data.contains("GameType", 3) ? data.getInt("GameType") : 0;

         String mode = switch (gameType) {
            case 1 -> Text.translatable("customrecipe.world.creative").getString();
            case 2 -> Text.translatable("customrecipe.world.adventure").getString();
            case 3 -> Text.translatable("customrecipe.world.spectator").getString();
            default -> Text.translatable("customrecipe.world.survival").getString();
         };
         String commands = data.contains("allowCommands", 1) && data.getBoolean("allowCommands")
            ? Text.translatable("customrecipe.world.commands").getString()
            : "";
         NbtCompound versionData = data.contains("Version", 10) ? data.getCompound("Version") : new NbtCompound();
         String version = versionData.contains("Name", 8) ? versionData.getString("Name") : Text.translatable("customrecipe.world.unknown_version").getString();
         String played = lastPlayed > 0L ? name + " (" + LAST_PLAYED_FORMAT.format(Instant.ofEpochMilli(lastPlayed)) + ")" : name;
         return new RecipeWorldsScreen.WorldDetails(
            name, played, mode + commands + Text.translatable("customrecipe.world.version", new Object[]{version}).getString()
         );
      } catch (RuntimeException | IOException var13) {
         return new RecipeWorldsScreen.WorldDetails(fallbackName, fallbackName, Text.translatable("customrecipe.world.local").getString());
      }
   }

   private RecipeWorldsScreen.WorldIcon loadIcon(Path directory, String id) {
      Path iconPath = directory.resolve("icon.png");
      if (!Files.isRegularFile(iconPath)) {
         return null;
      } else {
         try {
            RecipeWorldsScreen.WorldIcon var8;
            try (InputStream input = Files.newInputStream(iconPath)) {
               NativeImage image = NativeImage.read(input);
               Identifier textureId = Identifier.of("customrecipe", "dynamic/recipe_worlds/" + id.replaceAll("[^a-z0-9_./-]", "_"));
               RecipeWorldsScreen.WorldIcon icon = new RecipeWorldsScreen.WorldIcon(textureId, image.getWidth(), image.getHeight());
               this.client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
               var8 = icon;
            }

            return var8;
         } catch (RuntimeException | IOException var11) {
            return null;
         }
      }
   }

   private CustomRecipeEntry recipe() {
      return this.config.recipes.get(this.recipeIndex);
   }

   private List<RecipeWorldsScreen.LocalWorld> visibleWorlds() {
      String query = this.search == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);
      return query.isEmpty() ? this.worlds : this.worlds.stream().filter(world -> world.name.toLowerCase(Locale.ROOT).contains(query)).toList();
   }

   private int rowsTop() {
      return 52;
   }

   private int rowsBottom() {
      return this.height - 32;
   }

   private int visibleRows() {
      return Math.max(1, (this.rowsBottom() - this.rowsTop()) / 56);
   }

   private int shownRows() {
      return Math.min(this.visibleRows(), this.visibleWorlds().size());
   }

   private int listX() {
      return this.width / 2 - 160;
   }

   private int listW() {
      return 320;
   }

   private int maxScroll() {
      return Math.max(0, this.visibleWorlds().size() - this.visibleRows());
   }

   private boolean hasScrollBar() {
      return this.maxScroll() > 0;
   }

   private int scrollBarX() {
      return this.listX() + this.listW() + 4;
   }

   private int checkboxX() {
      return this.listX() + this.listW() - 24;
   }

   private void toggleCurrentRecipe(RecipeWorldsScreen.LocalWorld world) {
      boolean enabled = this.recipe().world_ids == null || !this.recipe().world_ids.contains(world.id);
      this.config.setWorldAssigned(this.recipe(), world.id, world.name, enabled);
      this.config.persistLocalWorldAssignments();
   }

   private void setScrollFromMouse(double mouseY) {
      int max = this.maxScroll();
      if (max != 0) {
         int trackHeight = this.rowsBottom() - this.rowsTop();
         int thumbHeight = Math.max(12, trackHeight * this.visibleRows() / this.visibleWorlds().size());
         int travel = trackHeight - thumbHeight;
         this.scroll = Math.max(0, Math.min(max, (int)Math.round((mouseY - this.rowsTop() - thumbHeight / 2.0) * max / travel)));
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.hasScrollBar() && mouseX >= this.scrollBarX() - 2 && mouseX <= this.scrollBarX() + 8 && mouseY >= this.rowsTop() && mouseY < this.rowsBottom()) {
         this.setScrollFromMouse(mouseY);
         return true;
      } else {
         List<RecipeWorldsScreen.LocalWorld> shown = this.visibleWorlds();

         for (int row = 0; row < this.shownRows(); row++) {
            int index = this.scroll + row;
            if (index >= shown.size()) {
               break;
            }

            int y = this.rowsTop() + row * 56;
            if (!(mouseX < this.listX()) && !(mouseX >= this.listX() + this.listW()) && !(mouseY < y) && !(mouseY >= y + 56)) {
               RecipeWorldsScreen.LocalWorld world = shown.get(index);
               if (mouseX >= this.checkboxX() - 2) {
                  this.toggleCurrentRecipe(world);
                  return true;
               }

               this.client.setScreen(new WorldRecipesScreen(this.config, this, world.id, world.name));
               return true;
            }
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!(mouseY < this.rowsTop()) && !(mouseY >= this.rowsBottom())) {
         this.scroll = Math.max(0, Math.min(this.scroll - (int)verticalAmount, this.maxScroll()));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context, mouseX, mouseY, delta);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 16777215);
      super.render(context, mouseX, mouseY, delta);
      List<RecipeWorldsScreen.LocalWorld> shown = this.visibleWorlds();

      for (int row = 0; row < this.shownRows(); row++) {
         int index = this.scroll + row;
         if (index >= shown.size()) {
            break;
         }

         RecipeWorldsScreen.LocalWorld world = shown.get(index);
         int y = this.rowsTop() + row * 56;
         boolean hovered = mouseX >= this.listX() && mouseX < this.listX() + this.listW() && mouseY >= y && mouseY < y + 56;
         context.fill(this.listX(), y, this.listX() + this.listW(), y + 56 - 1, hovered ? 1999854146 : 1712328720);
         this.drawBox(context, this.listX(), y, this.listW(), 56, hovered ? -1 : -11513776);
         if (world.icon != null) {
            context.drawTexture(world.icon.id, this.listX() + 4, y + 12, 0.0F, 0.0F, 32, 32, world.icon.width, world.icon.height);
         } else {
            context.drawItem(new ItemStack(Items.GRASS_BLOCK), this.listX() + 12, y + 20);
         }

         context.drawText(this.textRenderer, world.name, this.listX() + 42, y + 8, -1, true);
         context.drawText(this.textRenderer, world.lastPlayed, this.listX() + 42, y + 20, -5592406, false);
         context.drawText(this.textRenderer, world.description, this.listX() + 42, y + 32, -5592406, false);
         boolean enabled = this.recipe().world_ids != null && this.recipe().world_ids.contains(world.id);
         CustomRecipeSprites.draw(context, CustomRecipeSprites.SLOT, this.checkboxX() - 1, y + 17, 20, 20);
         CustomRecipeSprites.draw(context, enabled ? CustomRecipeSprites.ACCEPT : CustomRecipeSprites.REJECT, this.checkboxX(), y + 18, 18, 18);
      }

      if (this.hasScrollBar()) {
         this.drawScrollBar(context);
      }

      if (shown.isEmpty()) {
         context.drawCenteredTextWithShadow(
            this.textRenderer, Text.translatable("customrecipe.screen.no_worlds"), this.width / 2, this.rowsTop() + 12, 11184810
         );
      }
   }

   private void drawScrollBar(DrawContext context) {
      int trackTop = this.rowsTop();
      int trackHeight = this.rowsBottom() - trackTop;
      int thumbHeight = Math.max(12, trackHeight * this.visibleRows() / this.visibleWorlds().size());
      int travel = trackHeight - thumbHeight;
      int thumbY = trackTop + (this.maxScroll() == 0 ? 0 : travel * this.scroll / this.maxScroll());
      context.fill(this.scrollBarX(), trackTop, this.scrollBarX() + 6, this.rowsBottom(), -2013265920);
      context.fill(this.scrollBarX(), thumbY, this.scrollBarX() + 6, thumbY + thumbHeight, -5592406);
      this.drawBox(context, this.scrollBarX(), thumbY, 6, thumbHeight, -1118482);
   }

   private void drawBox(DrawContext context, int x, int y, int w, int h, int color) {
      context.drawHorizontalLine(x, x + w - 1, y, color);
      context.drawHorizontalLine(x, x + w - 1, y + h - 1, color);
      context.drawVerticalLine(x, y, y + h - 1, color);
      context.drawVerticalLine(x + w - 1, y, y + h - 1, color);
   }

   public boolean shouldPause() {
      return true;
   }

   public void close() {
      this.client.setScreen(this.returnScreen);
   }

   private record LocalWorld(String id, String name, String lastPlayed, String description, RecipeWorldsScreen.WorldIcon icon) {
   }

   private record WorldDetails(String name, String lastPlayed, String description) {
   }

   private record WorldIcon(Identifier id, int width, int height) {
   }
}
