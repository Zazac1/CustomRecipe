package fr.zazac1.customrecipe.client;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import fr.zazac1.customrecipe.VanillaRecipePage;
import net.minecraftforge.fml.ModList;
import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import net.minecraft.client.gui.screen.Screen;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.EditBox;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipe;
import net.minecraft.util.registry.Registry;


import net.minecraft.resources.IResource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
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
public class VanillaRecipesScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    /** Vanilla 1.20.1 has 836 crafting recipes (shaped, shapeless and special). */
    private static final int COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM = 800;
    /** Small enough to keep the screen responsive while the bar visibly advances. */
    private static final int LOCAL_RECIPES_PER_TICK = 12;
    /** Reused for searches/reopening the screen; it is invalidated when the world recipe set changes. */
    private static List<VanillaRecipePage.VanillaRecipeInfo> localRecipeCache = fr.zazac1.customrecipe.Java8.list();
    private static Map<String, String> localRecipeJsonCache = fr.zazac1.customrecipe.Java8.map();
    private static net.minecraft.server.MinecraftServer localRecipeCacheServer;
    private static int localRecipeCacheFingerprint;
    /** Archive caches work both in the menu and in ForgeGradle worlds with an incomplete recipe manager. */
    private static boolean localRecipeCacheUsesServerRecipes;
    private static boolean startupPreloadInProgress;
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
    private VanillaRecipePage.VanillaRecipeInfo selectedRecipe;
    private VanillaRecipeDetails selectedDetails;
    private VanillaRecipeDetails.VariantPreview selectedVariant;
    private final List<LocalRecipeSource> localScanQueue = new ArrayList<>();
    private final Set<JarFile> localScanArchives = new HashSet<>();
    private final Set<String> scheduledLocalRecipeIds = new HashSet<>();
    private List<VanillaRecipePage.VanillaRecipeInfo> localScanMatches = fr.zazac1.customrecipe.Java8.list();
    private Set<String> localScanMatchedIds = fr.zazac1.customrecipe.Java8.set();
    /** Complete metadata retained alongside the current filtered scan results. */
    private List<VanillaRecipePage.VanillaRecipeInfo> localScanCacheMatches = fr.zazac1.customrecipe.Java8.list();
    private Set<String> localScanCacheMatchedIds = fr.zazac1.customrecipe.Java8.set();
    private net.minecraft.server.MinecraftServer localScanServer;
    private String localScanQuery = "";
    private int localScanProcessed;
    private boolean localScanCanPopulateCache;
    private Map<String, String> localScanJsonById = fr.zazac1.customrecipe.Java8.map();
    /** JSON captured by the synchronous startup scan for ingredient searches served from cache. */
    private Map<String, String> collectedLocalRecipeJsonById;

    private int recipeContentX() { return recipeBoxX() + 2; }
    private int recipeContentRight() { return recipeBoxX() + recipeBoxW() - 2; }
    /** Keep the 18 px icon inside the panel, clear of its right-hand frame. */
    private int clearSearchX() { return recipeContentRight() - 19; }
    private int recipeListX() { return recipeContentX(); }
    private int recipeLabelX() { return recipeListX() + 22; }
    private int recipeLabelW() { return Math.min(360, Math.max(220, width / 3)); }
    private int recipeStateX() { return recipeLabelX() + recipeLabelW() + 4; }
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
    private int maxScroll(List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) { return Math.max(0, shownRecipes.size() - visibleRows()); }
    private boolean hasRecipeScrollbar(List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) { return maxScroll(shownRecipes) > 0; }
    private boolean isOverRecipeScrollbar(double mouseX, double mouseY, List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        return hasRecipeScrollbar(shownRecipes)
                && mouseX >= recipeScrollbarX() && mouseX < recipeScrollbarX() + SCROLLBAR_TRACK_W
                && mouseY >= recipeScrollbarY() && mouseY < recipeScrollbarY() + recipeScrollbarH();
    }

    /** Applies the local status/special view without changing the server search result. */
    private List<VanillaRecipePage.VanillaRecipeInfo> filteredRecipes() {
        if (statusFilter == StatusFilter.ALL) return recipes;

        List<VanillaRecipePage.VanillaRecipeInfo> filtered = new ArrayList<>();
        for (VanillaRecipePage.VanillaRecipeInfo recipe : recipes) {
            boolean disabled = parent.isDefaultRecipeDisabled(recipe.id());
            if ((statusFilter == StatusFilter.DISABLED && disabled && !recipe.special())
                    || (statusFilter == StatusFilter.ENABLED && !disabled && !recipe.special())
                    || (statusFilter == StatusFilter.SPECIAL && recipe.special())) {
                filtered.add(recipe);
            }
        }
        return filtered;
    }

    private ITextComponent statusFilterLabel() {
        return switch (statusFilter) {
            case ALL -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.status_all");
            case ENABLED -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.status_enabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0x55FF55)));
            case DISABLED -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.status_disabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555)));
            case SPECIAL -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.status_special").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0x77BBFF)));
        };
    }

    private ITextComponent sourceFilterLabel() {
        return switch (sourceFilter) {
            case ALL -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.show_all");
            case MODDED -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.show_modded");
            case VANILLA -> fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.show_vanilla");
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

    /** Local ModMenu mode reads the default recipe data already loaded by the minecraft. */
    public VanillaRecipesScreen(ConfigScreen parent, boolean localMode) {
        super(fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.title"));
        this.parent = parent;
        this.localMode = localMode;
    }

    /**
     * Builds the default local-recipe cache once after Minecraft is ready. This
     * intentionally runs on the client thread because resource packs and mod
     * registries are not thread-safe; users can disable it in settings.
     */
    static void preloadAtStartup(net.minecraft.client.Minecraft client) {
        net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
        VanillaRecipesScreen scanner = new VanillaRecipesScreen(new ConfigScreen(null), true);
        scanner.minecraft = client;
        boolean completeServerRecipeSet = server != null && scanner.hasCompleteCraftingRecipeSet(server);
        int fingerprint = completeServerRecipeSet ? scanner.localRecipeFingerprint(server) : 0;
        if (startupPreloadInProgress || isLocalRecipeCacheValid(server, fingerprint, completeServerRecipeSet)) return;

        startupPreloadInProgress = true;
        try {
            scanner.collectedLocalRecipeJsonById = new HashMap<>();
            VanillaRecipePage page = scanner.findLocalRecipes();
            localRecipeCache = fr.zazac1.customrecipe.Java8.listCopy(page.recipes());
            localRecipeJsonCache = fr.zazac1.customrecipe.Java8.mapCopy(scanner.collectedLocalRecipeJsonById);
            localRecipeCacheServer = completeServerRecipeSet ? server : null;
            localRecipeCacheFingerprint = fingerprint;
            localRecipeCacheUsesServerRecipes = completeServerRecipeSet;
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Preloaded {} recipes for {}.", localRecipeCache.size(),
                    completeServerRecipeSet ? "the current world" : "the local archive cache");
        } catch (RuntimeException exception) {
            CustomRecipeMod.LOGGER.warn("[Custom Recipe] Startup recipe preload was skipped: {}", exception.getMessage());
        } finally {
            // A failed early attempt must be allowed to retry once resources are ready.
            startupPreloadInProgress = false;
        }
    }

    private static boolean isLocalRecipeCacheValid(net.minecraft.server.MinecraftServer server,
                                                   int fingerprint, boolean completeServerRecipeSet) {
        if (localRecipeCache.isEmpty()) return false;
        if (!completeServerRecipeSet) return !localRecipeCacheUsesServerRecipes;
        return localRecipeCacheUsesServerRecipes && localRecipeCacheServer == server
                && localRecipeCacheFingerprint == fingerprint;
    }

    ConfigScreen configScreen() { return parent; }

    @Override
    protected void init() {

        addLegacyRenderable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, parent.target(),
                parent.target().isWorld() ? parent.target().displayName() : "Global Library"));
        int clearSearchX = clearSearchX();
        searchField = addRenderableWidget(new EditBox(font, recipeContentX(), SEARCH_Y,
                clearSearchX - recipeContentX(), 18,
                fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.search_hint")));
        searchField.setValue(query);
        searchField.setResponder(this::onQueryChanged);
        if (restoreSearchFocus) {
            setFocused(searchField);
            restoreSearchFocus = false;
        }

        addLegacyRenderable((ctx, mx, my, d) -> {
            if (mx >= clearSearchX && mx < clearSearchX + 18 && my >= SEARCH_Y && my < SEARCH_Y + 18) {
                ctx.fill(clearSearchX, SEARCH_Y, clearSearchX + 18, SEARCH_Y + 18, 0x443355BB);
            }
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.REJECT, clearSearchX, SEARCH_Y, 18, 18);
        });

        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.ingredient", fr.zazac1.customrecipe.Texts.translatable(matchIngredients ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), b -> {
            matchIngredients = !matchIngredients;
            resetSearch();
        }).bounds(filterButtonX(0), PREVIEW_TOOLBAR_Y, filterButtonW(), 18).build());
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.output", fr.zazac1.customrecipe.Texts.translatable(matchOutput ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), b -> {
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
            boolean disabled = parent.isDefaultRecipeDisabled(recipe.id());
            addRenderableWidget(Button.builder(recipeLabel(recipe), b -> selectRecipe(recipe))
                    .bounds(recipeLabelX(), y, recipeLabelW(), ROW).build());
            addRenderableWidget(Button.builder(disabled ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.state.disabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555)))
                            : fr.zazac1.customrecipe.Texts.translatable("customrecipe.state.enabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0x55FF55))), b -> toggle(recipe.id()))
                    .bounds(recipeStateX(), y, RECIPE_STATE_W, ROW).build());
        }

        addPreviewControls();

        int bottom = height - 26;
        String saveLabel = fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.save").getString();
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.empty(), b -> parent.saveFromSubmenu())
                .bounds(width / 2 - 100, bottom, 200, 22).build());
        addLegacyRenderable((ctx, mouseX, mouseY, delta) -> {
            int iconX = width / 2 - font.width(saveLabel) / 2 - 20;
            CustomRecipeSprites.draw(ctx, CustomRecipeSprites.SAVE, iconX, bottom + 3, 16, 16);
            ctx.drawCenteredString(font, saveLabel, width / 2, bottom + 7, 0xFFFFFFFF);
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
            addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.empty(), b -> {
                selectedVariant = variant;
                rebuildWidgets();
            }).bounds(x, y, 20, 20).build());
        }
        String material = selectedVariant == null ? selectedDetails.variants().get(0).materialId() : selectedVariant.materialId();
        boolean blocked = isVariantDisabled(selectedRecipe.id(), material);
        addRenderableWidget(Button.builder(blocked ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.variant_disabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555)))
                        : fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.disable_variant").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555))), b -> {
                    toggleVariant(selectedRecipe.id(), material);
                    rebuildWidgets();
                }).bounds(previewVariantActionsX(), previewVariantActionsY(), 142, 20).build());
        boolean allDisabled = isRecipeDisabled(selectedRecipe.id());
        addRenderableWidget(Button.builder(allDisabled ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.variants_disabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555)))
                        : fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.disable_variants").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555))), b -> {
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
            startLocalSearch();
            return;
        }
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput,
                statusFilter.name(), sourceFilter.name(), parent.effectiveDisabledRecipes(), 0);
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
        if (localMode && loading) processLocalSearch();
    }

    private void loadMore() {
        if (localMode || loading || recipes.size() >= total) return;
        loading = true;
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput,
                statusFilter.name(), sourceFilter.name(), parent.effectiveDisabledRecipes(), nextPage);
    }

    private VanillaRecipePage findLocalRecipes() {
        var localServer = minecraft.getSingleplayerServer();
        // A loaded integrated server is the authoritative source: unlike the
        // client resource manager it includes every vanilla, modded and
        // datapack crafting recipe currently active in this world.
        if (localServer != null && hasCompleteCraftingRecipeSet(localServer)) {
            return findLoadedLocalRecipes(localServer);
        }
        if (localServer != null) {
            // ForgeGradle's remapped development archive contains only a
            // reduced recipe resource set. Fall back to the original vanilla
            // client archive instead of presenting its incomplete list.
            CustomRecipeMod.LOGGER.warn("[Custom Recipe] Integrated server exposes an incomplete crafting recipe set; using vanilla archive fallback.");
        }

        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();
        Set<String> matchedIds = new HashSet<>();
        Map<ResourceLocation, IResource> resources = fr.zazac1.customrecipe.client.compat.LegacyResources.listResources(minecraft.getResourceManager(), "recipes",
                id -> id.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, IResource> resource : resources.entrySet()) {
            try (var input = resource.getValue().getInputStream()) {
                String json = new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8);
                String recipeId = resource.getKey().getNamespace() + ":" + resource.getKey().getPath()
                        .substring("recipes/".length(), resource.getKey().getPath().length() - ".json".length());
                addLocalRecipe(matches, matchedIds, recipeId, json, loweredQuery);
            } catch (Exception ignored) {
                // A malformed optional resource is simply omitted from the local browser.
            }
        }

        // The ForgeGradle remapped development archive can expose only 177
        // crafting JSON files through the resource manager. Merge the original
        // vanilla client archive even when resources are present; matchedIds
        // keeps normal production launches free of duplicates.
        loadBundledVanillaRecipes(matches, matchedIds, loweredQuery);
        loadInstalledModRecipes(matches, matchedIds, loweredQuery);

        matches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
        return new VanillaRecipePage(matches, 0, matches.size());
    }

    /**
     * Queues local JSON/loaded recipes instead of parsing the whole archive in
     * one frame. The progress bar can therefore update while the browser is
     * being populated.
     */
    private void startLocalSearch() {
        closeLocalScanArchives();
        localScanQueue.clear();
        scheduledLocalRecipeIds.clear();
        localScanMatches = new ArrayList<>();
        localScanMatchedIds = new HashSet<>();
        localScanCacheMatches = new ArrayList<>();
        localScanCacheMatchedIds = new HashSet<>();
        localScanQuery = query.trim().toLowerCase(Locale.ROOT);
        localScanProcessed = 0;
        localScanServer = minecraft.getSingleplayerServer();
        boolean completeServerRecipeSet = localScanServer != null && hasCompleteCraftingRecipeSet(localScanServer);
        int fingerprint = completeServerRecipeSet ? localRecipeFingerprint(localScanServer) : 0;
        if (isLocalRecipeCacheValid(localScanServer, fingerprint, completeServerRecipeSet)) {
            recipes.addAll(filterCachedLocalRecipes(localRecipeCache));
            total = recipes.size();
            nextPage = 1;
            loading = false;
            rebuildWidgets();
            return;
        }
        // Always retain unfiltered metadata. The first visit may itself be a
        // search, but subsequent searches must not reopen every mod archive.
        localScanCanPopulateCache = true;
        localScanJsonById = new HashMap<>();

        if (completeServerRecipeSet) {
            for (net.minecraft.item.crafting.IRecipe<?> recipe : localScanServer.getRecipeManager().getRecipes()) {
                if (recipe instanceof ICraftingRecipe && !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) {
                    localScanQueue.add(LocalRecipeSource.loaded(recipe));
                }
            }
        } else {
            if (localScanServer != null) {
                CustomRecipeMod.LOGGER.warn("[Custom Recipe] Integrated server exposes an incomplete crafting recipe set; using vanilla archive fallback.");
            }
            queueResourceManagerRecipes();
            try {
                JarFile vanilla = minecraftJar();
                if (vanilla != null) queueArchiveRecipes(vanilla, true);
            } catch (Exception ignored) {
                // The regular resource-manager list remains usable in unusual launchers.
            }
            queueInstalledModRecipes();
        }
        rebuildWidgets();
    }

    private int localRecipeFingerprint(net.minecraft.server.MinecraftServer server) {
        if (server == null) return 0;
        // RecipeManager does not promise a stable iteration order. Keep the
        // fingerprint order-independent so an unchanged world keeps its cache.
        int count = 0;
        int sum = 0;
        int rotatedXor = 0;
        for (net.minecraft.item.crafting.IRecipe<?> recipe : server.getRecipeManager().getRecipes()) {
            int hash = recipe.getId().hashCode();
            count++;
            sum += hash;
            rotatedXor ^= Integer.rotateLeft(hash, hash & 31);
        }
        return 31 * (31 * count + sum) + rotatedXor;
    }

    /** Cached recipes are complete; query/source/status filters are reapplied instantly in memory. */
    private List<VanillaRecipePage.VanillaRecipeInfo> filterCachedLocalRecipes(
            List<VanillaRecipePage.VanillaRecipeInfo> cachedRecipes) {
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> filtered = new ArrayList<>();
        for (VanillaRecipePage.VanillaRecipeInfo recipe : cachedRecipes) {
            boolean sourceMatch = sourceFilter == SourceFilter.ALL
                    || sourceFilter == SourceFilter.VANILLA && recipe.id().startsWith("minecraft:")
                    || sourceFilter == SourceFilter.MODDED && !recipe.id().startsWith("minecraft:");
            boolean disabled = parent.isDefaultRecipeDisabled(recipe.id());
            boolean statusMatch = switch (statusFilter) {
                case ALL -> true;
                case ENABLED -> !disabled && !recipe.special();
                case DISABLED -> disabled && !recipe.special();
                case SPECIAL -> recipe.special();
            };
            String cachedJson = localRecipeJsonCache.get(recipe.id());
            boolean searchMatch = loweredQuery.isEmpty() || recipe.id().toLowerCase(Locale.ROOT).contains(loweredQuery)
                    || matchOutput && itemMatchesQuery(recipe.result(), loweredQuery)
                    || matchIngredients && cachedJson != null && cachedJson.toLowerCase(Locale.ROOT).contains(loweredQuery)
                    || matchIngredients && recipe.slots().stream()
                    .anyMatch(ingredient -> itemMatchesQuery(ingredient, loweredQuery));
            if (sourceMatch && statusMatch && searchMatch) filtered.add(recipe);
        }
        return filtered;
    }

    private void queueResourceManagerRecipes() {
        for (ResourceLocation resourceId : fr.zazac1.customrecipe.client.compat.LegacyResources.listResources(minecraft.getResourceManager(), "recipes",
                id -> id.getPath().endsWith(".json")).keySet()) {
            String path = resourceId.getPath();
            String recipeId = resourceId.getNamespace() + ":" + path.substring("recipes/".length(), path.length() - ".json".length());
            queueLocalRecipe(LocalRecipeSource.resource(recipeId, resourceId));
        }
    }

    private void queueInstalledModRecipes() {
        for (var modFile : ModList.get().getModFiles()) {
            try {
                Path archivePath = modFile.getFile().getFilePath().toAbsolutePath().normalize();
                if (!Files.isRegularFile(archivePath) || !archivePath.getFileName().toString().endsWith(".jar")) continue;
                queueArchiveRecipes(new JarFile(archivePath.toFile()), false);
            } catch (Exception ignored) {
                // Virtual and nested modules need not expose a readable archive.
            }
        }
    }

    private void queueArchiveRecipes(JarFile archive, boolean vanillaOnly) {
        boolean used = false;
        try {
            Enumeration<java.util.jar.JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName();
                if (!path.startsWith("data/") || !path.endsWith(".json")) continue;
                int namespaceEnd = path.indexOf('/', "data/".length());
                int recipeStart = namespaceEnd + 1;
                if (namespaceEnd <= "data/".length() || !path.startsWith("recipes/", recipeStart)) continue;
                String namespace = path.substring("data/".length(), namespaceEnd);
                if (vanillaOnly && !namespace.equals("minecraft")) continue;
                String recipeId = namespace + ":" + path.substring(recipeStart + "recipes/".length(), path.length() - ".json".length());
                if (recipeId.startsWith(CustomRecipeMod.MOD_ID + ":")) continue;
                if (queueLocalRecipe(LocalRecipeSource.archive(recipeId, archive, path))) used = true;
            }
            if (used) localScanArchives.add(archive); else archive.close();
        } catch (Exception ignored) {
            try { archive.close(); } catch (Exception ignoredClose) {}
        }
    }

    private boolean queueLocalRecipe(LocalRecipeSource source) {
        if (!scheduledLocalRecipeIds.add(source.recipeId())) return false;
        localScanQueue.add(source);
        return true;
    }

    private void processLocalSearch() {
        int batchEnd = Math.min(localScanProcessed + LOCAL_RECIPES_PER_TICK, localScanQueue.size());
        while (localScanProcessed < batchEnd) {
            LocalRecipeSource source = localScanQueue.get(localScanProcessed++);
            if (source.loadedRecipe() != null) {
                addLoadedLocalRecipe(source.loadedRecipe(), localScanServer, localScanQuery, localScanMatches);
                addLoadedLocalRecipeToCache(source.loadedRecipe(), localScanServer,
                        localScanCacheMatches, localScanCacheMatchedIds);
                continue;
            }
            String json = readQueuedRecipe(source);
            if (json != null) {
                if (localScanCanPopulateCache) localScanJsonById.put(source.recipeId(), json);
                addLocalRecipe(localScanMatches, localScanMatchedIds, source.recipeId(), json, localScanQuery);
                addLocalRecipeToCache(localScanCacheMatches, localScanCacheMatchedIds, source.recipeId(), json);
            }
        }
        recipes.clear();
        recipes.addAll(localScanMatches);
        total = recipes.size();
        if (localScanProcessed >= localScanQueue.size()) {
            recipes.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
            total = recipes.size();
            if (localScanCanPopulateCache) {
                localScanCacheMatches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
                localRecipeCache = fr.zazac1.customrecipe.Java8.listCopy(localScanCacheMatches);
                localRecipeJsonCache = fr.zazac1.customrecipe.Java8.mapCopy(localScanJsonById);
                localRecipeCacheUsesServerRecipes = localScanServer != null
                        && hasCompleteCraftingRecipeSet(localScanServer);
                localRecipeCacheServer = localRecipeCacheUsesServerRecipes ? localScanServer : null;
                localRecipeCacheFingerprint = localRecipeCacheUsesServerRecipes
                        ? localRecipeFingerprint(localScanServer) : 0;
            }
            nextPage = 1;
            loading = false;
            closeLocalScanArchives();
            rebuildWidgets();
        }
    }

    private String readQueuedRecipe(LocalRecipeSource source) {
        try {
            if (source.resourceId() != null) {
                IResource resource = fr.zazac1.customrecipe.client.compat.LegacyResources.getResource(minecraft.getResourceManager(), source.resourceId()).orElse(null);
                if (resource == null) return null;
                try (var input = resource.getInputStream()) { return new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8); }
            }
            var entry = source.archive().getJarEntry(source.archivePath());
            if (entry == null) return null;
            try (var input = source.archive().getInputStream(entry)) { return new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8); }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void closeLocalScanArchives() {
        for (JarFile archive : localScanArchives) {
            try { archive.close(); } catch (Exception ignored) {}
        }
        localScanArchives.clear();
    }

    private boolean hasCompleteCraftingRecipeSet(net.minecraft.server.MinecraftServer server) {
        long craftingRecipes = server.getRecipeManager().getRecipes().stream()
                .filter(ICraftingRecipe.class::isInstance)
                .filter(recipe -> !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID))
                .count();
        return craftingRecipes >= COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM;
    }

    private void addLoadedLocalRecipe(net.minecraft.item.crafting.IRecipe<?> entry,
                                      net.minecraft.server.MinecraftServer server, String loweredQuery,
                                      List<VanillaRecipePage.VanillaRecipeInfo> matches) {
        if (!(entry instanceof ICraftingRecipe wrappedRecipe)
                || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) return;

        ICraftingRecipe recipe = unwrap(wrappedRecipe);
        boolean special = recipe.isSpecial();
        ItemStack result = ItemStack.EMPTY;
        try {
            result = recipe.getResultItem();
        } catch (RuntimeException ignored) {
            // Some special recipes only resolve their output with a real grid.
        }
        String resultId = result.isEmpty() ? entry.getId().toString()
                : Registry.ITEM.getKey(result.getItem()).toString();
        int gridWidth = 0;
        int gridHeight = 0;
        boolean shapeless = !(recipe instanceof ShapedRecipe);
        List<String> ingredients = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            gridWidth = shaped.getWidth();
            gridHeight = shaped.getHeight();
            for (Ingredient ingredient : shaped.getIngredients()) ingredients.add(firstMatchingId(ingredient));
        } else {
            for (Ingredient ingredient : recipe.getIngredients()) ingredients.add(firstMatchingId(ingredient));
        }

        boolean outputMatch = matchOutput && itemMatchesQuery(resultId, loweredQuery);
        boolean ingredientMatch = matchIngredients && recipe.getIngredients().stream()
                .anyMatch(ingredient -> ingredientMatchesQuery(ingredient, loweredQuery));
        boolean disabled = parent.isDefaultRecipeDisabled(entry.getId().toString());
        boolean statusMatch = switch (statusFilter) {
            case ENABLED -> !disabled && !special;
            case DISABLED -> disabled && !special;
            case SPECIAL -> special;
            case ALL -> true;
        };
        boolean sourceMatch = switch (sourceFilter) {
            case MODDED -> !entry.getId().getNamespace().equals("minecraft");
            case VANILLA -> entry.getId().getNamespace().equals("minecraft");
            case ALL -> true;
        };
        if (statusMatch && sourceMatch && (loweredQuery.isEmpty()
                || entry.getId().toString().toLowerCase(Locale.ROOT).contains(loweredQuery) || outputMatch || ingredientMatch)) {
            matches.add(new VanillaRecipePage.VanillaRecipeInfo(entry.getId().toString(), resultId,
                    toPreviewSlots(ingredients, gridWidth, gridHeight, shapeless),
                    gridWidth, gridHeight, shapeless, special));
        }
    }

    /** Adds loaded recipe metadata without applying the active screen filters. */
    private void addLoadedLocalRecipeToCache(net.minecraft.item.crafting.IRecipe<?> entry,
                                             net.minecraft.server.MinecraftServer server,
                                             List<VanillaRecipePage.VanillaRecipeInfo> matches,
                                             Set<String> matchedIds) {
        if (!(entry instanceof ICraftingRecipe wrappedRecipe)
                || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)
                || !matchedIds.add(entry.getId().toString())) return;
        ICraftingRecipe recipe = unwrap(wrappedRecipe);
        boolean special = recipe.isSpecial();
        ItemStack result = ItemStack.EMPTY;
        try {
            result = recipe.getResultItem();
        } catch (RuntimeException ignored) {
            // Some special recipes only resolve their output with a real grid.
        }
        String resultId = result.isEmpty() ? entry.getId().toString()
                : Registry.ITEM.getKey(result.getItem()).toString();
        int gridWidth = 0;
        int gridHeight = 0;
        boolean shapeless = !(recipe instanceof ShapedRecipe);
        List<String> ingredients = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            gridWidth = shaped.getWidth();
            gridHeight = shaped.getHeight();
            for (Ingredient ingredient : shaped.getIngredients()) ingredients.add(firstMatchingId(ingredient));
        } else {
            for (Ingredient ingredient : recipe.getIngredients()) ingredients.add(firstMatchingId(ingredient));
        }
        matches.add(new VanillaRecipePage.VanillaRecipeInfo(entry.getId().toString(), resultId,
                toPreviewSlots(ingredients, gridWidth, gridHeight, shapeless),
                gridWidth, gridHeight, shapeless, special));
    }

    private VanillaRecipePage findLoadedLocalRecipes(net.minecraft.server.MinecraftServer server) {
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (net.minecraft.item.crafting.IRecipe<?> entry : server.getRecipeManager().getRecipes()) {
            if (!(entry instanceof ICraftingRecipe wrappedRecipe)
                    || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;

            ICraftingRecipe recipe = unwrap(wrappedRecipe);
            boolean special = recipe.isSpecial();
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.getResultItem();
            } catch (RuntimeException ignored) {
                // Some special recipes only resolve their output with a real grid.
            }
            String resultId = result.isEmpty() ? entry.getId().toString()
                    : Registry.ITEM.getKey(result.getItem()).toString();
            int gridWidth = 0;
            int gridHeight = 0;
            boolean shapeless = !(recipe instanceof ShapedRecipe);
            List<String> ingredients = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                gridWidth = shaped.getWidth();
                gridHeight = shaped.getHeight();
                for (Ingredient ingredient : shaped.getIngredients()) ingredients.add(firstMatchingId(ingredient));
            } else {
                for (Ingredient ingredient : recipe.getIngredients()) ingredients.add(firstMatchingId(ingredient));
            }

            boolean outputMatch = matchOutput && itemMatchesQuery(resultId, loweredQuery);
            boolean ingredientMatch = matchIngredients && recipe.getIngredients().stream()
                    .anyMatch(ingredient -> ingredientMatchesQuery(ingredient, loweredQuery));
            boolean disabled = parent.isDefaultRecipeDisabled(entry.getId().toString());
            boolean statusMatch = switch (statusFilter) {
                case ENABLED -> !disabled && !special;
                case DISABLED -> disabled && !special;
                case SPECIAL -> special;
                case ALL -> true;
            };
            boolean sourceMatch = switch (sourceFilter) {
                case MODDED -> !entry.getId().getNamespace().equals("minecraft");
                case VANILLA -> entry.getId().getNamespace().equals("minecraft");
                case ALL -> true;
            };
            if (statusMatch && sourceMatch && (loweredQuery.isEmpty()
                    || entry.getId().toString().toLowerCase(Locale.ROOT).contains(loweredQuery) || outputMatch || ingredientMatch)) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(entry.getId().toString(), resultId,
                        toPreviewSlots(ingredients, gridWidth, gridHeight, shapeless),
                        gridWidth, gridHeight, shapeless, special));
            }
        }

        matches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
        CustomRecipeMod.LOGGER.info("[Custom Recipe] Found {} active local crafting recipes.", matches.size());
        return new VanillaRecipePage(matches, 0, matches.size());
    }

    private ICraftingRecipe unwrap(ICraftingRecipe recipe) {
        while (true) {
            if (recipe instanceof VariantFilteredCraftingRecipe filtered) {
                recipe = filtered.delegate();
            } else if (recipe instanceof DisabledCraftingRecipe disabled) {
                recipe = disabled.delegate();
            } else {
                return recipe;
            }
        }
    }

    private String firstMatchingId(Ingredient ingredient) {
        for (ItemStack stack : ingredient.getItems()) {
            return Registry.ITEM.getKey(stack.getItem()).toString();
        }
        return "";
    }

    private void addLocalRecipe(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                String recipeId, String json, String loweredQuery) {
        if (!json.contains("crafting_")) return;
        if (collectedLocalRecipeJsonById != null) collectedLocalRecipeJsonById.putIfAbsent(recipeId, json);
        boolean vanillaRecipe = recipeId.startsWith("minecraft:");
        if ((sourceFilter == SourceFilter.VANILLA && !vanillaRecipe)
                || (sourceFilter == SourceFilter.MODDED && vanillaRecipe)) return;
        String resultId = findResultId(json, recipeId);
        RecipeLayout layout = findLocalRecipeLayout(json);
        boolean outputMatch = matchOutput && itemMatchesQuery(resultId, loweredQuery);
        boolean ingredientMatch = matchIngredients && json.toLowerCase(Locale.ROOT).contains(loweredQuery);
        if ((loweredQuery.isEmpty() || recipeId.toLowerCase(Locale.ROOT).contains(loweredQuery)
                || outputMatch || ingredientMatch) && matchedIds.add(recipeId)) {
            boolean special = isSpecialRecipe(json);
            matches.add(new VanillaRecipePage.VanillaRecipeInfo(recipeId, resultId, toPreviewSlots(layout),
                    layout.width(), layout.height(), layout.shapeless(), special));
        }
    }

    /** Adds archive/resource metadata without applying the active screen filters. */
    private void addLocalRecipeToCache(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                       String recipeId, String json) {
        if (!json.contains("crafting_") || !matchedIds.add(recipeId)) return;
        String resultId = findResultId(json, recipeId);
        RecipeLayout layout = findLocalRecipeLayout(json);
        matches.add(new VanillaRecipePage.VanillaRecipeInfo(recipeId, resultId, toPreviewSlots(layout),
                layout.width(), layout.height(), layout.shapeless(), isSpecialRecipe(json)));
    }

    private void loadBundledVanillaRecipes(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                           String loweredQuery) {
        try (JarFile jar = minecraftJar()) {
            if (jar == null) return;
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String path = entry.getName();
                if (!path.startsWith("data/minecraft/recipes/") || !path.endsWith(".json")) continue;
                try (var input = jar.getInputStream(entry)) {
                    String json = new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8);
                    String recipeId = "minecraft:" + path.substring("data/minecraft/recipes/".length(), path.length() - ".json".length());
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
        for (var modFile : ModList.get().getModFiles()) {
            Path archivePath;
            try {
                archivePath = modFile.getFile().getFilePath();
            } catch (RuntimeException ignored) {
                // A virtual or nested Forge module has no directly readable archive.
                continue;
            }
                Path normalized = archivePath.toAbsolutePath().normalize();
                if (!scannedArchives.add(normalized) || !Files.isRegularFile(normalized)
                        || !normalized.getFileName().toString().endsWith(".jar")) continue;
                try (JarFile jar = new JarFile(normalized.toFile())) {
                    Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        String path = entry.getName();
                        if (!path.startsWith("data/") || !path.endsWith(".json")) continue;
                        // 1.20.1 datapacks store recipes under data/<namespace>/recipes/<id>.json.
                        int namespaceEnd = path.indexOf('/', "data/".length());
                        int recipeStart = namespaceEnd + 1;
                        if (namespaceEnd <= "data/".length()
                                || !path.startsWith("recipes/", recipeStart)
                                || recipeStart + "recipes/".length() >= path.length() - ".json".length()) continue;
                        String recipeId = path.substring("data/".length(), namespaceEnd) + ":"
                                + path.substring(recipeStart + "recipes/".length(), path.length() - ".json".length());
                        // Do not scan this mod's bundled library templates as
                        // regular vanilla/mod recipes.
                        if (recipeId.startsWith("customrecipe:")) continue;
                        try (var input = jar.getInputStream(entry)) {
                            addLocalRecipe(matches, matchedIds, recipeId,
                                    new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8), loweredQuery);
                        }
                    }
                } catch (Exception ignored) {
                    // A non-archive mod origin simply has no local recipe files to browse.
                }
        }
    }

    private String findResultId(String json, String fallback) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonElement result = root.get("result");
            if (result == null) return fallback;
            if (result.isJsonPrimitive()) return result.getAsString();
            if (result.isJsonObject()) {
                JsonObject object = result.getAsJsonObject();
                if (object.has("item")) return object.get("item").getAsString();
                if (object.has("id")) return object.get("id").getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private boolean isSpecialRecipe(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            return root.has("type") && root.get("type").getAsString().contains("crafting_special");
        } catch (Exception ignored) {
            return false;
        }
    }

    private RecipeLayout findLocalRecipeLayout(String json) {
        List<String> ingredients = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
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
        return choices.isEmpty() ? "" : choices.get(0);
    }

    private void collectIngredientIds(JsonElement element, List<String> ingredients) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (!raw.trim().isEmpty()) ingredients.add(raw);
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

    private static final class RecipeLayout {
        private final List<String> ingredients;
        private final int width;
        private final int height;
        private final boolean shapeless;
        public RecipeLayout(List<String> ingredients, int width, int height, boolean shapeless) { this.ingredients=ingredients; this.width=width; this.height=height; this.shapeless=shapeless; }
        public List<String> ingredients() { return ingredients; }
        public int width() { return width; }
        public int height() { return height; }
        public boolean shapeless() { return shapeless; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; RecipeLayout other=(RecipeLayout)obj; return java.util.Objects.equals(ingredients, other.ingredients) && java.util.Objects.equals(width, other.width) && java.util.Objects.equals(height, other.height) && java.util.Objects.equals(shapeless, other.shapeless); }
        @Override public int hashCode() { return java.util.Objects.hash(ingredients, width, height, shapeless); }
}

    private List<String> toPreviewSlots(RecipeLayout layout) {
        return toPreviewSlots(layout.ingredients(), layout.width(), layout.height(), layout.shapeless());
    }

    private List<String> toPreviewSlots(List<String> ingredients, int gridWidth, int gridHeight, boolean shapeless) {
        List<String> slots = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        if (shapeless) {
            for (int i = 0; i < ingredients.size() && i < 9; i++) slots.set(i, ingredients.get(i));
            return slots;
        }
        for (int row = 0; row < gridHeight && row < 3; row++) {
            for (int column = 0; column < gridWidth && column < 3; column++) {
                int source = row * gridWidth + column;
                slots.set(row * 3 + column, source < ingredients.size() ? ingredients.get(source) : "");
            }
        }
        return slots;
    }

    private void toggle(String recipeId) {
        parent.toggleDefaultRecipe(recipeId);
        scroll = Math.min(scroll, maxScroll(filteredRecipes()));
        // The staged state changes only the currently rendered rows; do not
        // restart a complete paginated server query.
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
        selectedVariant = details.variants().isEmpty() ? null : details.variants().get(0);
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
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, fr.zazac1.customrecipe.Java8.list());

        // When a local world is running, the recipe manager already resolved
        // every mod tag. This is more reliable than reopening its JSON, which
        // can be hidden inside Forge's transformed mod archive.
        var localServer = minecraft.getSingleplayerServer();
        if (localServer != null) {
            var loaded = localServer.getRecipeManager().byKey(id).orElse(null);
            if (loaded instanceof ICraftingRecipe wrappedRecipe) {
                return detailsFromLoadedRecipe(recipeId, unwrap(wrappedRecipe));
            }
        }
        ResourceLocation resourceId = new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath() + ".json");
        Optional<String> json = readLocalRecipeJson(resourceId);
        if (!json.isPresent()) return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, fr.zazac1.customrecipe.Java8.list());

        try {
            JsonObject root = new JsonParser().parse(json.get()).getAsJsonObject();
            List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, fr.zazac1.customrecipe.Java8.list()));
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
            for (String material : variants.stream().limit(48).collect(java.util.stream.Collectors.toList())) {
                List<String> slots = new ArrayList<>(9);
                for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.get(0)));
                previews.add(new fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview(material, slots));
            }
            return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, previews);
        } catch (Exception ignored) {
            return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, fr.zazac1.customrecipe.Java8.list());
        }
    }

    /** Builds selectable previews from the resolved Ingredient stacks, including Forge tags. */
    private fr.zazac1.customrecipe.VanillaRecipeDetails detailsFromLoadedRecipe(String recipeId, ICraftingRecipe recipe) {
        List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, fr.zazac1.customrecipe.Java8.list()));
        if (recipe instanceof ShapedRecipe shaped) {
            List<Ingredient> ingredients = shaped.getIngredients();
            for (int row = 0; row < shaped.getHeight() && row < 3; row++) {
                for (int column = 0; column < shaped.getWidth() && column < 3; column++) {
                    int source = row * shaped.getWidth() + column;
                    choices.set(row * 3 + column, source < ingredients.size()
                            ? ingredientChoices(ingredients.get(source)) : fr.zazac1.customrecipe.Java8.list());
                }
            }
        } else {
            List<Ingredient> ingredients = recipe.getIngredients();
            for (int slot = 0; slot < ingredients.size() && slot < 9; slot++) {
                choices.set(slot, ingredientChoices(ingredients.get(slot)));
            }
        }
        return variantPreviews(recipeId, choices);
    }

    private List<String> ingredientChoices(Ingredient ingredient) {
        if (ingredient == null) return fr.zazac1.customrecipe.Java8.list();
        TreeSet<String> choices = new TreeSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) choices.add(Registry.ITEM.getKey(stack.getItem()).toString());
        }
        return new ArrayList<>(choices);
    }

    private fr.zazac1.customrecipe.VanillaRecipeDetails variantPreviews(String recipeId, List<List<String>> choices) {
        TreeSet<String> variants = new TreeSet<>();
        for (List<String> choice : choices) if (choice.size() > 1) variants.addAll(choice);
        List<fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview> previews = new ArrayList<>();
        for (String material : variants.stream().limit(48).collect(java.util.stream.Collectors.toList())) {
            List<String> slots = new ArrayList<>(9);
            for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.get(0)));
            previews.add(new fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview(material, slots));
        }
        return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, previews);
    }

    private Optional<String> readLocalRecipeJson(ResourceLocation resourceId) {
        try {
            var resource = fr.zazac1.customrecipe.client.compat.LegacyResources.getResource(minecraft.getResourceManager(), resourceId).orElse(null);
            if (resource != null) {
            try (var input = resource.getInputStream()) {
                    return Optional.of(new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8));
                }
            }
            try (JarFile jar = minecraftJar()) {
                if (jar != null) {
                    var entry = jar.getJarEntry("data/" + resourceId.getNamespace() + "/" + resourceId.getPath());
                    if (entry != null) {
                        try (var input = jar.getInputStream(entry)) {
                            return Optional.of(new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            // Mod recipes are available while browsing from the title screen,
            // but Forge may not expose their data through ResourceManager yet.
            String archiveEntry = "data/" + resourceId.getNamespace() + "/" + resourceId.getPath();
            for (var modFile : ModList.get().getModFiles()) {
                Path archivePath = modFile.getFile().getFilePath().toAbsolutePath().normalize();
                if (!Files.isRegularFile(archivePath) || !archivePath.toString().endsWith(".jar")) continue;
                try (JarFile archive = new JarFile(archivePath.toFile())) {
                    var entry = archive.getJarEntry(archiveEntry);
                    if (entry == null) continue;
                    try (var input = archive.getInputStream(entry)) {
                        return Optional.of(new String(fr.zazac1.customrecipe.Java8.readAllBytes(input), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private JarFile minecraftJar() throws Exception {
        var source = minecraft.getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            Path path = java.nio.file.Paths.get(source.getLocation().toURI());
            if (Files.isRegularFile(path) && vanillaRecipeFileCount(path) >= COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM) {
                return new JarFile(path.toFile());
            }
        }

        // ForgeGradle remaps classes into a small development archive. Its
        // original client.jar remains available in the local Gradle cache and
        // retains all 1.20.1 vanilla recipe JSON files. This branch is only a
        // development fallback; installed Forge clients use their game JAR.
        Path gradleClient = java.nio.file.Paths.get(System.getProperty("user.home"), ".gradle", "caches", "forge_gradle",
                "minecraft_repo", "versions", "1.20.1", "client.jar");
        if (Files.isRegularFile(gradleClient)) return new JarFile(gradleClient.toFile());
        return null;
    }

    private int vanillaRecipeFileCount(Path archivePath) {
        try (JarFile archive = new JarFile(archivePath.toFile())) {
            int count = 0;
            Enumeration<java.util.jar.JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName();
                if (path.startsWith("data/minecraft/recipes/") && path.endsWith(".json") && ++count >= COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM) {
                    return count;
                }
            }
            return count;
        } catch (Exception ignored) {
            return 0;
        }
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
                ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
                if (tagId != null) collectLocalTagItems(tagId, choices, new HashSet<>());
            } else if (!raw.trim().isEmpty()) {
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
            for (var entry : net.minecraft.tags.ItemTags.getAllTags().getTagOrEmpty(tagId).getValues()) {
                choices.add(Registry.ITEM.getKey(entry).toString());
            }
            if (!choices.isEmpty()) return;
        } catch (IllegalStateException ignored) {
            // At the title screen tags may not be bound yet; use their JSON below.
        }
        // In 1.20.1 datapacks use the plural directory: tags/items/.  The
        // singular form is used by newer versions and made every JSON tag
        // (including minecraft:planks for sticks) appear empty on Forge.
        ResourceLocation tagResource = new ResourceLocation(tagId.getNamespace(), "tags/items/" + tagId.getPath() + ".json");
        Optional<String> json = readLocalRecipeJson(tagResource);
        if (!json.isPresent()) return;
        try {
            JsonObject root = new JsonParser().parse(json.get()).getAsJsonObject();
            if (!root.has("values") || !root.get("values").isJsonArray()) return;
            for (JsonElement value : root.getAsJsonArray("values")) {
                String raw = value.isJsonPrimitive() ? value.getAsString()
                        : value.isJsonObject() && value.getAsJsonObject().has("id")
                        ? value.getAsJsonObject().get("id").getAsString() : "";
                if (raw.startsWith("#")) {
                    ResourceLocation nested = ResourceLocation.tryParse(raw.substring(1));
                    if (nested != null) collectLocalTagItems(nested, choices, visited);
                } else if (!raw.trim().isEmpty()) {
                    choices.add(raw);
                }
            }
        } catch (Exception ignored) {
            // An optional malformed tag must not prevent the recipe preview from opening.
        }
    }

    boolean isVariantDisabled(String recipeId, String materialId) {
        return parent.isDefaultRecipeVariantDisabled(recipeId, materialId);
    }

    void toggleVariant(String recipeId, String materialId) {
        parent.toggleDefaultRecipeVariant(recipeId, materialId);
    }

    boolean isRecipeDisabled(String recipeId) { return parent.isDefaultRecipeDisabled(recipeId); }

    void toggleAllVariants(String recipeId) { toggle(recipeId); }

    private int visibleRows() {
        return Math.max(1, (height - ROWS_Y - 32) / ROW);
    }

    private ITextComponent recipeLabel(VanillaRecipePage.VanillaRecipeInfo recipe) {
        return fr.zazac1.customrecipe.Texts.literal(recipe.result()).setStyle(net.minecraft.util.text.Style.EMPTY
                .withColor(net.minecraft.util.text.Color.fromRgb(recipe.special() ? 0x77BBFF : 0xFFFFFF)));
    }

    private String itemName(String id) {
        var item = Registry.ITEM.get(ResourceLocation.tryParse(id));
        return item == null || item == Items.AIR ? shortId(id) : new ItemStack(item).getHoverName().getString();
    }

    /** Searches both the stable registry ID and the name the player sees in their language. */
    private boolean itemMatchesQuery(String itemId, String loweredQuery) {
        return itemId.toLowerCase(Locale.ROOT).contains(loweredQuery)
                || itemName(itemId).toLowerCase(Locale.ROOT).contains(loweredQuery);
    }

    /** A tag can resolve to many modded items; searching only its first value loses recipes. */
    private boolean ingredientMatchesQuery(Ingredient ingredient, String loweredQuery) {
        for (ItemStack stack : ingredient.getItems()) {
            String itemId = Registry.ITEM.getKey(stack.getItem()).toString();
            if (itemMatchesQuery(itemId, loweredQuery)) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        renderPanels(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        if (localMode && loading) {
            int totalQueued = Math.max(1, localScanQueue.size());
            int progressWidth = Math.min(240, width - 40);
            int progressX = (width - progressWidth) / 2;
            int progressY = ROWS_Y + 18;
            int filled = Math.round(progressWidth * (localScanProcessed / (float) totalQueued));
            ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal("Loading... " + localScanProcessed + " / " + totalQueued),
                    width / 2, progressY - 14, 0xFFEEEEEE);
            ctx.fill(progressX - 1, progressY - 1, progressX + progressWidth + 1, progressY + 11, 0xFF111111);
            ctx.fill(progressX, progressY, progressX + progressWidth, progressY + 10, 0xFF444444);
            ctx.fill(progressX, progressY, progressX + filled, progressY + 10, 0xFF55AA55);
            ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal(recipes.size() + " recipes found"),
                    width / 2, progressY + 16, 0xFFBBBBBB);
            return;
        }
        if (loading && recipes.isEmpty()) {
            ctx.drawString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.loading"), 8, ROWS_Y + 2, 0xBBBBBB, false);
            return;
        }

        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        ctx.drawString(font, fr.zazac1.customrecipe.Texts.literal("Found " + total + " recipes"), 8, HEADER_Y, 0xFFFFEE88, false);
        if (shownRecipes.isEmpty()) {
            ctx.drawString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.none"), 8, ROWS_Y + 2, 0xFFBBBBBB, false);
        }
        for (int i = 0; i < visibleRows() && scroll + i < shownRecipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = shownRecipes.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            int rowColor = recipe.special() ? 0x22335566
                    : parent.isDefaultRecipeDisabled(recipe.id()) ? 0x44550000 : 0x22005500;
            ctx.fill(recipeListX(), y, recipeScrollbarX() - 4, y + ROW, rowColor);
            var item = Registry.ITEM.get(ResourceLocation.tryParse(recipe.result()));
            if (item != null && item != Items.AIR) ctx.renderItem(new ItemStack(item), recipeListX() + 2, y + 2);
        }
        renderPreview(ctx);
        if (hasRecipeScrollbar(shownRecipes)) renderRecipeScrollbar(ctx, shownRecipes);
        if (loading) ctx.drawString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.vanilla.loading_more"), 8, height - 42, 0xFFBBBBBB, false);
    }

    private void renderRecipeScrollbar(GuiGraphics ctx, List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes) {
        int trackX = recipeScrollbarX();
        int trackY = recipeScrollbarY();
        int trackH = recipeScrollbarH();
        int maxScroll = maxScroll(shownRecipes);
        int thumbY = trackY + (trackH - 15) * scroll / maxScroll;
        ctx.fill(trackX, trackY, trackX + SCROLLBAR_TRACK_W, trackY + trackH, SCROLLBAR_TRACK_COLOR);
        CustomRecipeSprites.draw(ctx, draggingRecipeScrollbar ? CustomRecipeSprites.SCROLLER_ACTIVE : CustomRecipeSprites.SCROLLER_IDLE,
                trackX, thumbY, SCROLLBAR_W, 15);
    }

    private void renderPanels(GuiGraphics ctx) {
        drawPanel(ctx, recipeBoxX(), recipeBoxY(), recipeBoxW(), recipeBoxH(), 0x99141A20, 0xFF40506A);
        drawPanel(ctx, previewBoxX(), previewBoxY(), previewBoxW(), previewBoxH(), 0x99141A20, 0xFF40506A);
    }

    private void renderPreview(GuiGraphics ctx) {
        int centerX = previewBoxX() + previewBoxW() / 2;
        if (selectedRecipe == null) {
            ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal("Select a recipe to preview"), centerX, previewBoxY() + 14, 0xFFAAAAAA);
            return;
        }
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal("Recipe Preview"), centerX, previewBoxY() + 10, 0xFFFFFFFF);
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal(selectedRecipe.id()), centerX, previewBoxY() + 26, 0xFFAAAAAA);
        int gridX = previewGridX();
        int gridY = previewGridY();
        List<String> slots = selectedVariant == null ? selectedRecipe.slots() : selectedVariant.slots();
        for (int slot = 0; slot < 9; slot++) {
            int x = gridX + (slot % 3) * 24;
            int y = gridY + (slot / 3) * 24;
            ctx.fill(x, y, x + 20, y + 20, 0xFF303030);
            if (slot < slots.size()) drawPreviewItem(ctx, slots.get(slot), x + 2, y + 2);
        }
        ctx.drawString(font, "->", gridX + 82, gridY + 27, 0xFFFFFFFF, true);
        ctx.fill(gridX + 108, gridY + 24, gridX + 132, gridY + 48, 0xFF303030);
        drawPreviewItem(ctx, selectedRecipe.result(), gridX + 112, gridY + 28);
        String layout = selectedRecipe.shapeless() ? "Shapeless: JSON ingredient order"
                : "Shaped: " + selectedRecipe.gridWidth() + "x" + selectedRecipe.gridHeight() + " pattern";
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal(layout), centerX, previewBoxY() + 42, 0xFFAAAAAA);
        if (selectedDetails == null) {
            ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal("Loading variants…"), centerX, previewVariantGridY() - 18, 0xFFAAAAAA);
            return;
        }
        if (selectedDetails.variants().isEmpty()) {
            ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal("No interchangeable material"), centerX, previewVariantGridY() - 18, 0xFFAAAAAA);
            return;
        }
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal("Material variants"), centerX, previewVariantGridY() - 18, 0xFFFFFFFF);
        int columns = previewVariantColumns();
        int variantsX = previewVariantGridX();
        int selectedIndex = -1;
        for (int index = 0; index < Math.min(48, selectedDetails.variants().size()); index++) {
            VanillaRecipeDetails.VariantPreview variant = selectedDetails.variants().get(index);
            int x = variantsX + (index % columns) * 24;
            int y = previewVariantGridY() + (index / columns) * 24;
            boolean disabled = isRecipeDisabled(selectedRecipe.id()) || isVariantDisabled(selectedRecipe.id(), variant.materialId());
            ctx.fill(x, y, x + 20, y + 20, disabled ? 0xAA4A0000 : 0xAA004A18);
            drawPreviewBorder(ctx, x, y, disabled ? 0xFFFF3333 : 0xFF22DD55);
            drawPreviewItem(ctx, variant.materialId(), x + 2, y + 2);
            if (selectedVariant != null && selectedVariant.materialId().equals(variant.materialId())) selectedIndex = index;
        }
        // Draw the selection last: neighbouring variant fills and borders must
        // never cover its right or bottom corners.
        if (selectedIndex >= 0) {
            int x = variantsX + (selectedIndex % columns) * 24;
            int y = previewVariantGridY() + (selectedIndex / columns) * 24;
            drawPreviewSelection(ctx, x, y);
        }
        // Draw the selection after every card so neighboring green/red borders
        // cannot cover one of its corners.
        if (selectedVariant != null) {
            for (int index = 0; index < Math.min(48, selectedDetails.variants().size()); index++) {
                VanillaRecipeDetails.VariantPreview variant = selectedDetails.variants().get(index);
                if (!selectedVariant.materialId().equals(variant.materialId())) continue;
                int x = variantsX + (index % columns) * 24;
                int y = previewVariantGridY() + (index / columns) * 24;
                drawPreviewSelection(ctx, x, y);
                break;
            }
        }
    }

    private void drawPreviewItem(GuiGraphics ctx, String id, int x, int y) {
        if (id == null || id.trim().isEmpty() || id.startsWith("#")) return;
        var item = Registry.ITEM.get(ResourceLocation.tryParse(id));
        if (item != null && item != Items.AIR) ctx.renderItem(new ItemStack(item), x, y);
    }

    private void drawPreviewBorder(GuiGraphics ctx, int x, int y, int color) {
        ctx.fill(x - 2, y - 2, x + 22, y, color); ctx.fill(x - 2, y + 20, x + 22, y + 22, color);
        ctx.fill(x - 2, y, x, y + 20, color); ctx.fill(x + 20, y, x + 22, y + 20, color);
    }

    private void drawPreviewSelection(GuiGraphics ctx, int x, int y) {
        int c = 0xFFFFFFFF;
        ctx.fill(x - 3, y - 3, x + 5, y - 1, c); ctx.fill(x - 3, y - 3, x - 1, y + 5, c);
        ctx.fill(x + 15, y - 3, x + 23, y - 1, c); ctx.fill(x + 21, y - 3, x + 23, y + 5, c);
        ctx.fill(x - 3, y + 21, x + 5, y + 23, c); ctx.fill(x - 3, y + 15, x - 1, y + 23, c);
        ctx.fill(x + 15, y + 21, x + 23, y + 23, c); ctx.fill(x + 21, y + 15, x + 23, y + 23, c);
    }

    private void drawPanel(GuiGraphics ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.hLine(x, x + w - 1, y, border); ctx.hLine(x, x + w - 1, y + h - 1, border);
        ctx.vLine(x, y, y + h - 1, border); ctx.vLine(x + w - 1, y, y + h - 1, border);
    }

    private String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        int maxScroll = maxScroll(shownRecipes);
        int oldScroll = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        if (scroll != oldScroll) rebuildWidgets();
        if (scroll + visibleRows() >= shownRecipes.size() - 3) loadMore();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= clearSearchX() && mouseX < clearSearchX() + 18
                && mouseY >= SEARCH_Y && mouseY < SEARCH_Y + 18) {
            query = "";
            searchField.setValue("");
            pendingSearchTicks = -1;
            resetSearch();
            return true;
        }
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverRecipeScrollbar(mouseX, mouseY, shownRecipes)) {
            draggingRecipeScrollbar = true;
            updateRecipeScrollbar(mouseY, shownRecipes);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingRecipeScrollbar) {
            updateRecipeScrollbar(mouseY, filteredRecipes());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) draggingRecipeScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
    @Override public void onClose() {
        closeLocalScanArchives();
        minecraft.setScreen(parent);
    }

    private static final class LocalRecipeSource {
        private final String recipeId;
        private final ResourceLocation resourceId;
        private final JarFile archive;
        private final String archivePath;
        private final net.minecraft.item.crafting.IRecipe<?> loadedRecipe;
        public LocalRecipeSource(String recipeId, ResourceLocation resourceId, JarFile archive,
                                     String archivePath, net.minecraft.item.crafting.IRecipe<?> loadedRecipe) { this.recipeId=recipeId; this.resourceId=resourceId; this.archive=archive; this.archivePath=archivePath; this.loadedRecipe=loadedRecipe; }
        public String recipeId() { return recipeId; }
        public ResourceLocation resourceId() { return resourceId; }
        public JarFile archive() { return archive; }
        public String archivePath() { return archivePath; }
        public net.minecraft.item.crafting.IRecipe<?> loadedRecipe() { return loadedRecipe; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; LocalRecipeSource other=(LocalRecipeSource)obj; return java.util.Objects.equals(recipeId, other.recipeId) && java.util.Objects.equals(resourceId, other.resourceId) && java.util.Objects.equals(archive, other.archive) && java.util.Objects.equals(archivePath, other.archivePath) && java.util.Objects.equals(loadedRecipe, other.loadedRecipe); }
        @Override public int hashCode() { return java.util.Objects.hash(recipeId, resourceId, archive, archivePath, loadedRecipe); }

        static LocalRecipeSource resource(String recipeId, ResourceLocation resourceId) {
            return new LocalRecipeSource(recipeId, resourceId, null, null, null);
        }
        static LocalRecipeSource archive(String recipeId, JarFile archive, String archivePath) {
            return new LocalRecipeSource(recipeId, null, archive, archivePath, null);
        }
        static LocalRecipeSource loaded(net.minecraft.item.crafting.IRecipe<?> recipe) {
            return new LocalRecipeSource(recipe.getId().toString(), null, null, null, recipe);
        }
    }
}
