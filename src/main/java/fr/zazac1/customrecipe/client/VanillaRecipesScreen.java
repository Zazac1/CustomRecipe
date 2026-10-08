package fr.zazac1.customrecipe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarFile;

/** Server-filtered default crafting recipe browser for OPs, including installed mods. */
public class VanillaRecipesScreen extends Screen {
    private static List<VanillaRecipePage.VanillaRecipeInfo> startupRecipeCache = List.of();
    /** Raw client-side recipe data is indexed once and reused for every local search. */
    private static final Map<String, String> localRecipeJsonCache = new java.util.LinkedHashMap<>();
    private static boolean startupPreloadStarted;
    private static final int ROW = 20;
    private static final int HEADER_Y = 34;
    private static final int SEARCH_Y = 52;
    private static final int ROWS_Y = 78;
    private static final int PREVIEW_TOOLBAR_Y = 32;
    private static final int PREVIEW_PANEL_Y = 54;
    private static final int RECIPE_STATE_W = 76;
    private static final int SCROLLBAR_W = 12;
    private static final int SCROLLBAR_TRACK_W = SCROLLBAR_W - 1;
    private static final int SCROLLBAR_TRACK_COLOR = 0xFF5A5A5A;

    private enum StatusFilter {
        ALL, ENABLED, DISABLED, SPECIAL
    }
    private enum SourceFilter {
        ALL, MODDED, VANILLA
    }
    private final ConfigScreen parent;
    private final boolean localMode;
    private String query = "";
    private boolean matchIngredients = true;
    private boolean matchOutput = true;
    private StatusFilter statusFilter = StatusFilter.ALL;
    private SourceFilter sourceFilter = SourceFilter.ALL;
    private final List<VanillaRecipePage.VanillaRecipeInfo> recipes = new ArrayList<>();
    private int total;
    private int nextPage;
    private int scroll;
    private boolean loading;
    private boolean searchStarted;
    private int pendingSearchTicks = -1;
    private boolean restoreSearchFocus;
    private EditBox searchField;
    private boolean draggingRecipeScrollbar;
    /** The browser keeps the selected recipe and its lazy-loaded detail payload in one screen. */
    private VanillaRecipePage.VanillaRecipeInfo selectedRecipe;
    private VanillaRecipeDetails selectedDetails;
    private VanillaRecipeDetails.VariantPreview selectedVariant;

    /** Shared inner bounds keep search and rows aligned in the left browser. */
    private int recipeContentX() { return recipeBoxX() + 2; }
    private int recipeContentRight() { return recipeBoxX() + recipeBoxW() - 2; }
    private int recipeListX() { return recipeContentX(); }
    private int recipeLabelX() { return recipeListX() + 22; }
    private int recipeLabelWidth() { return Math.min(360, Math.max(220, width / 3)); }
    private int recipeStateX() { return recipeLabelX() + recipeLabelWidth() + 4; }
    private int recipeScrollbarX() { return recipeBoxX() + recipeBoxW() - SCROLLBAR_W; }
    private int recipeScrollbarY() { return recipeBoxY() + 1; }
    private int recipeScrollbarH() { return Math.max(1, recipeBoxH() - 2); }
    private int recipeBoxX() { return 6; }
    private int recipeBoxY() { return ROWS_Y - 4; }
    private int recipeBoxW() { return recipeStateX() + RECIPE_STATE_W + 4 + SCROLLBAR_W - recipeBoxX() + 1; }
    private int recipeBoxH() { return visibleRows() * ROW + 6; }
    private int previewBoxX() { return recipeBoxX() + recipeBoxW() + 12; }
    private int previewBoxW() { return Math.max(80, width - previewBoxX() - 8); }
    private int previewBoxY() { return PREVIEW_PANEL_Y; }
    private int previewBoxH() { return recipeBoxY() + recipeBoxH() - previewBoxY(); }
    private int filterToolbarX() { return recipeStateX(); }
    private int filterToolbarW() { return Math.max(4, width - filterToolbarX() - 8); }
    private int filterButtonW() { return Math.max(40, (filterToolbarW() - 12) / 4); }
    private int filterButtonX(int index) { return filterToolbarX() + index * (filterButtonW() + 4); }
    private int previewGridX() { return previewBoxX() + previewBoxW() / 2 - 66; }
    private int previewGridY() { return previewBoxY() + 60; }
    private int previewVariantColumns() { return Math.min(10, Math.max(1, (previewBoxW() - 32) / 24)); }
    private int previewVariantGridX() { return previewBoxX() + previewBoxW() / 2 - previewVariantColumns() * 12 + 2; }
    private int previewVariantGridY() { return previewGridY() + 100; }
    private int previewVariantActionsX() { return previewBoxX() + previewBoxW() / 2 - 71; }
    private int previewVariantActionsY() {
        int count = selectedDetails == null ? 0 : Math.min(48, selectedDetails.variants().size());
        return previewVariantGridY() + ((count + previewVariantColumns() - 1) / previewVariantColumns()) * 24 + 6;
    }
    private int maxScroll(List<VanillaRecipePage.VanillaRecipeInfo> shown) { return Math.max(0, shown.size() - visibleRows()); }
    private boolean hasRecipeScrollbar(List<VanillaRecipePage.VanillaRecipeInfo> shown) { return maxScroll(shown) > 0; }
    private boolean isOverRecipeScrollbar(double mouseX, double mouseY, List<VanillaRecipePage.VanillaRecipeInfo> shown) {
        return hasRecipeScrollbar(shown)
                && mouseX >= recipeScrollbarX() && mouseX < recipeScrollbarX() + SCROLLBAR_TRACK_W
                && mouseY >= recipeScrollbarY() && mouseY < recipeScrollbarY() + recipeScrollbarH();
    }

