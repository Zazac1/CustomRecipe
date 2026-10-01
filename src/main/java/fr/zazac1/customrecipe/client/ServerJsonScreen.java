package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.network.chat.Component;

/** Advanced raw server JSON editor. */
public class ServerJsonScreen extends Screen {
    private static final int MAX_JSON_CHARS = 30_000;

    private final ConfigScreen parent;
    private final String initialJson;
    private EditBox jsonField;
    private String error = "";

    public ServerJsonScreen(ConfigScreen parent, ModConfig config) {
        super(Component.literal("Manual Edit"));
        this.parent = parent;
        // Keep Gson's pretty-printed layout: this is an editor, not a single-line field.
        this.initialJson = ConfigLoader.toJson(config);
    }

    @Override
    protected void init() {
        addRenderableOnly((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, parent.target(), parent.targetLabel()));
        int margin = 14;
        addRenderableWidget(new MultiLineTextWidget(margin + 31, 12,
                Component.literal("WARNING: Advanced editor. Invalid or incompatible JSON can erase recipe settings. Use Save only after checking it."),
                font));

        int editorTop = 44;
        int editorHeight = Math.max(70, height - editorTop - 52);
        jsonField = addRenderableWidget(new EditBox(font, margin, editorTop,
                width - margin * 2, editorHeight, Component.literal("Server config JSON")));
        jsonField.setHint(Component.literal("{\n  \"custom_recipes\": []\n}"));
        jsonField.setMaxLength(MAX_JSON_CHARS);
        jsonField.setValue(initialJson);
        setFocused(jsonField);

        addRenderableWidget(Button.builder(Component.literal("    Apply JSON"), b -> apply())
                .bounds(width / 2 - 102, height - 28, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("   Cancel"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 + 4, height - 28, 98, 20).build());
        addRenderableOnly((ctx, mx, my, d) -> {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.ACCEPT, width / 2 - 98, height - 27, 18, 18);
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, width / 2 + 8, height - 27, 18, 18);
        });
    }

    private void apply() {
        ModConfig config = ConfigLoader.fromJson(jsonField.getValue());
        if (config == null) {
            error = "Invalid JSON";
            return;
        }
        parent.replaceConfig(config);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (!initialJson.equals(jsonField.getValue())) {
            minecraft.setScreen(new SaveChangesScreen(this, this::apply, () -> minecraft.setScreen(parent)));
        } else {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (!error.isEmpty()) ctx.drawString(font, error, 14, height - 44, 0xFF5555, false);
    }

    @Override public boolean isPauseScreen() { return true; }
}
