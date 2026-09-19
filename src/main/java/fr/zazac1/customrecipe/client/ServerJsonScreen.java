package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

/** Advanced raw server JSON editor. */
@Environment(EnvType.CLIENT)
public class ServerJsonScreen extends Screen {
    private static final int MAX_JSON_CHARS = 30_000;

    private final ConfigScreen parent;
    private final String initialJson;
    private TextFieldWidget jsonField;
    private String error = "";

    public ServerJsonScreen(ConfigScreen parent, ModConfig config) {
        super(Text.literal("Manual Edit"));
        this.parent = parent;
        // Keep Gson's pretty-printed layout: this is an editor, not a single-line field.
        this.initialJson = ConfigLoader.toJson(config);
    }

    @Override
    protected void init() {
        addDrawable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, client, parent.target(),
                parent.target().isWorld() ? parent.target().displayName() : "Global Library"));
        int margin = 14;
        addDrawableChild(new MultilineTextWidget(margin + 31, 12,
                Text.literal("WARNING: Advanced editor. Invalid or incompatible JSON can erase recipe settings. Use Save only after checking it."),
                textRenderer));

        int editorTop = 44;
        jsonField = addDrawableChild(new TextFieldWidget(textRenderer, margin, editorTop,
                width - margin * 2, 20, Text.literal("Server config JSON")));
        jsonField.setPlaceholder(Text.literal("{ \"custom_recipes\": [] }"));
        jsonField.setMaxLength(MAX_JSON_CHARS);
        jsonField.setText(initialJson);
        setFocused(jsonField);

        addDrawableChild(ButtonWidget.builder(Text.literal("    Apply JSON"), b -> apply())
                .dimensions(width / 2 - 102, height - 28, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("   Cancel"), b -> client.setScreen(parent))
                .dimensions(width / 2 + 4, height - 28, 98, 20).build());
        addDrawable((ctx, mx, my, d) -> {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ACCEPT, width / 2 - 98, height - 27, 18, 18);
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, width / 2 + 8, height - 27, 18, 18);
        });
    }

    private void apply() {
        ModConfig config = ConfigLoader.fromJson(jsonField.getText());
        if (config == null) {
            error = "Invalid JSON";
            return;
        }
        parent.replaceConfig(config);
        client.setScreen(parent);
    }

    @Override
    public void close() {
        if (!initialJson.equals(jsonField.getText())) {
            client.setScreen(new SaveChangesScreen(this, this::apply, () -> client.setScreen(parent)));
        } else {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
        if (!error.isEmpty()) ctx.drawText(textRenderer, error, 14, height - 44, 0xFF5555, false);
    }

    @Override public boolean shouldPause() { return true; }
}
