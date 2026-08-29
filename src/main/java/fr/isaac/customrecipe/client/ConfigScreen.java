package fr.isaac.customrecipe.client;

import fr.isaac.customrecipe.ConfigLoader;
import fr.isaac.customrecipe.CustomRecipeEntry;
import fr.isaac.customrecipe.ModConfig;
import fr.isaac.customrecipe.RecipeVariantRule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
    final List<String> knownByDefaultBuiltin;
    final List<String> disabledRecipes;
    final List<RecipeVariantRule> disabledRecipeVariants;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigLoader.get(), "Custom Recipe", false, ConfigLoader::saveAndInvalidate);
    }

    /** Creates the OP-only editor using a config received from a server. */
    public ConfigScreen(Screen parent, ModConfig config, String screenTitle,
                        boolean serverManaged, Consumer<ModConfig> saveAction) {
        super(Component.literal(screenTitle));
        this.parent = parent;
        this.baseConfig = config;
        this.saveAction = saveAction;
        this.serverManaged = serverManaged;
        this.recipes  = new ArrayList<>(config.custom_recipes);
        this.disabled = new ArrayList<>(config.disabled_builtin);
        this.knownByDefaultBuiltin = new ArrayList<>(config.known_by_default_builtin);
        this.disabledRecipes = new ArrayList<>(config.disabled_recipes);
        this.disabledRecipeVariants = new ArrayList<>(config.disabled_recipe_variants);
    }

    @Override
    protected void init() {
        // Premier lancement : afficher le guide de bienvenue
        if (!serverManaged && !welcomeShown && !ConfigLoader.get().seen_welcome) {
            welcomeShown = true;
            final WelcomeScreen ws = new WelcomeScreen(this); // ConfigScreen est un Screen
            minecraft.execute(() -> minecraft.gui.setScreen(ws));
        }

        int btnW = 200, btnH = 20;
        int cx = width / 2 - btnW / 2;
        int cy = height / 2 - 40;

        // Title
        int titleX = Math.max(8, (width - font.width(title.getString())) / 2);
        addRenderableWidget(new MultiLineTextWidget(titleX, cy - 26, title, font));

        addRenderableWidget(Button.builder(
                Component.literal("My Recipes"),
                b -> minecraft.gui.setScreen(new CustomRecipesScreen(this))
        ).bounds(cx, cy, btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal("Built-in Recipes"),
                b -> minecraft.gui.setScreen(new BuiltinRecipesScreen(this))
        ).bounds(cx, cy + 24, btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal("Create a Recipe"),
                b -> minecraft.gui.setScreen(new RecipeBuilderScreen(this))
        ).bounds(cx, cy + 48, btnW, btnH).build());

        if (serverManaged) {
            addRenderableWidget(Button.builder(
                    Component.literal("Default Recipes"),
                    b -> minecraft.gui.setScreen(new VanillaRecipesScreen(this))
            ).bounds(cx, cy + 72, btnW, btnH).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Manual Edit"),
                    b -> minecraft.gui.setScreen(new ServerJsonScreen(this, currentConfig()))
            ).bounds(cx, cy + 96, btnW, btnH).build());
        } else {
            addRenderableWidget(Button.builder(
                    Component.literal("Default Recipes"),
                    b -> minecraft.gui.setScreen(new VanillaRecipesScreen(this, true))
            ).bounds(cx, cy + 72, btnW, btnH).build());
        }

        addRenderableWidget(Button.builder(
                Component.literal("Save"),
                b -> save()
        ).bounds(cx, cy + (serverManaged ? 128 : 104), btnW, btnH).build());
    }

    void save() {
        saveAction.accept(currentConfig());
        if (!serverManaged) LocalRecipeReload.afterSave(minecraft);
        minecraft.gui.setScreen(parent);
    }

    boolean isServerManaged() {
        return serverManaged;
    }

    ModConfig currentConfig() {
        baseConfig.custom_recipes = new ArrayList<>(recipes);
        baseConfig.disabled_builtin = new ArrayList<>(disabled);
        baseConfig.known_by_default_builtin = new ArrayList<>(knownByDefaultBuiltin);
        baseConfig.disabled_recipes = new ArrayList<>(disabledRecipes);
        baseConfig.disabled_recipe_variants = new ArrayList<>(disabledRecipeVariants);
        return baseConfig;
    }

    void replaceConfig(ModConfig config) {
        recipes.clear();
        recipes.addAll(config.custom_recipes);
        disabled.clear();
        disabled.addAll(config.disabled_builtin);
        knownByDefaultBuiltin.clear();
        knownByDefaultBuiltin.addAll(config.known_by_default_builtin);
        disabledRecipes.clear();
        disabledRecipes.addAll(config.disabled_recipes);
        disabledRecipeVariants.clear();
        disabledRecipeVariants.addAll(config.disabled_recipe_variants);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
