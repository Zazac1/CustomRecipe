package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.CustomRecipeEntry;
import fr.isaac.customrecipe.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ConfigScreen extends Screen {

    private final Screen parent;

    /** Shared state — modified by sub-screens, saved on Done. */
    final List<CustomRecipeEntry> recipes;
    final List<String> disabled;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Custom Recipe"));
        this.parent = parent;
        ModConfig cfg = ConfigLoader.get();
        this.recipes  = new ArrayList<>(cfg.custom_recipes);
        this.disabled = new ArrayList<>(cfg.disabled_builtin);
    }

    @Override
    protected void init() {
        int btnW = 200, btnH = 20;
        int cx = width / 2 - btnW / 2;
        int cy = height / 2 - 40;

        // Title
        int titleX = Math.max(8, (width - textRenderer.getWidth(title.getString())) / 2);
        addDrawableChild(new MultilineTextWidget(titleX, cy - 26, title, textRenderer));

        addDrawableChild(ButtonWidget.builder(
                Text.literal("My Recipes"),
                b -> client.setScreen(new CustomRecipesScreen(this))
        ).dimensions(cx, cy, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Built-in Recipes"),
                b -> client.setScreen(new BuiltinRecipesScreen(this))
        ).dimensions(cx, cy + 24, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Create a Recipe"),
                b -> client.setScreen(new RecipeBuilderScreen(this))
        ).dimensions(cx, cy + 48, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                b -> save()
        ).dimensions(cx, cy + 80, btnW, btnH).build());
    }

    void save() {
        ModConfig cfg = ConfigLoader.get();
        cfg.custom_recipes  = new ArrayList<>(recipes);
        cfg.disabled_builtin = new ArrayList<>(disabled);
        ConfigLoader.saveAndInvalidate(cfg);
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
