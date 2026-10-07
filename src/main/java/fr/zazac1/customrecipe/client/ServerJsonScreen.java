package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ServerJsonScreen extends Screen {
   private static final int MAX_JSON_CHARS = 30000;
   private final ConfigScreen parent;
   private final String initialJson;
   private EditBoxWidget jsonField;
   private String error = "";

   public ServerJsonScreen(ConfigScreen parent, ModConfig config) {
      super(Text.literal("Manual Edit"));
      this.parent = parent;
      this.initialJson = ConfigLoader.toJson(config);
   }

   protected void init() {
      this.addDrawable(
         (ctx, mx, my, d) -> RecipeTargetBadge.draw(
            ctx, this.client, this.parent.target(), this.parent.target().isWorld() ? this.parent.target().displayName() : "Global Library"
         )
      );
      int margin = 14;
      this.addDrawableChild(
         new MultilineTextWidget(
            margin + 31,
            12,
            Text.literal("WARNING: Advanced editor. Invalid or incompatible JSON can erase recipe settings. Use Save only after checking it."),
            this.textRenderer
         )
      );
      int editorTop = 44;
      int editorHeight = Math.max(70, this.height - editorTop - 52);
      this.jsonField = (EditBoxWidget)this.addDrawableChild(
         new EditBoxWidget(
            this.textRenderer,
            margin,
            editorTop,
            this.width - margin * 2,
            editorHeight,
            Text.literal("Server config JSON"),
            Text.literal("{\n  \"custom_recipes\": []\n}")
         )
      );
      this.jsonField.setMaxLength(30000);
      this.jsonField.setText(this.initialJson);
      this.setFocused(this.jsonField);
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("    Apply JSON"), b -> this.apply()).dimensions(this.width / 2 - 102, this.height - 28, 98, 20).build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("   Cancel"), b -> this.client.setScreen(this.parent))
            .dimensions(this.width / 2 + 4, this.height - 28, 98, 20)
            .build()
      );
      this.addDrawable((ctx, mx, my, d) -> {
         CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ACCEPT, this.width / 2 - 98, this.height - 27, 18, 18);
         CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, this.width / 2 + 8, this.height - 27, 18, 18);
      });
   }

   private void apply() {
      ModConfig config = ConfigLoader.fromJson(this.jsonField.getText());
      if (config == null) {
         this.error = "Invalid JSON";
      } else {
         this.parent.replaceConfig(config);
         this.client.setScreen(this.parent);
      }
   }

   public void close() {
      if (!this.initialJson.equals(this.jsonField.getText())) {
         this.client.setScreen(new SaveChangesScreen(this, this::apply, () -> this.client.setScreen(this.parent)));
      } else {
         this.client.setScreen(this.parent);
      }
   }

   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      ctx.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      super.render(ctx, mouseX, mouseY, delta);
      if (!this.error.isEmpty()) {
         ctx.drawText(this.textRenderer, this.error, 14, this.height - 44, 16733525, false);
      }
   }

   public boolean shouldPause() {
      return true;
   }
}

