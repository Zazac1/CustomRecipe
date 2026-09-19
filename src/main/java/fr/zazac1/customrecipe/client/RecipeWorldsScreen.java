package fr.zazac1.customrecipe.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Compatibility entry point for the older 1.21.1 world selector. */
@Environment(EnvType.CLIENT)
public final class RecipeWorldsScreen extends Screen {
    private final CustomRecipesScreen returnScreen;

    RecipeWorldsScreen(ConfigScreen config, CustomRecipesScreen returnScreen, int recipeIndex) {
        super(Text.translatable("customrecipe.screen.select_target"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.back"), b -> client.setScreen(returnScreen))
                .dimensions(width / 2 - 55, height / 2 - 10, 110, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 36, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return true; }
    @Override public void close() { client.setScreen(returnScreen); }
}