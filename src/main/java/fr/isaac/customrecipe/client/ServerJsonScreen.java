package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
        this.initialJson = ConfigLoader.toJson(config).replaceAll("\\s+", " ");
    }

    @Override
    protected void init() {
        int margin = 14;
        addDrawableChild(new MultilineTextWidget(margin, 12,
                Text.literal("WARNING: Advanced editor. Invalid or incompatible JSON can erase recipe settings. Use Save only after checking it."),
                textRenderer));

        jsonField = addDrawableChild(new TextFieldWidget(textRenderer, margin, 40,
                width - margin * 2, 20, Text.literal("Server config JSON")));
        jsonField.setMaxLength(MAX_JSON_CHARS);
        jsonField.setText(initialJson);
        setFocused(jsonField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Apply JSON"), b -> apply())
                .dimensions(width / 2 - 102, height - 28, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent))
                .dimensions(width / 2 + 4, height - 28, 98, 20).build());
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
        if (!error.isEmpty()) ctx.drawText(textRenderer, error, 14, 66, 0xFF5555, false);
    }

    @Override public boolean shouldPause() { return false; }
}