    /** Applies the local status/special view without changing the server search result. */
    private List<VanillaRecipePage.VanillaRecipeInfo> filteredRecipes() {
        if (statusFilter == StatusFilter.ALL) return recipes;

        List<VanillaRecipePage.VanillaRecipeInfo> filtered = new ArrayList<>();
        for (VanillaRecipePage.VanillaRecipeInfo recipe : recipes) {
            boolean disabled = parent.disabledRecipes.contains(recipe.id());
            if ((statusFilter == StatusFilter.DISABLED && disabled && !recipe.special())
                    || (statusFilter == StatusFilter.ENABLED && !disabled && !recipe.special())
                    || (statusFilter == StatusFilter.SPECIAL && recipe.special())) {
                filtered.add(recipe);
            }
        }
        return filtered;
    }

    private Component statusFilterLabel() {
        return switch (statusFilter) {
            case ALL -> Component.translatable("customrecipe.vanilla.status_all");
            case ENABLED -> Component.translatable("customrecipe.vanilla.status_enabled").withColor(0x55FF55);
            case DISABLED -> Component.translatable("customrecipe.vanilla.status_disabled").withColor(0xFF5555);
            case SPECIAL -> Component.translatable("customrecipe.vanilla.status_special").withColor(0x77BBFF);
        };
    }

    private Component sourceFilterLabel() {
        return switch (sourceFilter) {
            case ALL -> Component.translatable("customrecipe.vanilla.show_all");
            case MODDED -> Component.translatable("customrecipe.vanilla.show_modded");
            case VANILLA -> Component.translatable("customrecipe.vanilla.show_vanilla");
        };
    }

    private void cycleStatusFilter() {
        statusFilter = switch (statusFilter) {
            case ALL -> StatusFilter.ENABLED;
            case ENABLED -> StatusFilter.DISABLED;
            case DISABLED -> StatusFilter.SPECIAL;
            case SPECIAL -> StatusFilter.ALL;
        };
        scroll = 0;
        clearWidgets(); init();
    }

    private void cycleSourceFilter() {
        sourceFilter = switch (sourceFilter) {
            case ALL -> SourceFilter.MODDED;
            case MODDED -> SourceFilter.VANILLA;
            case VANILLA -> SourceFilter.ALL;
        };
        resetSearch();
    }

    public VanillaRecipesScreen(ConfigScreen parent) {
        this(parent, false);
    }

    /** Local ModMenu mode reads the default recipe data already loaded by the minecraft. */
    public VanillaRecipesScreen(ConfigScreen parent, boolean localMode) {
        super(Component.translatable("customrecipe.vanilla.title"));
        this.parent = parent;
        this.localMode = localMode;
    }

    /** Builds the local browser cache once, after the minecraft has finished initialising. */
    static void preloadAtStartup(net.minecraft.client.Minecraft minecraft) {
        if (startupPreloadStarted || !startupRecipeCache.isEmpty() || minecraft.getResourceManager() == null) return;
        startupPreloadStarted = true;
        try {
            VanillaRecipesScreen scanner = new VanillaRecipesScreen(null, true);
            VanillaRecipePage page = scanner.findLocalRecipes();
            startupRecipeCache = List.copyOf(page.recipes());
        } catch (RuntimeException exception) {
            // A failed optional local scan must not retry every client tick.
            // The regular browser can still perform a later fresh query.
        }
    }

    ConfigScreen configScreen() { return parent; }

    @Override
    protected void init() {
        // Must be registered before widgets: Screen.render applies the blurred
        // background first, then renderables in insertion order. Drawing this
        // content directly before super.render would put it behind that blur.
        addRenderableOnly((ctx, mx, my, d) -> renderBrowserContent(ctx));
        addRenderableOnly((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, parent.target(), parent.targetLabel()));
        int clearSearchX = recipeContentRight() - 18;
        searchField = addRenderableWidget(new EditBox(font, recipeContentX(), SEARCH_Y,
                clearSearchX - recipeContentX(), 18,
                Component.translatable("customrecipe.vanilla.search_hint")));
        searchField.setValue(query);
        searchField.setResponder(this::onQueryChanged);
        if (restoreSearchFocus) {
            setFocused(searchField);
            restoreSearchFocus = false;
        }

        addRenderableWidget(Button.builder(Component.empty(), b -> {
            query = "";
            searchField.setValue("");
            pendingSearchTicks = -1;
            resetSearch();
        }).bounds(clearSearchX, SEARCH_Y, 18, 18).build());
        addRenderableOnly((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                CustomRecipeSprites.REJECT, clearSearchX, SEARCH_Y, 18, 18));

        addRenderableWidget(Button.builder(Component.translatable("customrecipe.vanilla.ingredient", Component.translatable(matchIngredients ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), b -> {
            matchIngredients = !matchIngredients;
            resetSearch();
        }).bounds(filterButtonX(0), PREVIEW_TOOLBAR_Y, filterButtonW(), 18).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.vanilla.output", Component.translatable(matchOutput ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), b -> {
            matchOutput = !matchOutput;
            resetSearch();
        }).bounds(filterButtonX(1), PREVIEW_TOOLBAR_Y, filterButtonW(), 18).build());
        addRenderableWidget(Button.builder(statusFilterLabel(), b -> cycleStatusFilter())
                .bounds(filterButtonX(2), PREVIEW_TOOLBAR_Y, filterButtonW(), 18).build());
        addRenderableWidget(Button.builder(sourceFilterLabel(), b -> cycleSourceFilter())
                .bounds(filterButtonX(3), PREVIEW_TOOLBAR_Y, filterButtonW(), 18).build());

