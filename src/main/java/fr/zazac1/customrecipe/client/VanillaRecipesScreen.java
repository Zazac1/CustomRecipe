package fr.zazac1.customrecipe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
@Environment(EnvType.CLIENT)
public class VanillaRecipesScreen extends Screen {
    private static List<VanillaRecipePage.VanillaRecipeInfo> startupRecipeCache = List.of();
    private static boolean startupPreloadStarted;
    private static final int ROW = 20;
    // The left browser keeps its original badge/header spacing.
    private static final int LEFT_HEADER_Y = 34;
    private static final int LEFT_SEARCH_Y = 52;
    private static final int LEFT_ROWS_Y = 78;
    // Only the independent preview panel and its filter toolbar are raised.
    private static final int PREVIEW_TOOLBAR_Y = 32;
    private static final int PREVIEW_PANEL_Y = 54;
    private static final int RECIPE_STATE_W = 76;
    private static final int SCROLLBAR_W = 12;
    /** The thumb needs its visual right edge on the frame; the track is one px narrower. */
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
    private boolean draggingRecipeScrollbar;
    private EditBox searchField;
    private VanillaRecipePage.VanillaRecipeInfo selectedRecipe;
    private VanillaRecipeDetails selectedDetails;
    private VanillaRecipeDetails.VariantPreview selectedVariant;

