package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Advanced raw server JSON editor. */
@Environment(EnvType.CLIENT)
public class ServerJsonScreen extends Screen {
    private static final int MAX_JSON_CHARS = 30_000;

    private final ConfigScreen parent;
    private final String initialJson;
    private EditBox jsonField;
    private String error = "";

    public ServerJsonScreen(ConfigScreen parent, ModConfig config) {
        super(Component.literal("Manual Edit"));
        this.parent = parent;
        this.initialJson = ConfigLoader.toJson(config).replaceAll("\\s+", " ");
    }

    @Override
    protected void init() {
        int margin = 14;
        addRenderableWidget(new MultiLineTextWidget(margin, 12,
                Component.literal("WARNING: Advanced editor. Invalid or incompatible JSON can erase recipe settings. Use Save only after checking it."),
                font));

        jsonField = addRenderableWidget(new EditBox(font, margin, 40,
                width - margin * 2, 20, Component.literal("Server config JSON")));
        jsonField.setMaxLength(MAX_JSON_CHARS);
        jsonField.setValue(initialJson);
        setFocused(jsonField);

        addRenderableWidget(Button.builder(Component.literal("Apply JSON"), b -> apply())
                .bounds(width / 2 - 102, height - 28, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 + 4, height - 28, 98, 20).build());
    }

    private void apply() {
        ModConfig config = ConfigLoader.fromJson(jsonField.getValue());
        if (config == null) {
            error = "Invalid JSON";
            return;
        }
        parent.replaceConfig(config);
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (!error.isEmpty()) ctx.text(font, error, 14, 66, 0xFF5555, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