        int visibleRows = visibleRows();
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        for (int i = 0; i < visibleRows && scroll + i < shownRecipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = shownRecipes.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            boolean disabled = parent.disabledRecipes.contains(recipe.id());
            addRenderableWidget(Button.builder(recipeLabel(recipe), b -> selectRecipe(recipe))
                    .bounds(recipeLabelX(), y, recipeLabelWidth(), ROW).build());
            addRenderableWidget(Button.builder(disabled ? Component.translatable("customrecipe.state.disabled").withColor(0xFF5555)
                            : Component.translatable("customrecipe.state.enabled").withColor(0x55FF55), b -> toggle(recipe.id()))
                    .bounds(recipeStateX(), y, RECIPE_STATE_W, ROW).build());
        }

        addPreviewControls();

        int bottom = height - 26;
        String saveLabel = Component.translatable("customrecipe.button.save").getString();
        addRenderableWidget(Button.builder(Component.empty(), b -> parent.saveFromSubmenu())
                .bounds(width / 2 - 100, bottom, 200, 22).build());
        addRenderableOnly((ctx, mouseX, mouseY, delta) -> {
            int iconX = width / 2 - font.width(saveLabel) / 2 - 20;
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SAVE, iconX, bottom + 3, 16, 16);
            ctx.drawCenteredString(font, saveLabel, width / 2, bottom + 7, 0xFFFFFFFF);
        });

        if (!searchStarted) resetSearch();
    }

    void applyResult(VanillaRecipePage page) {
        if (page.page() == 0) {
            recipes.clear();
            scroll = 0;
        }
        Set<String> present = new HashSet<>();
        for (VanillaRecipePage.VanillaRecipeInfo recipe : recipes) present.add(recipe.id());
        for (VanillaRecipePage.VanillaRecipeInfo recipe : page.recipes()) {
            if (present.add(recipe.id())) recipes.add(recipe);
        }
        total = page.total();
        nextPage = page.page() + 1;
        loading = false;
        clearWidgets(); init();
    }

    private void selectRecipe(VanillaRecipePage.VanillaRecipeInfo recipe) {
        if (recipe.id().equals(selectedRecipe == null ? "" : selectedRecipe.id())) return;
        selectedRecipe = recipe;
        selectedDetails = null;
        selectedVariant = null;
        if (localMode) {
            minecraft.execute(() -> applyDetails(findLocalRecipeDetails(recipe.id())));
        } else {
            ClientServerConfigNetworking.requestVanillaDetails(recipe.id());
        }
        clearWidgets(); init();
    }

    /** Called from the S2C payload handler while this two-panel browser is active. */
    void applyDetails(VanillaRecipeDetails details) {
        if (selectedRecipe == null || !selectedRecipe.id().equals(details.recipeId())) return;
        selectedDetails = details;
        selectedVariant = details.variants().isEmpty() ? null : details.variants().getFirst();
        clearWidgets(); init();
    }

    private void addPreviewControls() {
        if (selectedRecipe == null || selectedDetails == null || selectedDetails.variants().isEmpty()) return;
        int columns = previewVariantColumns();
        for (int index = 0; index < Math.min(48, selectedDetails.variants().size()); index++) {
            VanillaRecipeDetails.VariantPreview variant = selectedDetails.variants().get(index);
            int x = previewVariantGridX() + (index % columns) * 24;
            int y = previewVariantGridY() + (index / columns) * 24;
            addRenderableWidget(Button.builder(Component.empty(), b -> {
                selectedVariant = variant;
                clearWidgets(); init();
            }).bounds(x, y, 20, 20).build());
        }
        int actionY = previewVariantActionsY();
        String material = selectedVariant == null ? selectedDetails.variants().getFirst().materialId() : selectedVariant.materialId();
        boolean disabled = isVariantDisabled(selectedRecipe.id(), material);
        addRenderableWidget(Button.builder(disabled ? Component.translatable("customrecipe.details.variant_disabled").withColor(0xFF5555)
                        : Component.translatable("customrecipe.details.disable_variant").withColor(0xFF5555), b -> {
                    toggleVariant(selectedRecipe.id(), material);
                    clearWidgets(); init();
                }).bounds(previewVariantActionsX(), actionY, 142, 20).build());
        boolean allDisabled = isRecipeDisabled(selectedRecipe.id());
        addRenderableWidget(Button.builder(allDisabled ? Component.translatable("customrecipe.details.variants_disabled").withColor(0xFF5555)
                        : Component.translatable("customrecipe.details.disable_variants").withColor(0xFF5555), b -> {
                    toggleAllVariants(selectedRecipe.id());
                    clearWidgets(); init();
                }).bounds(previewVariantActionsX(), actionY + 24, 142, 20).build());
    }

    private void resetSearch() {
        searchStarted = true;
        scroll = 0;
        nextPage = 0;
        total = 0;
        recipes.clear();
        loading = true;
        if (localMode) {
            VanillaRecipePage page = findLocalRecipes();
            recipes.addAll(page.recipes());
            total = page.total();
            nextPage = 1;
            loading = false;
            clearWidgets(); init();
            return;
        }
        requestServerPage(0);
    }

    /** Refresh after a short pause so typing does not scan or query once per key. */
    private void onQueryChanged(String value) {
        query = value;
        restoreSearchFocus = true;
        pendingSearchTicks = 4;
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingSearchTicks > 0 && --pendingSearchTicks == 0) {
            resetSearch();
        }
    }

    private void loadMore() {
        if (localMode || loading || recipes.size() >= total) return;
        loading = true;
        requestServerPage(nextPage);
    }

    private void requestServerPage(int page) {
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput,
                statusFilter.name(), sourceFilter.name(), parent.disabledRecipes, page);
    }


    private VanillaRecipePage findLocalRecipes() {
        if (query.isBlank() && matchIngredients && matchOutput
                && sourceFilter == SourceFilter.ALL && !startupRecipeCache.isEmpty()) {
            return new VanillaRecipePage(startupRecipeCache, 0, startupRecipeCache.size());
        }
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();
        Set<String> matchedIds = new HashSet<>();
        if (!localRecipeJsonCache.isEmpty()) {
            for (Map.Entry<String, String> cached : localRecipeJsonCache.entrySet()) {
                addLocalRecipe(matches, matchedIds, cached.getKey(), cached.getValue(), loweredQuery);
            }
            matches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
            return new VanillaRecipePage(matches, 0, matches.size());
        }
        Map<ResourceLocation, Resource> resources = minecraft.getResourceManager().listResources("recipe",
                id -> id.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> resource : resources.entrySet()) {
            try (var input = resource.getValue().open()) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                String recipeId = resource.getKey().getNamespace() + ":" + resource.getKey().getPath()
                        .substring("recipe/".length(), resource.getKey().getPath().length() - ".json".length());
                addLocalRecipe(matches, matchedIds, recipeId, json, loweredQuery);
            } catch (Exception ignored) {
                // A malformed optional resource is simply omitted from the local browser.
            }
        }

        // Client resources do not include the server-data recipe pack.  Read
        // Minecraft's built-in SERVER_DATA pack explicitly so this works from
        // Mod Menu at the title screen, before any world is running.
        loadBundledVanillaRecipes(matches, matchedIds, loweredQuery);
        loadInstalledModRecipes(matches, matchedIds, loweredQuery);

        matches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
        return new VanillaRecipePage(matches, 0, matches.size());
    }

    private void addLocalRecipe(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                String recipeId, String json, String loweredQuery) {
        // Cache before filtering: a later ingredient/output query reuses this
        // raw data rather than walking resource packs and mod archives again.
        localRecipeJsonCache.putIfAbsent(recipeId, json);
        if (!json.contains("crafting_")) return;
        boolean vanillaRecipe = recipeId.startsWith("minecraft:");
        if ((sourceFilter == SourceFilter.VANILLA && !vanillaRecipe)
                || (sourceFilter == SourceFilter.MODDED && vanillaRecipe)) return;
        String resultId = findResultId(json, recipeId);
        boolean outputMatch = matchOutput && itemMatchesQuery(resultId, loweredQuery);
        // A recipe ID normally describes its output (for example oak_planks).
        // Keep that ID search under the Output switch as well; otherwise a
        // query such as "oak" kept matching regardless of the button state.
        boolean recipeIdMatch = matchOutput && recipeId.toLowerCase(Locale.ROOT).contains(loweredQuery);
        boolean ingredientMatch = matchIngredients && localIngredientsMatch(json, loweredQuery);
        if ((loweredQuery.isEmpty() || recipeIdMatch || outputMatch || ingredientMatch)
                && matchedIds.add(recipeId)) {
            RecipeLayout layout = findLocalRecipeLayout(json);
            boolean special = isSpecialRecipe(json);
            matches.add(new VanillaRecipePage.VanillaRecipeInfo(recipeId, resultId, toPreviewSlots(layout),
                    layout.width(), layout.height(), layout.shapeless(), special));
        }
    }

    private void loadBundledVanillaRecipes(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                           String loweredQuery) {
        var vanillaPack = ServerPacksSource.createVanillaPackSource();
        try {
            vanillaPack.listResources(PackType.SERVER_DATA, "minecraft", "recipe", (resourceId, supplier) -> {
                try (var input = supplier.get()) {
                    String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    String recipeId = resourceId.getNamespace() + ":" + resourceId.getPath()
                            .substring("recipe/".length(), resourceId.getPath().length() - ".json".length());
                    addLocalRecipe(matches, matchedIds, recipeId, json, loweredQuery);
                } catch (Exception ignored) {
                    // An optional malformed recipe must not prevent the rest of
                    // Minecraft's built-in catalogue from being listed.
                }
            });
        } catch (Exception ignored) {
            // A damaged vanilla pack leaves installed mod recipes usable.
        } finally {
            vanillaPack.close();
        }
    }

    /** ModMenu can open before datapack recipes are mounted, so read installed mod archives directly. */
    private void loadInstalledModRecipes(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                         String loweredQuery) {
        Set<Path> scannedArchives = new HashSet<>();
        List<Path> originPaths;
        try (var entries = Files.list(FMLPaths.MODSDIR.get())) {
            originPaths = entries.filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
        } catch (Exception ignored) {
            return;
        }
        for (Path archivePath : originPaths) {
                Path normalized = archivePath.toAbsolutePath().normalize();
                if (!scannedArchives.add(normalized) || !Files.isRegularFile(normalized)
                        || !normalized.getFileName().toString().endsWith(".jar")) continue;
                try (JarFile jar = new JarFile(normalized.toFile())) {
                    Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        String path = entry.getName();
                        if (!path.startsWith("data/") || !path.endsWith(".json")) continue;
                        // Accept only data/<namespace>/recipe/<id>.json. A loose
                        // contains("/recipe/") also matched advancement/datapack paths.
                        int namespaceEnd = path.indexOf('/', "data/".length());
                        int recipeStart = namespaceEnd + 1;
                        if (namespaceEnd <= "data/".length()
                                || !path.startsWith("recipe/", recipeStart)
                                || recipeStart + "recipe/".length() >= path.length() - ".json".length()) continue;
                        String recipeId = path.substring("data/".length(), namespaceEnd) + ":"
                                + path.substring(recipeStart + "recipe/".length(), path.length() - ".json".length());
                        // Do not scan this mod's bundled library templates as
                        // regular vanilla/mod recipes.
                        if (recipeId.startsWith("customrecipe:")) continue;
                        try (var input = jar.getInputStream(entry)) {
                            addLocalRecipe(matches, matchedIds, recipeId,
                                    new String(input.readAllBytes(), StandardCharsets.UTF_8), loweredQuery);
                        }
                    }
                } catch (Exception ignored) {
                    // A non-archive mod origin simply has no local recipe files to browse.
                }
        }
    }

    private String findResultId(String json, String fallback) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement result = root.get("result");
            if (result == null) return fallback;
            if (result.isJsonPrimitive()) return result.getAsString();
            if (result.isJsonObject() && result.getAsJsonObject().has("id")) {
                return result.getAsJsonObject().get("id").getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private boolean isSpecialRecipe(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.has("type") && root.get("type").getAsString().contains("crafting_special");
        } catch (Exception ignored) {
            return false;
        }
    }

    private RecipeLayout findLocalRecipeLayout(String json) {
        List<String> ingredients = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";
            if (type.contains("crafting_shaped") && root.has("pattern") && root.has("key")) {
                var pattern = root.getAsJsonArray("pattern");
                JsonObject key = root.getAsJsonObject("key");
                int width = 0;
                for (JsonElement row : pattern) width = Math.max(width, row.getAsString().length());
                for (JsonElement row : pattern) {
                    String symbols = row.getAsString();
                    for (int x = 0; x < width; x++) {
                        ingredients.add(x >= symbols.length() || symbols.charAt(x) == ' ' ? ""
                                : firstLocalIngredientId(key.get(String.valueOf(symbols.charAt(x)))));
                    }
                }
                return new RecipeLayout(ingredients, width, pattern.size(), false);
            }
            collectIngredientIds(root.get("ingredients"), ingredients);
        } catch (Exception ignored) {}
        return new RecipeLayout(ingredients, 0, 0, true);
    }

    private String firstLocalIngredientId(JsonElement element) {
        List<String> choices = localIngredientChoices(element);
        return choices.isEmpty() ? "" : choices.getFirst();
    }

    private void collectIngredientIds(JsonElement element, List<String> ingredients) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (!raw.isBlank()) ingredients.add(raw);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectIngredientIds(child, ingredients);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) {
            ingredients.add(object.get("item").getAsString());
        } else if (object.has("tag")) {
            ingredients.add("#" + object.get("tag").getAsString());
        } else {
            for (Map.Entry<String, JsonElement> child : object.entrySet()) collectIngredientIds(child.getValue(), ingredients);
        }
    }

    private record RecipeLayout(List<String> ingredients, int width, int height, boolean shapeless) {}

    private List<String> toPreviewSlots(RecipeLayout layout) {
        List<String> slots = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        if (layout.shapeless()) {
            for (int i = 0; i < layout.ingredients().size() && i < 9; i++) slots.set(i, layout.ingredients().get(i));
            return slots;
        }
        for (int row = 0; row < layout.height() && row < 3; row++) {
            for (int column = 0; column < layout.width() && column < 3; column++) {
                int source = row * layout.width() + column;
                slots.set(row * 3 + column, source < layout.ingredients().size() ? layout.ingredients().get(source) : "");
            }
        }
        return slots;
    }

    private void toggle(String recipeId) {
        if (!parent.disabledRecipes.remove(recipeId)) parent.disabledRecipes.add(recipeId);
        // This is a staged client-side change.  Keep the already-loaded page
        // and selection: Fabric 1.3.7 deliberately avoids a full server/JAR
        // search for every Enable/Disable click.
        scroll = Math.min(scroll, maxScroll(filteredRecipes()));
        clearWidgets(); init();
    }

    void requestDetails(VanillaRecipeDetailsScreen screen, String recipeId) {
        if (localMode) {
            minecraft.execute(() -> screen.applyDetails(findLocalRecipeDetails(recipeId)));
        } else {
            ClientServerConfigNetworking.requestVanillaDetails(recipeId);
        }
    }

    /** Mirrors the server variant query using the vanilla JSON and minecraft item tags. */
    private fr.zazac1.customrecipe.VanillaRecipeDetails findLocalRecipeDetails(String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, List.of());
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
        Optional<String> json = readLocalRecipeJson(resourceId);
        if (json.isEmpty()) return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, List.of());

        try {
            JsonObject root = JsonParser.parseString(json.get()).getAsJsonObject();
            List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, List.of()));
            boolean shaped = root.has("type") && root.get("type").getAsString().contains("crafting_shaped");
            if (shaped && root.has("pattern") && root.has("key")) {
                var pattern = root.getAsJsonArray("pattern");
                JsonObject key = root.getAsJsonObject("key");
                int width = 0;
                for (JsonElement row : pattern) width = Math.max(width, row.getAsString().length());
                for (int row = 0; row < pattern.size() && row < 3; row++) {
                    String symbols = pattern.get(row).getAsString();
                    for (int column = 0; column < width && column < 3; column++) {
                        JsonElement ingredient = column < symbols.length() && symbols.charAt(column) != ' '
                                ? key.get(String.valueOf(symbols.charAt(column))) : null;
                        choices.set(row * 3 + column, localIngredientChoices(ingredient));
                    }
                }
            } else if (root.has("ingredients") && root.get("ingredients").isJsonArray()) {
                var ingredients = root.getAsJsonArray("ingredients");
                for (int slot = 0; slot < ingredients.size() && slot < 9; slot++) {
                    choices.set(slot, localIngredientChoices(ingredients.get(slot)));
                }
            }

            TreeSet<String> variants = new TreeSet<>();
            for (List<String> choice : choices) if (choice.size() > 1) variants.addAll(choice);
            List<fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview> previews = new ArrayList<>();
            for (String material : variants.stream().limit(48).toList()) {
                List<String> slots = new ArrayList<>(9);
                for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.getFirst()));
                previews.add(new fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview(material, slots));
            }
            return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, previews);
        } catch (Exception ignored) {
            return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, List.of());
        }
    }

    private Optional<String> readLocalRecipeJson(ResourceLocation resourceId) {
        try {
            var resource = minecraft.getResourceManager().getResource(resourceId).orElse(null);
            if (resource != null) {
                try (var input = resource.open()) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            var vanillaPack = ServerPacksSource.createVanillaPackSource();
            try {
                var supplier = vanillaPack.getResource(PackType.SERVER_DATA, resourceId);
                if (supplier == null) return Optional.empty();
                try (var input = supplier.get()) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            } finally {
                vanillaPack.close();
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<String> localIngredientChoices(JsonElement element) {
        TreeSet<String> choices = new TreeSet<>();
        collectLocalIngredientChoices(element, choices);
        return new ArrayList<>(choices);
    }

    /** Expands item tags before matching, so #planks also finds every individual plank. */
    private boolean localIngredientsMatch(String json, String loweredQuery) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<JsonElement> ingredients = new ArrayList<>();
            if (root.has("ingredients")) ingredients.add(root.get("ingredients"));
            if (root.has("key") && root.get("key").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("key").entrySet()) ingredients.add(entry.getValue());
            }
            for (JsonElement ingredient : ingredients) {
                for (String itemId : localIngredientChoices(ingredient)) if (itemMatchesQuery(itemId, loweredQuery)) return true;
            }
        } catch (Exception ignored) {
            // A malformed optional recipe resource is omitted from matching.
        }
        return false;
    }

    private void collectLocalIngredientChoices(JsonElement element, Set<String> choices) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
                if (tagId != null) collectLocalTagItems(tagId, choices, new HashSet<>());
            } else if (!raw.isBlank()) {
                choices.add(raw);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectLocalIngredientChoices(child, choices);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) {
            choices.add(object.get("item").getAsString());
            return;
        }
        if (object.has("tag")) {
            ResourceLocation tagId = ResourceLocation.tryParse(object.get("tag").getAsString());
            if (tagId != null) collectLocalTagItems(tagId, choices, new HashSet<>());
        }
    }

    /** Reads tag JSON too, so variants are available from ModMenu before joining a world. */
    private void collectLocalTagItems(ResourceLocation tagId, Set<String> choices, Set<ResourceLocation> visited) {
        if (!visited.add(tagId)) return;
        try {
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId)).ifPresent(entries -> {
                for (var entry : entries) choices.add(BuiltInRegistries.ITEM.getKey(entry.value()).toString());
            });
            if (!choices.isEmpty()) return;
        } catch (IllegalStateException ignored) {
            // At the title screen tags may not be bound yet; use their JSON below.
        }
        ResourceLocation tagResource = ResourceLocation.fromNamespaceAndPath(tagId.getNamespace(), "tags/item/" + tagId.getPath() + ".json");
        Optional<String> json = readLocalRecipeJson(tagResource);
        if (json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json.get()).getAsJsonObject();
            if (!root.has("values") || !root.get("values").isJsonArray()) return;
            for (JsonElement value : root.getAsJsonArray("values")) {
                String raw = value.isJsonPrimitive() ? value.getAsString()
                        : value.isJsonObject() && value.getAsJsonObject().has("id")
                        ? value.getAsJsonObject().get("id").getAsString() : "";
                if (raw.startsWith("#")) {
                    ResourceLocation nested = ResourceLocation.tryParse(raw.substring(1));
                    if (nested != null) collectLocalTagItems(nested, choices, visited);
                } else if (!raw.isBlank()) {
                    choices.add(raw);
                }
            }
        } catch (Exception ignored) {
            // An optional malformed tag must not prevent the recipe preview from opening.
        }
    }

    boolean isVariantDisabled(String recipeId, String materialId) {
        return parent.disabledRecipeVariants.stream().anyMatch(rule -> recipeId.equals(rule.recipe_id) && materialId.equals(rule.material_id));
    }

    void toggleVariant(String recipeId, String materialId) {
        for (int i = 0; i < parent.disabledRecipeVariants.size(); i++) {
            var rule = parent.disabledRecipeVariants.get(i);
            if (recipeId.equals(rule.recipe_id) && materialId.equals(rule.material_id)) {
                parent.disabledRecipeVariants.remove(i);
                return;
            }
        }
        parent.disabledRecipeVariants.add(new fr.zazac1.customrecipe.RecipeVariantRule(recipeId, materialId));
    }

    boolean isRecipeDisabled(String recipeId) { return parent.disabledRecipes.contains(recipeId); }

    void toggleAllVariants(String recipeId) { toggle(recipeId); }

    private int visibleRows() {
        return Math.max(1, (height - ROWS_Y - 32) / ROW);
    }

    private Component recipeLabel(VanillaRecipePage.VanillaRecipeInfo recipe) {
        return Component.literal(recipe.result()).withColor(recipe.special() ? 0x77BBFF : 0xFFFFFF);
    }

    private String itemName(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return item == null || item == Items.AIR ? shortId(id) : new ItemStack(item).getHoverName().getString();
    }

    private boolean itemMatchesQuery(String itemId, String loweredQuery) {
        return itemId.toLowerCase(Locale.ROOT).contains(loweredQuery)
                || itemName(itemId).toLowerCase(Locale.ROOT).contains(loweredQuery);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Rendered as the first renderable, after the background blur and before widgets. */
    private void renderBrowserContent(GuiGraphics ctx) {
        renderPanels(ctx);
        if (loading && recipes.isEmpty()) {
            ctx.drawString(font, Component.translatable("customrecipe.vanilla.loading"), 8, ROWS_Y + 2, 0xBBBBBB, false);
            return;
        }

        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        ctx.drawString(font, Component.literal("Found " + total + " recipes"), 8, HEADER_Y, 0xFFFFEE88, false);
        if (shownRecipes.isEmpty()) {
            ctx.drawString(font, Component.translatable("customrecipe.vanilla.none"), 8, ROWS_Y + 2, 0xFFBBBBBB, false);
        }
        for (int i = 0; i < visibleRows() && scroll + i < shownRecipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = shownRecipes.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            int rowColor = recipe.special() ? 0x22335566
                    : parent.disabledRecipes.contains(recipe.id()) ? 0x44550000 : 0x22005500;
            ctx.fill(recipeListX(), y, recipeScrollbarX() - 4, y + ROW, rowColor);
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(recipe.result()));
            if (item != null && item != Items.AIR) ctx.renderItem(new ItemStack(item), recipeListX() + 2, y + 2);
        }
        renderInlinePreview(ctx);
        renderRecipeScrollbar(ctx, shownRecipes);
        if (loading) ctx.drawString(font, Component.translatable("customrecipe.vanilla.loading_more"), 8, height - 42, 0xFFBBBBBB, false);
    }

    private String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private void renderInlinePreview(GuiGraphics ctx) {
        int center = previewBoxX() + previewBoxW() / 2;
        if (selectedRecipe == null) {
            ctx.drawCenteredString(font, Component.literal("Select a recipe to preview"), center, previewBoxY() + 14, 0xFFAAAAAA);
            return;
        }
        ctx.drawCenteredString(font, Component.literal("Recipe Preview"), center, previewBoxY() + 10, 0xFFFFFFFF);
        ctx.drawCenteredString(font, Component.literal(selectedRecipe.id()), center, previewBoxY() + 26, 0xFFAAAAAA);
        List<String> slots = selectedVariant == null ? selectedRecipe.slots() : selectedVariant.slots();
        int gridX = previewGridX();
        int gridY = previewGridY();
        for (int slot = 0; slot < 9; slot++) {
            int sx = gridX + (slot % 3) * 24;
            int sy = gridY + (slot / 3) * 24;
            ctx.fill(sx, sy, sx + 20, sy + 20, 0xFF303030);
            if (slot < slots.size()) drawPreviewItem(ctx, slots.get(slot), sx + 2, sy + 2);
        }
        ctx.drawString(font, "->", gridX + 82, gridY + 27, 0xFFFFFFFF, true);
        ctx.fill(gridX + 108, gridY + 24, gridX + 132, gridY + 48, 0xFF303030);
        drawPreviewItem(ctx, selectedRecipe.result(), gridX + 112, gridY + 28);
        String layout = selectedRecipe.shapeless() ? "Shapeless: JSON ingredient order"
                : "Shaped: " + selectedRecipe.gridWidth() + "x" + selectedRecipe.gridHeight() + " pattern";
        ctx.drawCenteredString(font, Component.literal(layout), center, previewBoxY() + 42, 0xFFAAAAAA);
        if (selectedDetails == null) {
            ctx.drawCenteredString(font, Component.literal("Loading variants..."), center, previewVariantGridY() - 18, 0xFFAAAAAA);
            return;
        }
        if (selectedDetails.variants().isEmpty()) {
            ctx.drawCenteredString(font, Component.literal("No interchangeable material"), center, previewVariantGridY() - 18, 0xFFAAAAAA);
            return;
        }
        ctx.drawCenteredString(font, Component.literal("Material variants"), center, previewVariantGridY() - 18, 0xFFFFFFFF);
        int columns = previewVariantColumns();
        for (int index = 0; index < Math.min(48, selectedDetails.variants().size()); index++) {
            VanillaRecipeDetails.VariantPreview variant = selectedDetails.variants().get(index);
            int vx = previewVariantGridX() + (index % columns) * 24;
            int vy = previewVariantGridY() + (index / columns) * 24;
            boolean disabled = isRecipeDisabled(selectedRecipe.id()) || isVariantDisabled(selectedRecipe.id(), variant.materialId());
            ctx.fill(vx, vy, vx + 20, vy + 20, disabled ? 0xAA4A0000 : 0xAA004A18);
            if (selectedVariant != null && selectedVariant.materialId().equals(variant.materialId())) {
                ctx.renderOutline(vx - 1, vy - 1, 22, 22, 0xFFFFFFFF);
            }
            drawPreviewItem(ctx, variant.materialId(), vx + 2, vy + 2);
        }
    }

    private void renderPanels(GuiGraphics ctx) {
        ctx.fill(recipeBoxX(), recipeBoxY(), recipeBoxX() + recipeBoxW(), recipeBoxY() + recipeBoxH(), 0x99141A20);
        ctx.renderOutline(recipeBoxX(), recipeBoxY(), recipeBoxW(), recipeBoxH(), 0xFF40506A);
        ctx.fill(previewBoxX(), previewBoxY(), previewBoxX() + previewBoxW(), previewBoxY() + previewBoxH(), 0x99141A20);
        ctx.renderOutline(previewBoxX(), previewBoxY(), previewBoxW(), previewBoxH(), 0xFF40506A);
    }

    private void renderRecipeScrollbar(GuiGraphics ctx, List<VanillaRecipePage.VanillaRecipeInfo> shown) {
        if (!hasRecipeScrollbar(shown)) return;
        int max = maxScroll(shown);
        int thumbH = 15;
        int thumbY = recipeScrollbarY() + (recipeScrollbarH() - thumbH) * scroll / max;
        ctx.fill(recipeScrollbarX(), recipeScrollbarY(), recipeScrollbarX() + SCROLLBAR_TRACK_W,
                recipeScrollbarY() + recipeScrollbarH(), SCROLLBAR_TRACK_COLOR);
        CustomRecipeSprites.draw(ctx, draggingRecipeScrollbar ? CustomRecipeSprites.SCROLLER_ACTIVE : CustomRecipeSprites.SCROLLER_IDLE,
                recipeScrollbarX(), thumbY, 12, thumbH);
    }

    private void drawPreviewItem(GuiGraphics ctx, String id, int x, int y) {
        if (id == null || id.isBlank() || id.startsWith("#")) return;
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        if (item != null && item != Items.AIR) ctx.renderItem(new ItemStack(item), x, y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        int maxScroll = maxScroll(shownRecipes);
        int oldScroll = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        if (scroll != oldScroll) clearWidgets(); init();
        if (scroll + visibleRows() >= shownRecipes.size() - 3) loadMore();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<VanillaRecipePage.VanillaRecipeInfo> shown = filteredRecipes();
        // The rail owns its full 12 px hitbox, before row buttons receive the click.
        if (button == 0 && isOverRecipeScrollbar(mouseX, mouseY, shown)) {
            draggingRecipeScrollbar = true;
            updateRecipeScrollbar(mouseY, shown);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingRecipeScrollbar) {
            updateRecipeScrollbar(mouseY, filteredRecipes());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingRecipeScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateRecipeScrollbar(double mouseY, List<VanillaRecipePage.VanillaRecipeInfo> shown) {
        int max = maxScroll(shown);
        if (max <= 0) return;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseY - recipeScrollbarY()) / Math.max(1, recipeScrollbarH() - 1)));
        int next = (int) Math.round(progress * max);
        if (next != scroll) {
            scroll = next;
            if (scroll + visibleRows() >= shown.size() - 3) loadMore();
            clearWidgets(); init();
        }
    }

    @Override public boolean isPauseScreen() { return true; }

    /** Recipe states belong to ConfigScreen and are only persisted from there. */
    @Override public void onClose() { minecraft.setScreen(parent); }
}
