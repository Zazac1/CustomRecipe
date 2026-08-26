package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.CustomRecipeEntry;
import fr.isaac.customrecipe.ModConfig;
import fr.isaac.customrecipe.RecipeVariantRule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ConfigScreen extends Screen {

    private final Screen parent;
    private final ModConfig baseConfig;
    private final Consumer<ModConfig> saveAction;
    private final boolean serverManaged;
    private boolean welcomeShown = false; // évite la boucle infinie si l'utilisateur revient

    /** Shared state — modified by sub-screens, saved on Save. */
    final List<CustomRecipeEntry> recipes;
    final List<String> disabled;
    final List<String> disabledRecipes;
    final List<RecipeVariantRule> disabledRecipeVariants;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigLoader.get(), "Custom Recipe", false, ConfigLoader::saveAndInvalidate);
    }

    /** Creates the OP-only editor using a config received from a server. */
    public ConfigScreen(Screen parent, ModConfig config, String screenTitle,
                        boolean serverManaged, Consumer<ModConfig> saveAction) {
        super(Text.literal(screenTitle));
        this.parent = parent;
        this.baseConfig = config;
        this.saveAction = saveAction;
        this.serverManaged = serverManaged;
        this.recipes  = new ArrayList<>(config.custom_recipes);
        this.disabled = new ArrayList<>(config.disabled_builtin);
        this.disabledRecipes = new ArrayList<>(config.disabled_recipes);
        this.disabledRecipeVariants = new ArrayList<>(config.disabled_recipe_variants);
    }

    @Override
    protected void init() {
        // Premier lancement : afficher le guide de bienvenue
        if (!serverManaged && !welcomeShown && !ConfigLoader.get().seen_welcome) {
            welcomeShown = true;
            final WelcomeScreen ws = new WelcomeScreen(this); // ConfigScreen est un Screen
            client.execute(() -> client.setScreen(ws));
        }

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

        if (serverManaged) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Vanilla Crafting Recipes"),
                    b -> client.setScreen(new VanillaRecipesScreen(this))
            ).dimensions(cx, cy + 72, btnW, btnH).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Manual Edit"),
                    b -> client.setScreen(new ServerJsonScreen(this, currentConfig()))
            ).dimensions(cx, cy + 96, btnW, btnH).build());
        } else {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Vanilla Crafting Recipes"),
                    b -> client.setScreen(new VanillaRecipesScreen(this, true))
            ).dimensions(cx, cy + 72, btnW, btnH).build());
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                b -> save()
        ).dimensions(cx, cy + (serverManaged ? 128 : 104), btnW, btnH).build());
    }

    void save() {
        saveAction.accept(currentConfig());
        client.setScreen(parent);
    }

    ModConfig currentConfig() {
        baseConfig.custom_recipes = new ArrayList<>(recipes);
        baseConfig.disabled_builtin = new ArrayList<>(disabled);
        baseConfig.disabled_recipes = new ArrayList<>(disabledRecipes);
        baseConfig.disabled_recipe_variants = new ArrayList<>(disabledRecipeVariants);
        return baseConfig;
    }

    void replaceConfig(ModConfig config) {
        recipes.clear();
        recipes.addAll(config.custom_recipes);
        disabled.clear();
        disabled.addAll(config.disabled_builtin);
        disabledRecipes.clear();
        disabledRecipes.addAll(config.disabled_recipes);
        disabledRecipeVariants.clear();
        disabledRecipeVariants.addAll(config.disabled_recipe_variants);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
