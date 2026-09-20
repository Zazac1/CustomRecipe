package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.ConfigLoader;
import fr.zazac1.customrecipe.CustomRecipeMod;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ConfigScreen extends Screen {

    private final Screen parent;
    private final ModConfig baseConfig;
    private final Consumer<ModConfig> saveAction;
    private final boolean serverManaged;
    /** Pause-menu entry points stay scoped to their currently loaded world. */
    private final boolean targetLocked;
    private final String editorWorldId;
    private final String editorWorldName;
    private final RecipeTarget target;
    private final WorldRecipeConfig targetConfig;
    private String initialConfigJson;

    /** Shared state — modified by sub-screens, saved on Save. */
    final List<CustomRecipeEntry> recipes;
    final List<String> disabled;
    final List<String> knownByDefaultBuiltin;
    final List<String> disabledRecipes;
    final List<RecipeVariantRule> disabledRecipeVariants;
    final List<String> hiddenQuickAddBuiltin;
    final List<CustomRecipeEntry> quickAddRecipes;

    public ConfigScreen(Screen parent) {
        this(parent, ConfigLoader.get(), "Custom Recipe", false, ConfigLoader::saveAndInvalidate, null, false);
    }

    public static ConfigScreen fromModMenu(Screen parent) {
        return new ConfigScreen(parent);
    }

    public static ConfigScreen fromPauseMenu(Screen parent) {
        return new ConfigScreen(parent, ConfigLoader.get(), "Custom Recipe", false,
                ConfigLoader::saveAndInvalidate, null, true);
    }

    /** Creates the OP-only editor using a config received from a server. */
    public ConfigScreen(Screen parent, ModConfig config, String screenTitle,
                        boolean serverManaged, Consumer<ModConfig> saveAction) {
        this(parent, config, screenTitle, serverManaged, saveAction, null, false);
    }

    private ConfigScreen(Screen parent, ModConfig config, String screenTitle,
                         boolean serverManaged, Consumer<ModConfig> saveAction, RecipeTarget requestedTarget,
                         boolean targetLocked) {
        super(Text.literal(screenTitle));
        this.parent = parent;
        this.baseConfig = config;
        this.saveAction = saveAction;
        this.serverManaged = serverManaged;
        this.targetLocked = targetLocked;
        this.editorWorldId = serverManaged
                ? (config.editor_world_id == null ? "" : config.editor_world_id)
                : WorldRecipeAssignments.activeWorldId();
        this.editorWorldName = serverManaged
                ? (config.editor_world_name == null ? "Current world" : config.editor_world_name)
                : WorldRecipeAssignments.activeWorldName();
        this.target = requestedTarget == null ? defaultTarget() : requestedTarget;
        this.targetConfig = this.target.resolve(config);
        this.recipes  = new ArrayList<>(targetConfig.custom_recipes);
        this.disabled = new ArrayList<>(targetConfig.disabled_builtin);
        this.knownByDefaultBuiltin = new ArrayList<>(targetConfig.known_by_default_builtin);
        this.disabledRecipes = new ArrayList<>(targetConfig.disabled_recipes);
        this.disabledRecipeVariants = new ArrayList<>(targetConfig.disabled_recipe_variants);
        this.hiddenQuickAddBuiltin = new ArrayList<>(targetConfig.hidden_quick_add_builtin);
        this.quickAddRecipes = new ArrayList<>(targetConfig.quick_add_recipes);
        this.initialConfigJson = ConfigLoader.toJson(baseConfig);
    }

    private RecipeTarget defaultTarget() {
        if (!editorWorldId.isBlank()) return new WorldRecipeTarget(editorWorldId,
                "Current " + editorWorldName);
        return GlobalRecipeTarget.INSTANCE;
    }

    @Override
    protected void init() {

        int buttonWidth = Math.min(260, width - 32);
        int buttonHeight = 28;
        int gap = 8;
        int x = width / 2 - buttonWidth / 2;
        int top = height / 2 - (buttonHeight * 5 + gap * 4) / 2;
        boolean hasWorldTarget = target.isWorld();

        addDrawable((ctx, mouseX, mouseY, delta) ->
                ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.home.title"), width / 2, top - 27, 0xFFFFFFFF));

        ButtonWidget selectWorld = ButtonWidget.builder(Text.empty(),
                b -> client.setScreen(new RecipeTargetSelectScreen(this)))
                .dimensions(x, top, buttonWidth, buttonHeight).build();
        selectWorld.active = !serverManaged && !targetLocked;
        if (targetLocked) selectWorld.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.translatable("customrecipe.home.current_world_locked")));
        addDrawableChild(selectWorld);
        addHomeButton(top, buttonWidth, Text.translatable("customrecipe.home.select_world").getString(),
                hasWorldTarget ? "(" + target.displayName() + ")" : Text.translatable("customrecipe.home.global").getString(), Items.MAP, selectWorld.active);

        int libraryY = top + buttonHeight + gap;
        addDrawableChild(ButtonWidget.builder(Text.empty(),
                // Dedicated-server editors keep a world target; Library must explicitly open
                // the server's reusable Global Library so its Import/Export is available.
                b -> client.setScreen(new CustomRecipesScreen(this, serverManaged)))
                .dimensions(x, libraryY, buttonWidth, buttonHeight).build());
        addHomeButton(libraryY, buttonWidth, Text.translatable("customrecipe.home.library").getString(), null, Items.BOOKSHELF, true);

        int createY = libraryY + buttonHeight + gap;
        addDrawableChild(ButtonWidget.builder(Text.empty(),
                b -> client.setScreen(new RecipeBuilderScreen(this)))
                .dimensions(x, createY, buttonWidth, buttonHeight).build());
        addHomeButton(createY, buttonWidth, Text.translatable("customrecipe.home.create").getString(), null, Items.CRAFTING_TABLE, true);

        int vanillaY = createY + buttonHeight + gap;
        ButtonWidget vanilla = ButtonWidget.builder(Text.empty(),
                b -> client.setScreen(new VanillaRecipesScreen(this, !serverManaged)))
                .dimensions(x, vanillaY, buttonWidth, buttonHeight).build();
        vanilla.active = hasWorldTarget || serverManaged;
        if (!vanilla.active) vanilla.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.translatable("customrecipe.home.vanilla.tooltip")));
        addDrawableChild(vanilla);
        addHomeButton(vanillaY, buttonWidth, Text.translatable("customrecipe.home.vanilla").getString(), null, Items.GRASS_BLOCK, vanilla.active);


        int saveY = vanillaY + buttonHeight + gap;
        addDrawableChild(ButtonWidget.builder(Text.empty(), b -> save())
                .dimensions(x, saveY, buttonWidth, buttonHeight).build());
        addHomeSaveButton(saveY, buttonWidth);
        addDrawableChild(ButtonWidget.builder(Text.literal("Export"), b -> exportAll()).dimensions(width / 2 - 104, saveY + 32, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b -> importAll()).dimensions(width / 2 + 4, saveY + 32, 100, 20).build());
    }

    /** Draws the large home entries while the normal ButtonWidget keeps hover/click behavior. */
    private void addHomeButton(int y, int buttonWidth, String label, String subtitle,
                               net.minecraft.item.Item icon, boolean active) {
        int x = width / 2 - buttonWidth / 2;
        addDrawable((ctx, mouseX, mouseY, delta) -> {
            // Text drawing uses the top of its 9 px glyph box, not its baseline.
            // Keep both one-line and two-line entries centered inside the 28 px button.
            int mainY = subtitle == null ? y + 10 : y + 4;
            int color = active ? 0xFFFFFFFF : 0xFF777777;
            ctx.drawCenteredTextWithShadow(textRenderer, label, width / 2, mainY, color);
            if (subtitle != null) {
                ctx.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, y + 15,
                        active ? 0xFFB8B8B8 : 0xFF666666);
            }

            int iconX = x + 10;
            int iconY = y + 6;
            if (label.equals(Text.translatable("customrecipe.home.select_world").getString())) {
                Identifier globe = Identifier.of(CustomRecipeMod.MOD_ID, "textures/gui/world_globe.png");
                ctx.drawTexture(globe, iconX + 1, y + 6,
                        0, 0, 16, 16, 16, 16);
            } else {
                ctx.drawItem(new ItemStack(icon), iconX + 2, iconY);
            }
        });
    }

    /** Save is explicit on the home screen for local, global and server-managed editors. */
    private void addHomeSaveButton(int y, int buttonWidth) {
        int x = width / 2 - buttonWidth / 2;
        String label = Text.translatable("customrecipe.button.save").getString();
        addDrawable((ctx, mouseX, mouseY, delta) -> {
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SAVE, x + 12, y + 6, 16, 16);
            ctx.drawCenteredTextWithShadow(textRenderer, label, width / 2, y + 10, 0xFFFFFFFF);
        });
    }

    /** Previous compact menu retained while Vanilla Recipes is moved to its future location. */
    private void initLegacy() {

        int btnW = 200, btnH = 20;
        int cx = width / 2 - btnW / 2;
        int cy = height / 2 - 20;

        addDrawable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, client, target,
                target.isWorld() ? target.displayName() : "Global Library"));

        // Title
        int titleX = Math.max(8, (width - textRenderer.getWidth(title.getString())) / 2);
        addDrawableChild(new MultilineTextWidget(titleX, cy - 52, title, textRenderer));

        boolean canChooseTarget = !serverManaged;
        ButtonWidget targetButton = ButtonWidget.builder(
                Text.literal("Target: " + target.displayName() + " ▼"),
                b -> {
                    if (canChooseTarget) client.setScreen(new RecipeTargetSelectScreen(this));
                }
        ).dimensions(cx, cy - 26, btnW, btnH).build();
        targetButton.active = canChooseTarget;
        addDrawableChild(targetButton);

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
                    Text.literal("Default Recipes"),
                    // This editor changes server recipes, so it only browses the server catalog.
                    b -> client.setScreen(new VanillaRecipesScreen(this))
            ).dimensions(cx, cy + 72, btnW, btnH).build());

            addDrawableChild(ButtonWidget.builder(
                    Text.literal("Manual Edit"),
                    b -> client.setScreen(new ServerJsonScreen(this, currentConfig()))
            ).dimensions(cx, cy + 96, btnW, btnH).build());
        } else {
            boolean hasWorldTarget = target.isWorld();
            ButtonWidget vanillaButton = ButtonWidget.builder(
                    Text.literal(hasWorldTarget ? "Default Recipes" : "Select a world for Default Recipes")
                            .withColor(hasWorldTarget ? 0xFFFFFF : 0x777777),
                    b -> client.setScreen(new VanillaRecipesScreen(this, true))
            ).dimensions(cx, cy + 72, btnW, btnH).build();
            vanillaButton.active = hasWorldTarget;
            if (!hasWorldTarget) vanillaButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                    Text.literal("Select a world to access its vanilla recipes.")));
            addDrawableChild(vanillaButton);
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                b -> save()
        ).dimensions(cx, cy + (serverManaged ? 128 : 104), btnW, btnH).build());
    }

    private void exportAll() {
        WindowsFileDialogs.saveJson(exportFileName(), path -> {
            try { ConfigLoader.exportTo(currentConfig(), path); } catch (java.io.IOException ignored) {}
        }, error -> {});
    }

    private String exportFileName() {
        String worldName = target.isWorld() ? target.displayName().replaceFirst("^Current\\s+", "") : "global-library";
        String safeName = worldName.replaceAll("[\\\\/:*?\"<>|]", "_").trim().replaceAll("\\s+", "_");
        return "customrecipe-backup-" + (safeName.isBlank() ? "world" : safeName) + ".json";
    }

    private void importAll() {
        WindowsFileDialogs.openJson(path -> {
            try {
                ModConfig imported = ConfigLoader.importFrom(path);
                WorldRecipeConfig importedTarget = target.resolve(imported);
                int recipeCount = importedTarget.custom_recipes.size();
                int vanillaCount = importedTarget.disabled_builtin.size()
                        + importedTarget.disabled_recipes.size()
                        + importedTarget.disabled_recipe_variants.size()
                        + importedTarget.known_by_default_builtin.size()
                        + importedTarget.hidden_quick_add_builtin.size();
                client.setScreen(new ImportConfigurationScreen(this, imported, recipeCount, vanillaCount));
            } catch (java.io.IOException ignored) {}
        }, error -> {});
    }

    void confirmImportedConfiguration(ModConfig imported) {
        client.setScreen(new ConfigScreen(parent, imported, title.getString(), serverManaged, saveAction, target, targetLocked));
    }
    void save() {
        saveAndReturn(parent);
    }

    /** Saves without skipping the screen that opened a sub-menu. */
    void saveAndReturn(Screen returnTo) {
        saveAction.accept(currentConfig());
        if (!serverManaged && client.getServer() != null) {
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("customrecipe.chat.applying"), false);
            }
            client.getServer().execute(() -> client.getServer().getCommandManager()
                    .executeWithPrefix(client.getServer().getCommandSource().withSilent(), "reload"));
        }
        client.setScreen(returnTo);
    }

    /** Dedicated servers receive staged changes only from the main Save action. */
    void saveFromSubmenu() {
        if (serverManaged) {
            currentConfig();
            client.setScreen(this);
            return;
        }
        saveAndReturn(this);
    }

    @Override
    public void close() {
        if (hasUnsavedChanges()) {
            client.setScreen(new SaveChangesScreen(this, this::save, this::discardAndClose));
        } else {
            client.setScreen(parent);
        }
    }

    private boolean hasUnsavedChanges() {
        return !initialConfigJson.equals(ConfigLoader.toJson(currentConfig()));
    }

    private void discardAndClose() {
        ModConfig original = ConfigLoader.fromJson(initialConfigJson);
        if (original != null) restoreConfig(original);
        client.setScreen(parent);
    }

    private void restoreConfig(ModConfig original) {
        baseConfig.recipe_target_version = original.recipe_target_version;
        baseConfig.global_library = original.global_library;
        baseConfig.world_configs = original.world_configs;
        baseConfig.world_names = original.world_names;
        baseConfig.editor_world_id = original.editor_world_id;
        baseConfig.editor_world_name = original.editor_world_name;
        baseConfig.disabled_builtin = original.disabled_builtin;
        baseConfig.known_by_default_builtin = original.known_by_default_builtin;
        baseConfig.disabled_recipes = original.disabled_recipes;
        baseConfig.disabled_recipe_variants = original.disabled_recipe_variants;
        baseConfig.custom_recipes = original.custom_recipes;
        baseConfig.shown_world_editor_tips = original.shown_world_editor_tips;
    }

    boolean isServerManaged() {
        return serverManaged;
    }

    boolean isAddedToCurrentWorld(CustomRecipeEntry recipe) {
        return recipe != null && target.isWorld();
    }

    void addToCurrentWorld(CustomRecipeEntry recipe) {
        // Recipes are added directly to a WorldRecipeTarget by RecipeBuilder.
    }

    String currentWorldId() { return editorWorldId; }
    String currentWorldName() { return editorWorldName; }

    void removeFromWorld(CustomRecipeEntry recipe, String worldId) {
        if (recipe == null || recipe.world_ids == null || worldId == null) return;
        recipe.world_ids.remove(worldId);
        if (recipe.world_names != null) recipe.world_names.remove(worldId);
    }

    void setWorldAssigned(CustomRecipeEntry recipe, String worldId, String worldName, boolean assigned) {
        if (recipe == null || worldId == null || worldId.isBlank()) return;
        if (!assigned) {
            removeFromWorld(recipe, worldId);
            return;
        }
        if (recipe.world_ids == null) recipe.world_ids = new ArrayList<>();
        if (recipe.world_names == null) recipe.world_names = new java.util.LinkedHashMap<>();
        if (!recipe.world_ids.contains(worldId)) recipe.world_ids.add(worldId);
        recipe.world_names.put(worldId, worldName == null || worldName.isBlank() ? "World" : worldName);
    }

    void persistLocalWorldAssignments() {
        if (!serverManaged) ConfigLoader.saveAndInvalidate(currentConfig());
    }

    RecipeTarget target() { return target; }

    void addFromLibrary(CustomRecipeEntry source) {
        if (!target.isWorld() || source == null) return;
        CustomRecipeEntry copy = ConfigLoader.copyRecipe(source);
        if (copy != null && recipes.stream().noneMatch(existing -> ConfigLoader.sameRecipe(existing, copy))) recipes.add(copy);
    }

    int globalLibraryImportableCount() {
        if (!target.isWorld()) return 0;
        return ConfigLoader.previewGlobalLibraryImport(baseConfig, recipes).added();
    }

    ConfigLoader.LibraryImportResult addAllFromGlobalLibrary() {
        if (!target.isWorld()) return new ConfigLoader.LibraryImportResult(0, 0);
        currentConfig();
        ConfigLoader.LibraryImportResult result = ConfigLoader.importGlobalLibraryToWorld(
                baseConfig, target.id(), target.displayName());
        recipes.clear();
        recipes.addAll(targetConfig.custom_recipes);
        return result;
    }

    void alsoSaveToLibrary(CustomRecipeEntry source) {
        if (!target.isWorld() || source == null) return;
        CustomRecipeEntry copy = ConfigLoader.copyRecipe(source);
        if (copy != null) baseConfig.global_library.custom_recipes.add(copy);
    }

    ConfigScreen createTargetScreen(RecipeTarget nextTarget) {
        currentConfig();
        ConfigScreen next = new ConfigScreen(parent, baseConfig, title.getString(), serverManaged, saveAction,
                nextTarget, targetLocked);
        next.initialConfigJson = initialConfigJson;
        return next;
    }

    ConfigScreen createImportedConfigScreen(ModConfig imported) {
        currentConfig();
        ConfigScreen next = new ConfigScreen(parent, imported, title.getString(), serverManaged, saveAction,
                target, targetLocked);
        next.initialConfigJson = initialConfigJson;
        return next;
    }
    void selectTarget(RecipeTarget nextTarget) {
        if (targetLocked) return;
        client.setScreen(createTargetScreen(nextTarget));
    }

    ModConfig currentConfig() {
        targetConfig.custom_recipes = new ArrayList<>(recipes);
        targetConfig.disabled_builtin = new ArrayList<>(disabled);
        targetConfig.known_by_default_builtin = new ArrayList<>(knownByDefaultBuiltin);
        targetConfig.disabled_recipes = new ArrayList<>(disabledRecipes);
        targetConfig.disabled_recipe_variants = new ArrayList<>(disabledRecipeVariants);
        targetConfig.hidden_quick_add_builtin = new ArrayList<>(hiddenQuickAddBuiltin);
        targetConfig.quick_add_recipes = new ArrayList<>(quickAddRecipes);
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
        hiddenQuickAddBuiltin.clear();
        hiddenQuickAddBuiltin.addAll(replacement.hidden_quick_add_builtin);
        quickAddRecipes.clear();
        quickAddRecipes.addAll(replacement.quick_add_recipes);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return true; }
}
