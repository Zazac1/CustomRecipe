package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeEntry;
import fr.zazac1.customrecipe.ModConfig;
import fr.zazac1.customrecipe.RecipeVariantRule;
import fr.zazac1.customrecipe.RecipeTarget;
import fr.zazac1.customrecipe.GlobalRecipeTarget;
import fr.zazac1.customrecipe.WorldRecipeConfig;
import fr.zazac1.customrecipe.WorldRecipeTarget;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
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
    private final RecipeTarget target;
    private final WorldRecipeConfig targetConfig;
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
        this(parent, config, screenTitle, serverManaged, saveAction, null);
    }

    private ConfigScreen(Screen parent, ModConfig config, String screenTitle,
                         boolean serverManaged, Consumer<ModConfig> saveAction, RecipeTarget requestedTarget) {
        super(Text.literal(screenTitle));
        this.parent = parent;
        this.baseConfig = config;
        this.saveAction = saveAction;
        this.serverManaged = serverManaged;
        this.target = requestedTarget == null ? defaultTarget(config) : requestedTarget;
        this.targetConfig = this.target.resolve(config);
        this.recipes  = new ArrayList<>(targetConfig.custom_recipes);
        this.disabled = new ArrayList<>(targetConfig.disabled_builtin);
        this.knownByDefaultBuiltin = new ArrayList<>(targetConfig.known_by_default_builtin);
        this.disabledRecipes = new ArrayList<>(targetConfig.disabled_recipes);
        this.disabledRecipeVariants = new ArrayList<>(targetConfig.disabled_recipe_variants);
    }

    private RecipeTarget defaultTarget(ModConfig config) {
        String id = serverManaged ? config.editor_world_id : WorldRecipeAssignments.activeWorldId();
        String name = serverManaged ? config.editor_world_name : WorldRecipeAssignments.activeWorldName();
        return id == null || id.isBlank() ? GlobalRecipeTarget.INSTANCE : new WorldRecipeTarget(id, name);
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
                Text.literal(target.isWorld() ? "My Recipes" : "Global Library"),
                b -> client.setScreen(new CustomRecipesScreen(this))
        ).dimensions(cx, cy, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Built-in Recipes"),
                b -> client.setScreen(new BuiltinRecipesScreen(this))
        ).dimensions(cx, cy + 24, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Create a Recipe"),
                    b -> client.setScreen(new RecipeBuilderScreen120(this))
        ).dimensions(cx, cy + 48, btnW, btnH).build());

        if (target.isWorld()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Global Library"),
                    b -> client.setScreen(new CustomRecipesScreen(this, true)))
                    .dimensions(cx, cy + 72, btnW, btnH).build());
        }

        if (serverManaged) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Default Recipes"),
                    b -> client.setScreen(new VanillaRecipesScreen120(this))
            ).dimensions(cx, cy + (target.isWorld() ? 96 : 72), btnW, btnH).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Manual Edit"),
                    b -> client.setScreen(new ServerJsonScreen(this, currentConfig()))
            ).dimensions(cx, cy + (target.isWorld() ? 120 : 96), btnW, btnH).build());
        } else {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Default Recipes"),
                    b -> client.setScreen(new VanillaRecipesScreen120(this, true))
            ).dimensions(cx, cy + (target.isWorld() ? 96 : 72), btnW, btnH).build());
        }

        int saveY = cy + (serverManaged ? (target.isWorld() ? 152 : 128) : (target.isWorld() ? 128 : 104));
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
                .dimensions(cx, saveY, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export"), b -> exportAll())
                .dimensions(cx, saveY + 24, 98, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b -> importAll())
                .dimensions(cx + 102, saveY + 24, 98, btnH).build());
    }

    void save() {
        saveAction.accept(currentConfig());
        if (!serverManaged && client.getServer() != null) {
            var server = client.getServer();
            server.execute(() -> server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(), "reload"));
        }
        client.setScreen(parent);
    }

    private void exportAll() {
        WindowsFileDialogs.saveJson(exportFileName(), path -> {
            try { ConfigLoader.exportTo(currentConfig(), path); } catch (java.io.IOException ignored) {}
        }, error -> {});
    }

    private String exportFileName() {
        String name = target.isWorld() ? target.displayName().replaceFirst("^Current\\s+", "") : "global-library";
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim().replaceAll("\\s+", "_");
        return "customrecipe-backup-" + (safe.isBlank() ? "world" : safe) + ".json";
    }

    private void importAll() {
        WindowsFileDialogs.openJson(path -> {
            try {
                ModConfig imported = ConfigLoader.importFrom(path);
                WorldRecipeConfig importedTarget = target.resolve(imported);
                int recipeCount = importedTarget.custom_recipes.size();
                int vanillaCount = importedTarget.disabled_builtin.size() + importedTarget.disabled_recipes.size()
                        + importedTarget.disabled_recipe_variants.size() + importedTarget.known_by_default_builtin.size()
                        + importedTarget.hidden_quick_add_builtin.size();
                client.setScreen(new ImportConfigurationScreen(this, imported, recipeCount, vanillaCount));
            } catch (java.io.IOException ignored) {}
        }, error -> {});
    }

    void confirmImportedConfiguration(ModConfig imported) {
        client.setScreen(new ConfigScreen(parent, imported, title.getString(), serverManaged, saveAction, target));
    }

    boolean isServerManaged() {
        return serverManaged;
    }

    ModConfig currentConfig() {
        targetConfig.custom_recipes = new ArrayList<>(recipes);
        targetConfig.disabled_builtin = new ArrayList<>(disabled);
        targetConfig.known_by_default_builtin = new ArrayList<>(knownByDefaultBuiltin);
        targetConfig.disabled_recipes = new ArrayList<>(disabledRecipes);
        targetConfig.disabled_recipe_variants = new ArrayList<>(disabledRecipeVariants);
        return baseConfig;
    }

    void replaceConfig(ModConfig config) {
        recipes.clear();
        WorldRecipeConfig replacement = target.resolve(config);
        recipes.addAll(replacement.custom_recipes);
        disabled.clear();
        disabled.addAll(replacement.disabled_builtin);
        knownByDefaultBuiltin.clear();
        knownByDefaultBuiltin.addAll(replacement.known_by_default_builtin);
        disabledRecipes.clear();
        disabledRecipes.addAll(replacement.disabled_recipes);
        disabledRecipeVariants.clear();
        disabledRecipeVariants.addAll(replacement.disabled_recipe_variants);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    RecipeTarget target() { return target; }

    int addAllFromGlobalLibrary() {
        if (!target.isWorld()) return 0;
        ConfigLoader.LibraryImportResult result = ConfigLoader.importGlobalLibraryToWorld(
                baseConfig, target.id(), target.displayName());
        recipes.clear();
        recipes.addAll(targetConfig.custom_recipes);
        return result.added();
    }
}
