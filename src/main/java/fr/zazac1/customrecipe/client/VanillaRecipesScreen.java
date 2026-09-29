package fr.zazac1.customrecipe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.DisabledCraftingRecipe;
import fr.zazac1.customrecipe.VariantFilteredCraftingRecipe;
import fr.zazac1.customrecipe.VanillaRecipePage;
import net.minecraftforge.fml.ModList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
public class VanillaRecipesScreen extends Screen {
    /** Vanilla 1.20.1 has 836 crafting recipes (shaped, shapeless and special). */
    private static final int COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM = 800;
    /** Small enough to keep the screen responsive while the bar visibly advances. */
    private static final int LOCAL_RECIPES_PER_TICK = 12;
    /** Reused for searches/reopening the screen; it is invalidated when the world recipe set changes. */
    private static List<VanillaRecipePage.VanillaRecipeInfo> localRecipeCache = List.of();
    private static Map<String, String> localRecipeJsonCache = Map.of();
    private static net.minecraft.server.MinecraftServer localRecipeCacheServer;
    private static int localRecipeCacheFingerprint;
    private static boolean startupPreloadStarted;
    private static final int ROW = 20;
    private static final int HEADER_Y = 34;
    private static final int SEARCH_Y = 52;
    private static final int ROWS_Y = 78;

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
    private final List<LocalRecipeSource> localScanQueue = new ArrayList<>();
    private final Set<JarFile> localScanArchives = new HashSet<>();
    private final Set<String> scheduledLocalRecipeIds = new HashSet<>();
    private List<VanillaRecipePage.VanillaRecipeInfo> localScanMatches = List.of();
    private Set<String> localScanMatchedIds = Set.of();
    private net.minecraft.server.MinecraftServer localScanServer;
    private String localScanQuery = "";
    private int localScanProcessed;
    private boolean localScanCanPopulateCache;
    private Map<String, String> localScanJsonById = Map.of();

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
            case ENABLED -> Component.translatable("customrecipe.vanilla.status_enabled").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x55FF55));
            case DISABLED -> Component.translatable("customrecipe.vanilla.status_disabled").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF5555));
            case SPECIAL -> Component.translatable("customrecipe.vanilla.status_special").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x77BBFF));
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

    /** Local ModMenu mode reads the default recipe data already loaded by the minecraft. */
    public VanillaRecipesScreen(ConfigScreen parent, boolean localMode) {
        super(Component.translatable("customrecipe.vanilla.title"));
        this.parent = parent;
        this.localMode = localMode;
    }

    /**
     * Builds the default local-recipe cache once after Minecraft is ready. This
     * intentionally runs on the client thread because resource packs and mod
     * registries are not thread-safe; users can disable it in settings.
     */
    static void preloadAtStartup(net.minecraft.client.Minecraft client) {
        if (startupPreloadStarted || !localRecipeCache.isEmpty()) return;
        startupPreloadStarted = true;
        try {
            VanillaRecipesScreen scanner = new VanillaRecipesScreen(new ConfigScreen(null), true);
            scanner.minecraft = client;
            VanillaRecipePage page = scanner.findLocalRecipes();
            localRecipeCache = List.copyOf(page.recipes());
            localRecipeCacheServer = client.getSingleplayerServer();
            localRecipeCacheFingerprint = scanner.localRecipeFingerprint(localRecipeCacheServer);
            CustomRecipeMod.LOGGER.info("[Custom Recipe] Preloaded {} recipes at startup.", localRecipeCache.size());
        } catch (RuntimeException exception) {
            CustomRecipeMod.LOGGER.warn("[Custom Recipe] Startup recipe preload was skipped: {}", exception.getMessage());
        }
    }

    ConfigScreen configScreen() { return parent; }

    @Override
    protected void init() {

        addRenderableOnly((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, parent.target(),
                parent.target().isWorld() ? parent.target().displayName() : "Global Library"));
        int searchButtonW = Math.max(72, font.width(Component.translatable("customrecipe.vanilla.search").getString()) + 16);
        int sourceFilterX = width - 100;
        int statusFilterX = sourceFilterX - 96;
        int outputFilterX = statusFilterX - 74;
        int ingredientFilterX = outputFilterX - 92;
        int searchButtonX = ingredientFilterX - 4 - searchButtonW;
        int clearSearchX = searchButtonX - 20;
        searchField = addRenderableWidget(new EditBox(font, 8, SEARCH_Y, clearSearchX - 8, 18,
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

        addRenderableWidget(Button.builder(Component.translatable("customrecipe.vanilla.search"), b -> resetSearch())
                .bounds(searchButtonX, SEARCH_Y, searchButtonW, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.vanilla.ingredient", Component.translatable(matchIngredients ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), b -> {
            matchIngredients = !matchIngredients;
            resetSearch();
        }).bounds(ingredientFilterX, SEARCH_Y, 88, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.vanilla.output", Component.translatable(matchOutput ? "customrecipe.recipe.on" : "customrecipe.recipe.off")), b -> {
            matchOutput = !matchOutput;
            resetSearch();
        }).bounds(outputFilterX, SEARCH_Y, 70, 18).build());
        addRenderableWidget(Button.builder(statusFilterLabel(), b -> cycleStatusFilter())
                .bounds(statusFilterX, SEARCH_Y, 92, 18).build());
        addRenderableWidget(Button.builder(sourceFilterLabel(), b -> cycleSourceFilter())
                .bounds(sourceFilterX, SEARCH_Y, 92, 18).build());

        int visibleRows = visibleRows();
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        for (int i = 0; i < visibleRows && scroll + i < shownRecipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = shownRecipes.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            boolean disabled = parent.disabledRecipes.contains(recipe.id());
            addRenderableWidget(Button.builder(recipeLabel(recipe), b -> minecraft.setScreen(new VanillaRecipeDetailsScreen(this, recipe)))
                    .bounds(30, y + 1, width - 122, 18).build());
            addRenderableWidget(Button.builder(disabled ? Component.translatable("customrecipe.state.disabled").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF5555))
                            : Component.translatable("customrecipe.state.enabled").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x55FF55)), b -> toggle(recipe.id()))
                    .bounds(width - 84, y + 1, 76, 18).build());
        }

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
        if (localMode && loading) processLocalSearch();
    }

    private void loadMore() {
        if (localMode || loading || recipes.size() >= total) return;
        loading = true;
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput,
                statusFilter.name(), sourceFilter.name(), parent.disabledRecipes, nextPage);
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
        Map<ResourceLocation, Resource> resources = minecraft.getResourceManager().listResources("recipes",
                id -> id.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> resource : resources.entrySet()) {
            try (var input = resource.getValue().open()) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
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
        localScanQuery = query.trim().toLowerCase(Locale.ROOT);
        localScanProcessed = 0;
        localScanServer = minecraft.getSingleplayerServer();
        int fingerprint = localRecipeFingerprint(localScanServer);
        if (localRecipeCacheServer == localScanServer && localRecipeCacheFingerprint == fingerprint
                && !localRecipeCache.isEmpty()) {
            recipes.addAll(filterCachedLocalRecipes(localRecipeCache));
            total = recipes.size();
            nextPage = 1;
            loading = false;
            rebuildWidgets();
            return;
        }
        localScanCanPopulateCache = localScanQuery.isEmpty() && matchIngredients && matchOutput
                && sourceFilter == SourceFilter.ALL && statusFilter == StatusFilter.ALL;
        localScanJsonById = localScanCanPopulateCache ? new HashMap<>() : Map.of();

        if (localScanServer != null && hasCompleteCraftingRecipeSet(localScanServer)) {
            for (net.minecraft.world.item.crafting.Recipe<?> recipe : localScanServer.getRecipeManager().getRecipes()) {
                if (recipe instanceof CraftingRecipe && !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) {
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
        int fingerprint = 1;
        for (net.minecraft.world.item.crafting.Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            fingerprint = 31 * fingerprint + recipe.getId().hashCode();
        }
        return fingerprint;
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
            boolean disabled = parent.disabledRecipes.contains(recipe.id());
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
        for (ResourceLocation resourceId : minecraft.getResourceManager().listResources("recipes",
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
                continue;
            }
            String json = readQueuedRecipe(source);
            if (json != null) {
                if (localScanCanPopulateCache) localScanJsonById.put(source.recipeId(), json);
                addLocalRecipe(localScanMatches, localScanMatchedIds, source.recipeId(), json, localScanQuery);
            }
        }
        recipes.clear();
        recipes.addAll(localScanMatches);
        total = recipes.size();
        if (localScanProcessed >= localScanQueue.size()) {
            recipes.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
            total = recipes.size();
            if (localScanCanPopulateCache) {
                localRecipeCache = List.copyOf(recipes);
                localRecipeJsonCache = Map.copyOf(localScanJsonById);
                localRecipeCacheServer = localScanServer;
                localRecipeCacheFingerprint = localRecipeFingerprint(localScanServer);
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
                Resource resource = minecraft.getResourceManager().getResource(source.resourceId()).orElse(null);
                if (resource == null) return null;
                try (var input = resource.open()) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            }
            var entry = source.archive().getJarEntry(source.archivePath());
            if (entry == null) return null;
            try (var input = source.archive().getInputStream(entry)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
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
                .filter(CraftingRecipe.class::isInstance)
                .filter(recipe -> !recipe.getId().getNamespace().equals(CustomRecipeMod.MOD_ID))
                .count();
        return craftingRecipes >= COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM;
    }

    private void addLoadedLocalRecipe(net.minecraft.world.item.crafting.Recipe<?> entry,
                                      net.minecraft.server.MinecraftServer server, String loweredQuery,
                                      List<VanillaRecipePage.VanillaRecipeInfo> matches) {
        if (!(entry instanceof CraftingRecipe wrappedRecipe)
                || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) return;

        CraftingRecipe recipe = unwrap(wrappedRecipe);
        boolean special = recipe.isSpecial();
        ItemStack result = ItemStack.EMPTY;
        try {
            result = recipe.getResultItem(server.registryAccess());
        } catch (RuntimeException ignored) {
            // Some special recipes only resolve their output with a real grid.
        }
        String resultId = result.isEmpty() ? entry.getId().toString()
                : BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
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
        boolean disabled = parent.disabledRecipes.contains(entry.getId().toString());
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

    private VanillaRecipePage findLoadedLocalRecipes(net.minecraft.server.MinecraftServer server) {
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (net.minecraft.world.item.crafting.Recipe<?> entry : server.getRecipeManager().getRecipes()) {
            if (!(entry instanceof CraftingRecipe wrappedRecipe)
                    || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;

            CraftingRecipe recipe = unwrap(wrappedRecipe);
            boolean special = recipe.isSpecial();
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException ignored) {
                // Some special recipes only resolve their output with a real grid.
            }
            String resultId = result.isEmpty() ? entry.getId().toString()
                    : BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
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
            boolean disabled = parent.disabledRecipes.contains(entry.getId().toString());
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

    private CraftingRecipe unwrap(CraftingRecipe recipe) {
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
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return "";
    }

    private void addLocalRecipe(List<VanillaRecipePage.VanillaRecipeInfo> matches, Set<String> matchedIds,
                                String recipeId, String json, String loweredQuery) {
        if (!json.contains("crafting_")) return;
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
                    String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
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
        return choices.isEmpty() ? "" : choices.get(0);
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
        if (!parent.disabledRecipes.remove(recipeId)) parent.disabledRecipes.add(recipeId);
        // The server receives the staged disabled IDs, so refresh its exact
        // filtered page and total instead of filtering a stale local page.
        resetSearch();
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
        if (id == null) return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, List.of());

        // When a local world is running, the recipe manager already resolved
        // every mod tag. This is more reliable than reopening its JSON, which
        // can be hidden inside Forge's transformed mod archive.
        var localServer = minecraft.getSingleplayerServer();
        if (localServer != null) {
            var loaded = localServer.getRecipeManager().byKey(id).orElse(null);
            if (loaded instanceof CraftingRecipe wrappedRecipe) {
                return detailsFromLoadedRecipe(recipeId, unwrap(wrappedRecipe));
            }
        }
        ResourceLocation resourceId = new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath() + ".json");
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
                for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.get(0)));
                previews.add(new fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview(material, slots));
            }
            return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, previews);
        } catch (Exception ignored) {
            return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, List.of());
        }
    }

    /** Builds selectable previews from the resolved Ingredient stacks, including Forge tags. */
    private fr.zazac1.customrecipe.VanillaRecipeDetails detailsFromLoadedRecipe(String recipeId, CraftingRecipe recipe) {
        List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, List.of()));
        if (recipe instanceof ShapedRecipe shaped) {
            List<Ingredient> ingredients = shaped.getIngredients();
            for (int row = 0; row < shaped.getHeight() && row < 3; row++) {
                for (int column = 0; column < shaped.getWidth() && column < 3; column++) {
                    int source = row * shaped.getWidth() + column;
                    choices.set(row * 3 + column, source < ingredients.size()
                            ? ingredientChoices(ingredients.get(source)) : List.of());
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
        if (ingredient == null) return List.of();
        TreeSet<String> choices = new TreeSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) choices.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return new ArrayList<>(choices);
    }

    private fr.zazac1.customrecipe.VanillaRecipeDetails variantPreviews(String recipeId, List<List<String>> choices) {
        TreeSet<String> variants = new TreeSet<>();
        for (List<String> choice : choices) if (choice.size() > 1) variants.addAll(choice);
        List<fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview> previews = new ArrayList<>();
        for (String material : variants.stream().limit(48).toList()) {
            List<String> slots = new ArrayList<>(9);
            for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.get(0)));
            previews.add(new fr.zazac1.customrecipe.VanillaRecipeDetails.VariantPreview(material, slots));
        }
        return new fr.zazac1.customrecipe.VanillaRecipeDetails(recipeId, previews);
    }

    private Optional<String> readLocalRecipeJson(ResourceLocation resourceId) {
        try {
            var resource = minecraft.getResourceManager().getResource(resourceId).orElse(null);
            if (resource != null) {
            try (var input = resource.open()) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            try (JarFile jar = minecraftJar()) {
                if (jar != null) {
                    var entry = jar.getJarEntry("data/" + resourceId.getNamespace() + "/" + resourceId.getPath());
                    if (entry != null) {
                        try (var input = jar.getInputStream(entry)) {
                            return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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
                        return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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
            Path path = Path.of(source.getLocation().toURI());
            if (Files.isRegularFile(path) && vanillaRecipeFileCount(path) >= COMPLETE_VANILLA_CRAFTING_RECIPE_MINIMUM) {
                return new JarFile(path.toFile());
            }
        }

        // ForgeGradle remaps classes into a small development archive. Its
        // original client.jar remains available in the local Gradle cache and
        // retains all 1.20.1 vanilla recipe JSON files. This branch is only a
        // development fallback; installed Forge clients use their game JAR.
        Path gradleClient = Path.of(System.getProperty("user.home"), ".gradle", "caches", "forge_gradle",
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
            for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
                choices.add(BuiltInRegistries.ITEM.getKey(entry.value()).toString());
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
        if (recipe.special()) {
            return Component.translatable("customrecipe.vanilla.special").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x77BBFF))
                    .append(Component.literal(shortId(recipe.id())).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xCCCCCC)));
        }
        return Component.literal(itemName(recipe.result()))
                .append(Component.literal("  " + shortId(recipe.id())).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAAAAA)));
    }

    private String itemName(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
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
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (itemMatchesQuery(itemId, loweredQuery)) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
        if (localMode && loading) {
            int totalQueued = Math.max(1, localScanQueue.size());
            int progressWidth = Math.min(240, width - 40);
            int progressX = (width - progressWidth) / 2;
            int progressY = ROWS_Y + 18;
            int filled = Math.round(progressWidth * (localScanProcessed / (float) totalQueued));
            ctx.drawCenteredString(font, Component.literal("Loading... " + localScanProcessed + " / " + totalQueued),
                    width / 2, progressY - 14, 0xFFEEEEEE);
            ctx.fill(progressX - 1, progressY - 1, progressX + progressWidth + 1, progressY + 11, 0xFF111111);
            ctx.fill(progressX, progressY, progressX + progressWidth, progressY + 10, 0xFF444444);
            ctx.fill(progressX, progressY, progressX + filled, progressY + 10, 0xFF55AA55);
            ctx.drawCenteredString(font, Component.literal(recipes.size() + " recipes found"),
                    width / 2, progressY + 16, 0xFFBBBBBB);
            return;
        }
        if (loading && recipes.isEmpty()) {
            ctx.drawString(font, Component.translatable("customrecipe.vanilla.loading"), 8, ROWS_Y + 2, 0xBBBBBB, false);
            return;
        }

        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        String countComponent = statusFilter == StatusFilter.ALL
                ? "Found " + total + " recipes"
                : "Showing " + shownRecipes.size() + " " + statusFilter.name().toLowerCase(Locale.ROOT) + " recipes";
        ctx.drawString(font, Component.translatable("customrecipe.vanilla.browse", countComponent), 8, HEADER_Y, 0xFFFFEE88, false);
        if (shownRecipes.isEmpty()) {
            ctx.drawString(font, Component.translatable("customrecipe.vanilla.none"), 8, ROWS_Y + 2, 0xFFBBBBBB, false);
        }
        for (int i = 0; i < visibleRows() && scroll + i < shownRecipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = shownRecipes.get(scroll + i);
            int y = ROWS_Y + i * ROW;
            int rowColor = recipe.special() ? 0x22335566
                    : parent.disabledRecipes.contains(recipe.id()) ? 0x44550000 : 0x22005500;
            ctx.fill(6, y, width - 88, y + ROW - 1, rowColor);
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(recipe.result()));
            if (item != null && item != Items.AIR) ctx.renderItem(new ItemStack(item), 10, y + 2);
        }
        if (loading) ctx.drawString(font, Component.translatable("customrecipe.vanilla.loading_more"), 8, height - 42, 0xFFBBBBBB, false);
    }

    private String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        List<VanillaRecipePage.VanillaRecipeInfo> shownRecipes = filteredRecipes();
        int maxScroll = Math.max(0, shownRecipes.size() - visibleRows());
        int oldScroll = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        if (scroll != oldScroll) rebuildWidgets();
        if (scroll + visibleRows() >= shownRecipes.size() - 3) loadMore();
        return true;
    }

    @Override public boolean isPauseScreen() { return true; }

    /** Recipe states belong to ConfigScreen and are only persisted from there. */
    @Override public void onClose() {
        closeLocalScanArchives();
        minecraft.setScreen(parent);
    }

    private record LocalRecipeSource(String recipeId, ResourceLocation resourceId, JarFile archive,
                                     String archivePath, net.minecraft.world.item.crafting.Recipe<?> loadedRecipe) {
        static LocalRecipeSource resource(String recipeId, ResourceLocation resourceId) {
            return new LocalRecipeSource(recipeId, resourceId, null, null, null);
        }
        static LocalRecipeSource archive(String recipeId, JarFile archive, String archivePath) {
            return new LocalRecipeSource(recipeId, null, archive, archivePath, null);
        }
        static LocalRecipeSource loaded(net.minecraft.world.item.crafting.Recipe<?> recipe) {
            return new LocalRecipeSource(recipe.getId().toString(), null, null, null, recipe);
        }
    }
}