    /** Compact recipe-browser geometry: only the useful recipe name is shown in each row. */
    /** Shared inner horizontal bounds for the search row and every recipe row. */
    private int recipeContentX() { return recipeBoxX() + 2; }
    private int recipeContentRight() { return recipeBoxX() + recipeBoxW() - 2; }
    private int recipeListX() { return recipeContentX(); }
    private int recipeLabelX() { return recipeListX() + 22; }
    private int recipeLabelW() { return Math.min(360, Math.max(220, width / 3)); }
    private int recipeStateX() { return recipeLabelX() + recipeLabelW() + 4; }
    /** The rail runs against the panel's inner right and spans its full inner height. */
    private int recipeScrollbarX() { return recipeBoxX() + recipeBoxW() - SCROLLBAR_W; }
    private int recipeScrollbarY() { return recipeBoxY() + 1; }
    private int recipeScrollbarH() { return Math.max(1, recipeBoxH() - 2); }
    /** The panel's fixed outer left; all content offsets derive from this edge. */
    private int recipeBoxX() { return 6; }
    private int recipeBoxY() { return LEFT_ROWS_Y - 4; }
    private int recipeBoxW() { return recipeStateX() + RECIPE_STATE_W + 4 + SCROLLBAR_W - recipeBoxX() + 1; }
    private int recipeBoxH() { return visibleRows() * ROW + 6; }
    private int previewBoxX() { return recipeBoxX() + recipeBoxW() + 12; }
    private int previewBoxW() { return Math.max(80, width - previewBoxX() - 8); }
    private int previewBoxY() { return PREVIEW_PANEL_Y; }
    /** Align both panel bottoms while the preview starts earlier than the left browser. */
    private int previewBoxH() { return recipeBoxY() + recipeBoxH() - previewBoxY(); }
    /** Filters may use the upper gap above Enabled/Disabled instead of truncating their labels. */
    private int filterToolbarX() { return recipeStateX(); }
    private int filterToolbarW() { return Math.max(4, width - filterToolbarX() - 8); }
    private int filterButtonW() { return Math.max(40, (filterToolbarW() - 12) / 4); }
    private int filterButtonX(int index) { return filterToolbarX() + index * (filterButtonW() + 4); }
    /** The 132 px craft diagram (3×3 grid, arrow, result) stays centered in the preview panel. */
    private int previewGridX() { return previewBoxX() + previewBoxW() / 2 - 66; }
    private int previewGridY() { return previewBoxY() + 60; }
    /** Use spare horizontal panel space before adding rows; cap the strip at ten cells. */
    private int previewVariantColumns() { return Math.min(10, Math.max(1, (previewBoxW() - 32) / 24)); }
    private int previewVariantGridX() {
        return previewBoxX() + previewBoxW() / 2 - previewVariantColumns() * 12 + 2;
    }
    private int previewVariantGridY() { return previewGridY() + 100; }
    private int previewVariantActionsX() { return previewBoxX() + previewBoxW() / 2 - 71; }
    private int previewVariantActionsY() {
        int count = selectedDetails == null ? 0 : Math.min(48, selectedDetails.variants().size());
        return previewVariantGridY() + ((count + previewVariantColumns() - 1) / previewVariantColumns()) * 24 + 6;
    }
    private int maxScroll(List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        return Math.max(0, shownRecipes.size() - visibleRows());
    }
    private boolean hasRecipeScrollbar(List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        return maxScroll(shownRecipes) > 0;
    }
    private boolean isOverRecipeScrollbar(double mouseX, double mouseY,
                                           List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        return hasRecipeScrollbar(shownRecipes)
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
        rebuildWidgets();
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

    /** Local ModMenu mode reads the default recipe data already loaded by the client. */
    public VanillaRecipesScreen(ConfigScreen parent, boolean localMode) {
        super(Component.translatable("customrecipe.vanilla.title"));
        this.parent = parent;
        this.localMode = localMode;
    }

    ConfigScreen configScreen() { return parent; }

    /** Builds the local browser cache once, after the client has finished initialising. */
    static void preloadAtStartup(net.minecraft.client.Minecraft client) {
        if (startupPreloadStarted || !startupRecipeCache.isEmpty() || client.getResourceManager() == null) return;
        startupPreloadStarted = true;
        try {
            VanillaRecipesScreen scanner = new VanillaRecipesScreen(null, true);
            VanillaRecipePage page = scanner.findLocalRecipes();
            startupRecipeCache = List.copyOf(page.recipes());
        } catch (RuntimeException exception) {
            startupPreloadStarted = false;
        }
    }

    @Override
    protected void init() {

        addRenderableOnly((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, parent.target(),
                parent.target().isWorld() ? parent.target().displayName() : "Global Library"));
        // Search shares the exact inner left/right margins of the recipe table.
        int clearSearchX = recipeContentRight() - 18;
        searchField = addRenderableWidget(new EditBox(font, recipeContentX(), LEFT_SEARCH_Y,
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
        }).bounds(clearSearchX, LEFT_SEARCH_Y, 18, 18).build());
        addRenderableOnly((ctx, mx, my, d) -> CustomRecipeSprites.draw(ctx,
                CustomRecipeSprites.REJECT, clearSearchX, LEFT_SEARCH_Y, 18, 18));

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
            int y = LEFT_ROWS_Y + i * ROW;
            boolean disabled = parent.disabledRecipes.contains(recipe.id());
            addRenderableWidget(Button.builder(recipeLabel(recipe), b -> selectRecipe(recipe))
                    .bounds(recipeLabelX(), y, recipeLabelW(), ROW).build());
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
            ctx.centeredText(font, saveLabel, width / 2, bottom + 7, 0xFFFFFFFF);
        });

        if (!searchStarted) resetSearch();
    }

    private void addPreviewControls() {
        if (selectedRecipe == null || selectedDetails == null || selectedDetails.variants().isEmpty()) return;
        int variantsX = previewVariantGridX();
        int variantColumns = previewVariantColumns();
        for (int index = 0; index < Math.min(48, selectedDetails.variants().size()); index++) {
            VanillaRecipeDetails.VariantPreview variant = selectedDetails.variants().get(index);
            int x = variantsX + (index % variantColumns) * 24;
            int y = previewVariantGridY() + (index / variantColumns) * 24;
            addRenderableWidget(Button.builder(Component.empty(), b -> {
                selectedVariant = variant;
                rebuildWidgets();
            }).bounds(x, y, 20, 20).build());
        }
        String material = selectedVariant == null ? selectedDetails.variants().getFirst().materialId() : selectedVariant.materialId();
        boolean blocked = isVariantDisabled(selectedRecipe.id(), material);
        addRenderableWidget(Button.builder(blocked ? Component.translatable("customrecipe.details.variant_disabled").withColor(0xFF5555)
                        : Component.translatable("customrecipe.details.disable_variant").withColor(0xFF5555), b -> {
                    toggleVariant(selectedRecipe.id(), material);
                    rebuildWidgets();
                }).bounds(previewVariantActionsX(), previewVariantActionsY(), 142, 20).build());
        boolean allDisabled = isRecipeDisabled(selectedRecipe.id());
        addRenderableWidget(Button.builder(allDisabled ? Component.translatable("customrecipe.details.variants_disabled").withColor(0xFF5555)
                        : Component.translatable("customrecipe.details.disable_variants").withColor(0xFF5555), b -> {
                    toggleAllVariants(selectedRecipe.id());
                    rebuildWidgets();
                }).bounds(previewVariantActionsX(), previewVariantActionsY() + 24, 142, 20).build());
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
        rebuildWidgets();
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
            rebuildWidgets();
            return;
        }
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput,
                statusFilter.name(), sourceFilter.name(), parent.disabledRecipes, 0);
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
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput,
                statusFilter.name(), sourceFilter.name(), parent.disabledRecipes, nextPage);
    }

    private VanillaRecipePage findLocalRecipes() {
        if (!startupRecipeCache.isEmpty()) return filterLocalRecipeCache();
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();
        Set<String> matchedIds = new HashSet<>();
        Map<Identifier, Resource> resources = minecraft.getResourceManager().listResources("recipe",
                id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> resource : resources.entrySet()) {
            try (var input = resource.getValue().open()) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                String recipeId = resource.getKey().getNamespace() + ":" + resource.getKey().getPath()
                        .substring("recipe/".length(), resource.getKey().getPath().length() - ".json".length());
                addLocalRecipe(matches, matchedIds, recipeId, json, loweredQuery);
            } catch (Exception ignored) {
                // A malformed optional resource is simply omitted from the local browser.
            }
        }

        // At the title screen ModMenu has not mounted server-data resources yet.
        // Vanilla recipes are still available in Minecraft's own JAR, so use it as a fallback.
        if (matches.isEmpty()) loadBundledVanillaRecipes(matches, matchedIds, loweredQuery);
        loadInstalledModRecipes(matches, matchedIds, loweredQuery);

        matches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
        // The first local scan is expensive. Retain its complete default result so
        // later search/filter interactions only inspect lightweight recipe metadata.
        if (query.isBlank() && matchIngredients && matchOutput && sourceFilter == SourceFilter.ALL) {
            startupRecipeCache = List.copyOf(matches);
        }
        return new VanillaRecipePage(matches, 0, matches.size());
    }

    private VanillaRecipePage filterLocalRecipeCache() {
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();
        for (VanillaRecipePage.VanillaRecipeInfo recipe : startupRecipeCache) {
            boolean vanillaRecipe = recipe.id().startsWith("minecraft:");
            if ((sourceFilter == SourceFilter.VANILLA && !vanillaRecipe)
                    || (sourceFilter == SourceFilter.MODDED && vanillaRecipe)) continue;
            boolean outputMatch = matchOutput && itemMatchesQuery(recipe.result(), loweredQuery);
            boolean ingredientMatch = matchIngredients && recipe.slots().stream()
                    .anyMatch(slot -> itemMatchesQuery(slot, loweredQuery));
            if (loweredQuery.isEmpty() || recipe.id().toLowerCase(Locale.ROOT).contains(loweredQuery)
                    || outputMatch || ingredientMatch) {
                matches.add(recipe);
            }
        }
        return new VanillaRecipePage(matches, 0, matches.size());
    }

    private void addLocalRecipe(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                String recipeId, String json, String loweredQuery) {
        if (!json.contains("crafting_")) return;
        boolean vanillaRecipe = recipeId.startsWith("minecraft:");
        if ((sourceFilter == SourceFilter.VANILLA && !vanillaRecipe)
                || (sourceFilter == SourceFilter.MODDED && vanillaRecipe)) return;
        String resultId = findResultId(json, recipeId);
        boolean outputMatch = matchOutput && itemMatchesQuery(resultId, loweredQuery);
        boolean ingredientMatch = matchIngredients && localIngredientsMatch(json, loweredQuery);
        if ((loweredQuery.isEmpty() || recipeId.toLowerCase(Locale.ROOT).contains(loweredQuery)
                || outputMatch || ingredientMatch) && matchedIds.add(recipeId)) {
            RecipeLayout layout = findLocalRecipeLayout(json);
            boolean special = isSpecialRecipe(json);
            matches.add(new VanillaRecipePage.VanillaRecipeInfo(recipeId, resultId, toPreviewSlots(layout),
                    layout.width(), layout.height(), layout.shapeless(), special));
        }
    }

    private void loadBundledVanillaRecipes(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                           String loweredQuery) {
        try (JarFile jar = minecraftJar()) {
            if (jar == null) return;
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String path = entry.getName();
                if (!path.startsWith("data/minecraft/recipe/") || !path.endsWith(".json")) continue;
                try (var input = jar.getInputStream(entry)) {
                    String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    String recipeId = "minecraft:" + path.substring("data/minecraft/recipe/".length(), path.length() - ".json".length());
                    addLocalRecipe(matches, matchedIds, recipeId, json, loweredQuery);
                }
            }
        } catch (Exception ignored) {
            // Normal in unusual launchers that do not expose a Minecraft JAR code source.
        }
    }

    /** ModMenu can open before datapack recipes are mounted, so read installed mod archives directly. */
    private void loadInstalledModRecipes(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                         String loweredQuery) {
        Set<Path> scannedArchives = new HashSet<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            List<Path> originPaths;
            try {
                originPaths = mod.getOrigin().getPaths();
            } catch (RuntimeException ignored) {
                // Nested Fabric modules do not always expose a filesystem archive.
                continue;
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
        // The staged state is already available locally. Rebuild visible rows
        // without issuing a new full recipe scan/query for one toggle click.
        scroll = Math.min(scroll, maxScroll(filteredRecipes()));
        rebuildWidgets();
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
        rebuildWidgets();
    }

    void applyDetails(VanillaRecipeDetails details) {
        if (selectedRecipe == null || !selectedRecipe.id().equals(details.recipeId())) return;
        selectedDetails = details;
        selectedVariant = details.variants().isEmpty() ? null : details.variants().getFirst();
        rebuildWidgets();
    }

    void requestDetails(VanillaRecipeDetailsScreen screen, String recipeId) {
        if (localMode) {
            minecraft.execute(() -> screen.applyDetails(findLocalRecipeDetails(recipeId)));
        } else {
            ClientServerConfigNetworking.requestVanillaDetails(recipeId);
        }
    }

    /** Mirrors the server variant query using the vanilla JSON and client item tags. */
    private fr.zazac1.customrecipe.VanillaRecipeDetails findLocalRecipeDetails(String recipeId) {
        Identifier id = Identifier.tryParse(recipeId);
        if (id == null) return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, List.of());
        Identifier resourceId = Identifier.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
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

    private Optional<String> readLocalRecipeJson(Identifier resourceId) {
        try {
            var resource = minecraft.getResourceManager().getResource(resourceId).orElse(null);
            if (resource != null) {
                try (var input = resource.open()) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            try (JarFile jar = minecraftJar()) {
                if (jar == null) return Optional.empty();
                var entry = jar.getJarEntry("data/" + resourceId.getNamespace() + "/" + resourceId.getPath());
                if (entry == null) return Optional.empty();
                try (var input = jar.getInputStream(entry)) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private JarFile minecraftJar() throws Exception {
        var source = minecraft.getClass().getProtectionDomain().getCodeSource();
        if (source == null) return null;
        Path path = Path.of(source.getLocation().toURI());
        return java.nio.file.Files.isRegularFile(path) ? new JarFile(path.toFile()) : null;
    }

    private List<String> localIngredientChoices(JsonElement element) {
        TreeSet<String> choices = new TreeSet<>();
        collectLocalIngredientChoices(element, choices);
        return new ArrayList<>(choices);
    }

    private void collectLocalIngredientChoices(JsonElement element, Set<String> choices) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(raw.substring(1));
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
            Identifier tagId = Identifier.tryParse(object.get("tag").getAsString());
            if (tagId != null) collectLocalTagItems(tagId, choices, new HashSet<>());
        }
    }

    /** Expands item tags before matching, so tag ingredients match all their items. */
    private boolean localIngredientsMatch(String json, String loweredQuery) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<JsonElement> ingredients = new ArrayList<>();
            if (root.has("ingredients")) ingredients.add(root.get("ingredients"));
            if (root.has("key") && root.get("key").isJsonObject()) {
                ingredients.addAll(root.getAsJsonObject("key").asMap().values());
            }
            for (JsonElement ingredient : ingredients) {
                for (String itemId : localIngredientChoices(ingredient)) if (itemMatchesQuery(itemId, loweredQuery)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    /** Reads tag JSON too, so variants are available from ModMenu before joining a world. */
    private void collectLocalTagItems(Identifier tagId, Set<String> choices, Set<Identifier> visited) {
        if (!visited.add(tagId)) return;
        try {
            for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
                choices.add(BuiltInRegistries.ITEM.getKey(entry.value()).toString());
            }
            if (!choices.isEmpty()) return;
        } catch (IllegalStateException ignored) {
            // At the title screen tags may not be bound yet; use their JSON below.
        }
        Identifier tagResource = Identifier.fromNamespaceAndPath(tagId.getNamespace(), "tags/item/" + tagId.getPath() + ".json");
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
                    Identifier nested = Identifier.tryParse(raw.substring(1));
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
        return Math.max(1, (height - LEFT_ROWS_Y - 32) / ROW);
    }

    private Component recipeLabel(VanillaRecipePage.VanillaRecipeInfo recipe) {
        return Component.literal(recipe.result()).withColor(recipe.special() ? 0x77BBFF : 0xFFFFFF);
    }

    private String itemName(String id) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(id));
        return item == null || item == Items.AIR ? shortId(id) : ClientItemStacks.fromItem(item).getHoverName().getString();
    }

    private boolean itemMatchesQuery(String itemId, String loweredQuery) {
        return itemId.toLowerCase(Locale.ROOT).contains(loweredQuery)
                || itemName(itemId).toLowerCase(Locale.ROOT).contains(loweredQuery);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        renderPanels(ctx);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (loading && recipes.isEmpty()) {
            ctx.text(font, Component.translatable("customrecipe.vanilla.loading"), 8, LEFT_ROWS_Y + 2, 0xBBBBBB, false);
            return;
        }

        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        // Deliberately concise: the toolbar occupies the same upper area as this status line.
        String countText = "Found " + total + " recipes";
        ctx.text(font, Component.literal(countText), 8, LEFT_HEADER_Y, 0xFFFFEE88, false);
        if (shownRecipes.isEmpty()) {
            ctx.text(font, Component.translatable("customrecipe.vanilla.none"), 8, LEFT_ROWS_Y + 2, 0xFFBBBBBB, false);
        }
        for (int i = 0; i < visibleRows() && scroll + i < shownRecipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = shownRecipes.get(scroll + i);
            int y = LEFT_ROWS_Y + i * ROW;
            int rowColor = recipe.special() ? 0x22335566
                    : parent.disabledRecipes.contains(recipe.id()) ? 0x44550000 : 0x22005500;
            ctx.fill(recipeListX(), y, recipeScrollbarX() - 4, y + ROW, rowColor);
            var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(recipe.result()));
            if (item != null && item != Items.AIR) ctx.item(ClientItemStacks.fromItem(item), recipeListX() + 2, y + 2);
        }
        renderPreview(ctx);
        if (hasRecipeScrollbar(shownRecipes)) renderRecipeScrollbar(ctx, mouseX, mouseY, shownRecipes);
        if (loading) ctx.text(font, Component.translatable("customrecipe.vanilla.loading_more"), 8, height - 42, 0xFFBBBBBB, false);
    }

    private void renderRecipeScrollbar(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                       List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        int trackX = recipeScrollbarX();
        int trackY = recipeScrollbarY();
        int trackH = recipeScrollbarH();
        int maxScroll = maxScroll(shownRecipes);
        int thumbH = 15;
        int thumbY = trackY + (trackH - thumbH) * scroll / maxScroll;
        // Shift the 12 px thumb right one pixel; trim the left track column so its
        // background still ends against the panel frame without a visible right sliver.
        ctx.fill(trackX, trackY, trackX + SCROLLBAR_TRACK_W, trackY + trackH, SCROLLBAR_TRACK_COLOR);
        CustomRecipeSprites.draw(ctx,
                draggingRecipeScrollbar ? CustomRecipeSprites.SCROLLER_ACTIVE : CustomRecipeSprites.SCROLLER_IDLE,
                trackX, thumbY, SCROLLBAR_W, thumbH);
    }

    private void renderPanels(GuiGraphicsExtractor ctx) {
        drawPanel(ctx, recipeBoxX(), recipeBoxY(), recipeBoxW(), recipeBoxH(), 0x99141A20, 0xFF40506A);
        drawPanel(ctx, previewBoxX(), previewBoxY(), previewBoxW(), previewBoxH(), 0x99141A20, 0xFF40506A);
    }

    private void renderPreview(GuiGraphicsExtractor ctx) {
        int centerX = previewBoxX() + previewBoxW() / 2;
        if (selectedRecipe == null) {
            ctx.centeredText(font, Component.literal("Select a recipe to preview"), centerX,
                    previewBoxY() + 14, 0xFFAAAAAA);
            return;
        }
        ctx.centeredText(font, Component.literal("Recipe Preview"), centerX, previewBoxY() + 10, 0xFFFFFFFF);
        ctx.centeredText(font, Component.literal(selectedRecipe.id()), centerX, previewBoxY() + 26, 0xFFAAAAAA);

        int gridX = previewGridX();
        int gridY = previewGridY();
        List<String> slots = selectedVariant == null ? selectedRecipe.slots() : selectedVariant.slots();
        for (int slot = 0; slot < 9; slot++) {
            int x = gridX + (slot % 3) * 24;
            int y = gridY + (slot / 3) * 24;
            ctx.fill(x, y, x + 20, y + 20, 0xFF303030);
            if (slot < slots.size()) drawPreviewItem(ctx, slots.get(slot), x + 2, y + 2);
        }
        ctx.text(font, "->", gridX + 82, gridY + 27, 0xFFFFFFFF, true);
        ctx.fill(gridX + 108, gridY + 24, gridX + 132, gridY + 48, 0xFF303030);
        drawPreviewItem(ctx, selectedRecipe.result(), gridX + 112, gridY + 28);
        String layout = selectedRecipe.shapeless() ? "Shapeless: JSON ingredient order"
                : "Shaped: " + selectedRecipe.gridWidth() + "x" + selectedRecipe.gridHeight() + " pattern";
        // The recipe ID above is the one canonical name in this compact view.
        // Keeping the format beneath it avoids a second, potentially overflowing output name.
        ctx.centeredText(font, Component.literal(layout), centerX, previewBoxY() + 42, 0xFFAAAAAA);

        int variantsX = previewVariantGridX();
        if (selectedDetails == null) {
            ctx.centeredText(font, Component.literal("Loading variants…"), centerX,
                    previewVariantGridY() - 18, 0xFFAAAAAA);
            return;
        }
        if (selectedDetails.variants().isEmpty()) {
            // This occupies the variants section rather than competing with the craft summary.
            ctx.centeredText(font, Component.literal("No interchangeable material"), centerX,
                    previewVariantGridY() - 18, 0xFFAAAAAA);
            return;
        }
        ctx.centeredText(font, Component.literal("Material variants"), centerX,
                previewVariantGridY() - 18, 0xFFFFFFFF);
        int variantColumns = previewVariantColumns();
        for (int index = 0; index < Math.min(48, selectedDetails.variants().size()); index++) {
            VanillaRecipeDetails.VariantPreview variant = selectedDetails.variants().get(index);
            int x = variantsX + (index % variantColumns) * 24;
            int y = previewVariantGridY() + (index / variantColumns) * 24;
            boolean disabled = isRecipeDisabled(selectedRecipe.id())
                    || isVariantDisabled(selectedRecipe.id(), variant.materialId());
            int border = disabled ? 0xFFFF3333 : 0xFF22DD55;
            int background = disabled ? 0xAA4A0000 : 0xAA004A18;
            ctx.fill(x, y, x + 20, y + 20, background);
            drawPreviewBorder(ctx, x, y, border);
            if (selectedVariant != null && selectedVariant.materialId().equals(variant.materialId())) {
                drawPreviewSelection(ctx, x, y);
            }
            drawPreviewItem(ctx, variant.materialId(), x + 2, y + 2);
        }
    }

    private void drawPreviewItem(GuiGraphicsExtractor ctx, String id, int x, int y) {
        if (id == null || id.isBlank() || id.startsWith("#")) return;
        var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(id));
        if (item != null && item != Items.AIR) ctx.item(ClientItemStacks.fromItem(item), x, y);
    }

    private void drawPreviewBorder(GuiGraphicsExtractor ctx, int x, int y, int color) {
        ctx.fill(x - 2, y - 2, x + 22, y, color);
        ctx.fill(x - 2, y + 20, x + 22, y + 22, color);
        ctx.fill(x - 2, y, x, y + 20, color);
        ctx.fill(x + 20, y, x + 22, y + 20, color);
    }

    private void drawPreviewSelection(GuiGraphicsExtractor ctx, int x, int y) {
        int color = 0xFFFFFFFF;
        ctx.fill(x - 3, y - 3, x + 5, y - 1, color);
        ctx.fill(x - 3, y - 3, x - 1, y + 5, color);
        ctx.fill(x + 15, y - 3, x + 23, y - 1, color);
        ctx.fill(x + 21, y - 3, x + 23, y + 5, color);
        ctx.fill(x - 3, y + 21, x + 5, y + 23, color);
        ctx.fill(x - 3, y + 15, x - 1, y + 23, color);
        ctx.fill(x + 15, y + 21, x + 23, y + 23, color);
        ctx.fill(x + 21, y + 15, x + 23, y + 23, color);
    }

    private void drawPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.horizontalLine(x, x + w - 1, y, border);
        ctx.horizontalLine(x, x + w - 1, y + h - 1, border);
        ctx.verticalLine(x, y, y + h - 1, border);
        ctx.verticalLine(x + w - 1, y, y + h - 1, border);
    }

    private String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        int maxScroll = maxScroll(shownRecipes);
        int oldScroll = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        if (scroll != oldScroll) rebuildWidgets();
        if (scroll + visibleRows() >= shownRecipes.size() - 3) loadMore();
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean focused) {
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        if (click.button() == 0 && isOverRecipeScrollbar(click.x(), click.y(), shownRecipes)) {
            draggingRecipeScrollbar = true;
            updateRecipeScrollbar(click.y(), shownRecipes);
            return true;
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingRecipeScrollbar) {
            updateRecipeScrollbar(click.y(), filteredRecipes());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        draggingRecipeScrollbar = false;
        return super.mouseReleased(click);
    }

    private void updateRecipeScrollbar(double mouseY, List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        int maxScroll = maxScroll(shownRecipes);
        if (maxScroll <= 0) return;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseY - recipeScrollbarY()) / Math.max(1, recipeScrollbarH() - 1)));
        int next = (int) Math.round(progress * maxScroll);
        if (next != scroll) {
            scroll = next;
            if (scroll + visibleRows() >= shownRecipes.size() - 3) loadMore();
            rebuildWidgets();
        }
    }

    @Override public boolean isPauseScreen() { return true; }

    /** Recipe states belong to ConfigScreen and are only persisted from there. */
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
