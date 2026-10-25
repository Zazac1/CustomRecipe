package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("customrecipe.json");

    private static ModConfig cached = null;

    /** Result of a non-destructive Global Library import. */
    public record LibraryImportResult(int added, int alreadyPresent) {}

    /** Self-identifying, intentionally narrow JSON format for Global Library sharing. */
    private static final class LibraryExport {
        int library_export_version = 1;
        String saved_with_mod_version = "";
        String saved_with_minecraft_version = "";
        WorldRecipeConfig global_library = new WorldRecipeConfig();
    }
    public static ModConfig get() {
        if (cached == null) cached = load();
        return cached;
    }

    /** Runtime settings for the loaded save. The library is intentionally not a fallback. */
    public static WorldRecipeConfig activeWorldConfig() {
        return activeWorldConfig(get());
    }

    public static WorldRecipeConfig activeWorldConfig(ModConfig config) {
        if (config == null) return new WorldRecipeConfig();
        String worldId = WorldRecipeAssignments.activeWorldId();
        WorldRecipeConfig world = config.findWorldConfig(worldId);
        if (world == null && worldId != null && !worldId.isBlank()) {
            world = config.getOrCreateWorldConfig(worldId, WorldRecipeAssignments.activeWorldName());
            synchronizeAutomaticLibraryRecipes(config, world);
        }
        return world == null ? new WorldRecipeConfig() : world;
    }

    /** Resolves global default-recipe rules with the active world's explicit overrides. */
    public static List<String> effectiveDisabledRecipes(ModConfig config, WorldRecipeConfig world) {
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        if (config != null && config.global_disabled_recipes != null) disabled.addAll(config.global_disabled_recipes);
        if (world != null) {
            if (world.enabled_recipes != null) disabled.removeAll(world.enabled_recipes);
            if (world.disabled_recipes != null) disabled.addAll(world.disabled_recipes);
        }
        disabled.removeIf(value -> value == null || value.isBlank());
        return new ArrayList<>(disabled);
    }

    /** Resolves global material-variant rules with the active world's explicit overrides. */
    public static List<RecipeVariantRule> effectiveDisabledRecipeVariants(ModConfig config, WorldRecipeConfig world) {
        LinkedHashMap<String, RecipeVariantRule> disabled = new LinkedHashMap<>();
        if (config != null && config.global_disabled_recipe_variants != null) {
            for (RecipeVariantRule rule : config.global_disabled_recipe_variants) putVariant(disabled, rule);
        }
        if (world != null) {
            if (world.enabled_recipe_variants != null) {
                for (RecipeVariantRule rule : world.enabled_recipe_variants) disabled.remove(variantKey(rule));
            }
            if (world.disabled_recipe_variants != null) {
                for (RecipeVariantRule rule : world.disabled_recipe_variants) putVariant(disabled, rule);
            }
        }
        return new ArrayList<>(disabled.values());
    }

    private static void putVariant(Map<String, RecipeVariantRule> target, RecipeVariantRule rule) {
        String key = variantKey(rule);
        if (key != null) target.put(key, rule);
    }

    private static String variantKey(RecipeVariantRule rule) {
        if (rule == null || rule.recipe_id == null || rule.recipe_id.isBlank()
                || rule.material_id == null || rule.material_id.isBlank()) return null;
        return rule.recipe_id + "\u0000" + rule.material_id;
    }

    /** Forces a reload from disk (called before each recipe injection). */
    public static void invalidate() {
        cached = null;
    }

    public static boolean saveAndInvalidate(ModConfig config) {
        normalize(config);
        boolean saved = save(config);
        if (saved) invalidate();
        return saved;
    }

    /** Persists an automatically detected integrity state without discarding the loaded config. */
    public static void saveIntegrityState(ModConfig config) {
        normalize(config);
        save(config);
    }

    /** Serializes a config for the OP-only server editor. */
    public static String toJson(ModConfig config) {
        normalize(config);
        stampSaveMetadata(config);
        return GSON.toJson(config);
    }

    /** Saves a portable full configuration chosen by the player. */
    public static void exportTo(ModConfig config, Path destination) throws IOException {
        Files.writeString(destination, toJson(config), StandardCharsets.UTF_8);
    }

    /** Saves only the reusable Global Library, without any world's recipes or settings. */
    public static void exportLibraryTo(ModConfig config, Path destination) throws IOException {
        if (config == null) throw new IOException("Global Library is unavailable.");
        normalize(config);
        stampSaveMetadata(config);
        LibraryExport exported = new LibraryExport();
        exported.saved_with_mod_version = config.saved_with_mod_version;
        exported.saved_with_minecraft_version = config.saved_with_minecraft_version;
        exported.global_library = config.global_library;
        Files.writeString(destination, GSON.toJson(exported), StandardCharsets.UTF_8);
    }
    /** Loads and validates a portable full configuration without writing it to the active config yet. */
    public static ModConfig importFrom(Path source) throws IOException {
        if (Files.size(source) > 5_000_000L) throw new IOException("Import file is too large.");
        String json = Files.readString(source, StandardCharsets.UTF_8);
        ModConfig raw = GSON.fromJson(json, ModConfig.class);
        if (raw != null && raw.recipe_target_version > 2) {
            throw new IOException("This backup uses a newer Custom Recipe format.");
        }
        ModConfig config = fromJson(json);
        if (config == null) throw new IOException("Invalid Custom Recipe configuration.");
        return config;
    }
    /** Reads one portable Global Library. It is not written to the live config here. */
    public static WorldRecipeConfig importLibraryFrom(Path source) throws IOException {
        if (Files.size(source) > 5_000_000L) throw new IOException("Import file is too large.");
        LibraryExport imported;
        try {
            imported = GSON.fromJson(Files.readString(source, StandardCharsets.UTF_8), LibraryExport.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid Global Library file.");
        }
        if (imported == null || imported.library_export_version > 1 || imported.global_library == null) {
            throw new IOException("Invalid or newer Global Library file.");
        }
        normalizeRecipeConfig(imported.global_library);
        return imported.global_library;
    }

    /** Merges a portable library without deleting or replacing the current library. */
    public static LibraryImportResult importLibraryInto(ModConfig config, WorldRecipeConfig importedLibrary) {
        if (config == null || importedLibrary == null) return new LibraryImportResult(0, 0);
        normalize(config);
        normalizeRecipeConfig(importedLibrary);
        // Vanilla state is copied exactly so enabled/disabled recipes, variants,
        // and known-by-default settings survive a library import.
        WorldRecipeConfig target = config.global_library;
        target.disabled_builtin = new ArrayList<>(importedLibrary.disabled_builtin);
        target.known_by_default_builtin = new ArrayList<>(importedLibrary.known_by_default_builtin);
        target.disabled_recipes = new ArrayList<>(importedLibrary.disabled_recipes);
        target.disabled_recipe_variants = new ArrayList<>(importedLibrary.disabled_recipe_variants);
        target.hidden_quick_add_builtin = new ArrayList<>(importedLibrary.hidden_quick_add_builtin);
        target.quick_add_recipes = new ArrayList<>(importedLibrary.quick_add_recipes);
        return importMissingRecipes(importedLibrary.custom_recipes, target, true);
    }
    /** Compares recipes without relying on their position in a config file. */
    public static boolean sameRecipe(CustomRecipeEntry first, CustomRecipeEntry second) {
        if (first == null || second == null) return false;
        if (first.id != null && !first.id.isBlank() && second.id != null && !second.id.isBlank()) {
            return first.id.equals(second.id);
        }
        return recipeFingerprint(first).equals(recipeFingerprint(second));
    }

    /** Returns null when a JSON payload is invalid. */
    public static ModConfig fromJson(String json) {
        try {
            ModConfig config = GSON.fromJson(json, ModConfig.class);
            if (config == null) return null;
            JsonObject raw = JsonParser.parseString(json).getAsJsonObject();
            if (!raw.has("automatic_gui_scale")) config.automatic_gui_scale = true;
            if (!raw.has("preload_recipes_on_startup")) config.preload_recipes_on_startup = true;
            normalize(config);
            return config;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaults = new ModConfig();
            normalize(defaults);
            save(defaults);
            CustomRecipeMod.LOGGER.info("[CustomRecipe] Config created at: {}", CONFIG_PATH);
            return defaults;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            try {
                ModConfig rawConfig = GSON.fromJson(json, ModConfig.class);
                if (rawConfig != null && rawConfig.recipe_target_version < 2) backupLegacyConfig(json);
            } catch (JsonSyntaxException ignored) {
                // fromJson below remains the single validation path for malformed files.
            }
            ModConfig config = fromJson(json);
            return config != null ? config : new ModConfig();
        } catch (IOException e) {
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to read config: {}", e.getMessage());
            return new ModConfig();
        }
    }

    /** Writes through a temporary file and preserves the last known-good config. */
    private static boolean save(ModConfig config) {
        stampSaveMetadata(config);
        Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        Path previous = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".previous");
        try {
            Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
            if (Files.exists(CONFIG_PATH)) Files.copy(CONFIG_PATH, previous, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to write config safely: {}", e.getMessage());
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            return false;
        }
    }
    /** Keeps the original pre-target config recoverable before its first migration. */
    private static void backupLegacyConfig(String json) {
        Path backup = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".legacy-backup");
        if (Files.exists(backup)) return;
        try {
            Files.writeString(backup, json);
            CustomRecipeMod.LOGGER.info("[CustomRecipe] Backed up legacy config to: {}", backup);
        } catch (IOException e) {
            CustomRecipeMod.LOGGER.warn("[CustomRecipe] Could not back up legacy config: {}", e.getMessage());
        }
    }

    private static void normalize(ModConfig config) {
        if (config.editor_world_id == null) config.editor_world_id = "";
        if (config.editor_world_name == null) config.editor_world_name = "";
        if (config.default_editor_target_id == null || config.default_editor_target_id.isBlank()) {
            config.default_editor_target_id = "global";
        }
        if (config.default_editor_target_name == null || config.default_editor_target_name.isBlank()) {
            config.default_editor_target_name = "Global Library";
        }
        if (config.disabled_builtin == null) config.disabled_builtin = new java.util.ArrayList<>();
        if (config.known_by_default_builtin == null) config.known_by_default_builtin = new java.util.ArrayList<>();
        if (config.disabled_recipes == null) config.disabled_recipes = new java.util.ArrayList<>();
        if (config.disabled_recipe_variants == null) config.disabled_recipe_variants = new java.util.ArrayList<>();
        if (config.global_disabled_recipes == null) config.global_disabled_recipes = new ArrayList<>();
        if (config.global_disabled_recipe_variants == null) config.global_disabled_recipe_variants = new ArrayList<>();
        if (config.custom_recipes == null) config.custom_recipes = new java.util.ArrayList<>();
        if (config.custom_items == null) config.custom_items = new ArrayList<>();
        normalizeCustomItems(config.custom_items);

        // Version 0 stored one shared list plus optional world_ids on recipes.
        // Split those recipes once: a library recipe remains in the library and
        // every assigned world receives its own independent copy.
        if (config.recipe_target_version < 1) migrateLegacyTargets(config);
        if (config.global_library == null) config.global_library = new WorldRecipeConfig();
        if (config.world_configs == null) config.world_configs = new LinkedHashMap<>();
        if (config.world_names == null) config.world_names = new LinkedHashMap<>();
        normalizeRecipeConfig(config.global_library);
        normalizeLibraryRecipes(config.global_library);
        config.world_configs.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null);
        for (WorldRecipeConfig worldConfig : config.world_configs.values()) normalizeRecipeConfig(worldConfig);
        synchronizeAutomaticLibraryRecipes(config);
        normalizeGlobalDefaultRecipeRules(config);

        // Retain the old fields while saved files transition. Runtime code and
        // the editor now read targets exclusively.
        WorldRecipeConfig legacyFields = new WorldRecipeConfig();
        legacyFields.custom_recipes = config.custom_recipes;
        legacyFields.disabled_builtin = config.disabled_builtin;
        legacyFields.known_by_default_builtin = config.known_by_default_builtin;
        legacyFields.disabled_recipes = config.disabled_recipes;
        legacyFields.disabled_recipe_variants = config.disabled_recipe_variants;
        normalizeRecipeConfig(legacyFields);
        config.custom_recipes = legacyFields.custom_recipes;
        config.disabled_builtin = legacyFields.disabled_builtin;
        config.known_by_default_builtin = legacyFields.known_by_default_builtin;
        config.disabled_recipes = legacyFields.disabled_recipes;
        config.disabled_recipe_variants = legacyFields.disabled_recipe_variants;
    }

    private static void normalizeCustomItems(List<CustomItemEntry> items) {
        Set<String> usedIds = new HashSet<>();
        items.removeIf(entry -> entry == null || entry.stack_data == null || entry.stack_data.isBlank());
        for (CustomItemEntry entry : items) {
            if (entry.name == null) entry.name = "";
            if (entry.item_id == null) entry.item_id = "minecraft:air";
            String requested = entry.id == null || entry.id.isBlank() ? entry.name : entry.id;
            String base = CustomItemLibrary.sanitizeId(requested);
            if (base.isBlank()) base = "custom_item";
            String candidate = base;
            int suffix = 2;
            while (!usedIds.add(candidate)) candidate = base + "_" + suffix++;
            entry.id = candidate;
            if (entry.name.isBlank()) entry.name = candidate;
        }
    }

    /**
     * Library recipes are templates only: they cannot be enabled, disabled, or
     * server-published. Older upgrade paths could retain either legacy flag
     * after the target split, making a Global Library entry appear disabled.
     */
    private static void normalizeLibraryRecipes(WorldRecipeConfig library) {
        if (library == null || library.custom_recipes == null) return;
        for (CustomRecipeEntry recipe : library.custom_recipes) {
            if (recipe == null) continue;
            recipe.enabled = null;
            recipe.server_enabled = null;
            recipe.global_library_source_id = null;
        }
    }

    private static void stampSaveMetadata(ModConfig config) {
        config.saved_with_mod_version = ModList.get().getModContainerById(CustomRecipeMod.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        config.saved_with_minecraft_version = ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }
    /** Deep copy used when a library recipe is added to a world. */
    public static CustomRecipeEntry copyRecipe(CustomRecipeEntry source) {
        return copyForTarget(source);
    }

    /** Counts recipes that can be added without altering the selected world. */
    public static LibraryImportResult previewGlobalLibraryImport(ModConfig config, List<CustomRecipeEntry> targetRecipes) {
        if (config == null || config.global_library == null) return new LibraryImportResult(0, 0);
        return countMissingRecipes(config.global_library.custom_recipes, targetRecipes);
    }

    /** Copies every missing Global Library recipe into one isolated world target. */
    public static LibraryImportResult importGlobalLibraryToWorld(ModConfig config, String worldId, String worldName) {
        if (config == null || config.global_library == null) return new LibraryImportResult(0, 0);
        return importMissingRecipes(config.global_library.custom_recipes,
                config.getOrCreateWorldConfig(worldId, worldName), false);
    }

    /** Ensures a newly opened world receives every Global Library recipe marked All Worlds. */
    public static void ensureAutomaticLibraryRecipesForWorld(String worldId, String worldName) {
        if (worldId == null || worldId.isBlank()) return;
        ModConfig config = get();
        boolean created = config.findWorldConfig(worldId) == null;
        WorldRecipeConfig world = config.getOrCreateWorldConfig(worldId, worldName);
        boolean changed = synchronizeAutomaticLibraryRecipes(config, world);
        if (created || changed) saveAndInvalidate(config);
    }

    /** Restores all pre-target global settings into the first world opened after upgrade. */
    static void migrateLegacySettingsToWorld(ModConfig config, String worldId, String worldName) {
        if (config == null) return;
        WorldRecipeConfig target = config.getOrCreateWorldConfig(worldId, worldName);
        mergeStrings(target.disabled_builtin, config.disabled_builtin);
        mergeStrings(target.known_by_default_builtin, config.known_by_default_builtin);
        mergeStrings(target.disabled_recipes, config.disabled_recipes);
        mergeVariants(target.disabled_recipe_variants, config.disabled_recipe_variants);
    }

    private static void mergeStrings(List<String> target, List<String> source) {
        if (target == null || source == null) return;
        for (String value : source) {
            if (value != null && !value.isBlank() && !target.contains(value)) target.add(value);
        }
    }

    private static void mergeVariants(List<RecipeVariantRule> target, List<RecipeVariantRule> source) {
        if (target == null || source == null) return;
        for (RecipeVariantRule candidate : source) {
            if (candidate == null || candidate.recipe_id == null || candidate.material_id == null) continue;
            boolean exists = target.stream().anyMatch(existing -> existing != null
                    && candidate.recipe_id.equals(existing.recipe_id)
                    && candidate.material_id.equals(existing.material_id));
            if (!exists) target.add(candidate);
        }
    }
    /** Used only once after upgrading an old shared-library configuration. */
    static LibraryImportResult importLegacyRecipesToWorld(ModConfig config, List<CustomRecipeEntry> legacyRecipes,
                                                           String worldId, String worldName) {
        if (config == null) return new LibraryImportResult(0, 0);
        return importMissingRecipes(legacyRecipes, config.getOrCreateWorldConfig(worldId, worldName), false);
    }

    private static LibraryImportResult countMissingRecipes(List<CustomRecipeEntry> source, List<CustomRecipeEntry> target) {
        if (source == null || source.isEmpty()) return new LibraryImportResult(0, 0);
        int added = 0;
        int alreadyPresent = 0;
        List<CustomRecipeEntry> known = target == null ? List.of() : target;
        for (CustomRecipeEntry recipe : source) {
            if (recipe == null) continue;
            if (known.stream().anyMatch(existing -> sameRecipe(recipe, existing))) alreadyPresent++; else added++;
        }
        return new LibraryImportResult(added, alreadyPresent);
    }

    private static LibraryImportResult importMissingRecipes(List<CustomRecipeEntry> source, WorldRecipeConfig target,
                                                             boolean preserveLibraryState) {
        if (source == null || source.isEmpty() || target == null) return new LibraryImportResult(0, 0);
        if (target.custom_recipes == null) target.custom_recipes = new ArrayList<>();
        int added = 0;
        int alreadyPresent = 0;
        for (CustomRecipeEntry recipe : source) {
            if (recipe == null) continue;
            if (target.custom_recipes.stream().anyMatch(existing -> sameRecipe(recipe, existing))) {
                alreadyPresent++;
                continue;
            }
            CustomRecipeEntry copy = copyForTarget(recipe);
            if (copy != null) {
                if (!preserveLibraryState) {
                    copy.global_auto_add = null;
                    copy.global_library_source_id = null;
                }
                target.custom_recipes.add(copy);
                added++;
            }
        }
        return new LibraryImportResult(added, alreadyPresent);
    }

    private static void synchronizeAutomaticLibraryRecipes(ModConfig config) {
        if (config == null || config.world_configs == null) return;
        for (WorldRecipeConfig world : config.world_configs.values()) {
            synchronizeAutomaticLibraryRecipes(config, world);
        }
    }

    private static boolean synchronizeAutomaticLibraryRecipes(ModConfig config, WorldRecipeConfig target) {
        if (config == null || config.global_library == null || target == null) return false;
        if (target.custom_recipes == null) target.custom_recipes = new ArrayList<>();

        Map<String, CustomRecipeEntry> automatic = new LinkedHashMap<>();
        if (config.global_library.custom_recipes != null) {
            for (CustomRecipeEntry recipe : config.global_library.custom_recipes) {
                if (recipe != null && recipe.id != null && !recipe.id.isBlank()
                        && Boolean.TRUE.equals(recipe.global_auto_add)) automatic.put(recipe.id, recipe);
            }
        }

        int previousSize = target.custom_recipes.size();
        target.custom_recipes.removeIf(recipe -> recipe != null
                && recipe.global_library_source_id != null
                && !recipe.global_library_source_id.isBlank()
                && !automatic.containsKey(recipe.global_library_source_id));
        boolean changed = target.custom_recipes.size() != previousSize;

        for (CustomRecipeEntry source : automatic.values()) {
            boolean present = target.custom_recipes.stream().anyMatch(existing -> existing != null
                    && (source.id.equals(existing.global_library_source_id) || sameRecipe(source, existing)));
            if (present) continue;
            CustomRecipeEntry copy = copyForTarget(source);
            if (copy == null) continue;
            copy.global_auto_add = null;
            copy.global_library_source_id = source.id;
            copy.enabled = null;
            copy.server_enabled = null;
            target.custom_recipes.add(copy);
            changed = true;
        }
        return changed;
    }
    private static void migrateLegacyTargets(ModConfig config) {
        WorldRecipeConfig library = new WorldRecipeConfig();
        library.disabled_builtin = new ArrayList<>(config.disabled_builtin);
        library.known_by_default_builtin = new ArrayList<>(config.known_by_default_builtin);
        library.disabled_recipes = new ArrayList<>(config.disabled_recipes);
        library.disabled_recipe_variants = new ArrayList<>(config.disabled_recipe_variants);
        Map<String, WorldRecipeConfig> worlds = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();

        // 1.20.1/1.21.8 persisted a client-side server publication cache.
        // It must not turn formerly usable local recipes into disabled recipes.
        for (CustomRecipeEntry recipe : config.custom_recipes) {
            if (recipe != null) recipe.server_enabled = null;
        }
        for (CustomRecipeEntry recipe : config.custom_recipes) {
            if (recipe == null || recipe.world_ids == null || recipe.world_ids.isEmpty()) {
                library.custom_recipes.add(copyForTarget(recipe));
                continue;
            }
            for (String worldId : recipe.world_ids) {
                if (worldId == null || worldId.isBlank()) continue;
                worlds.computeIfAbsent(worldId, ignored -> new WorldRecipeConfig())
                        .custom_recipes.add(copyForTarget(recipe));
                if (recipe.world_names != null && recipe.world_names.containsKey(worldId)) {
                    names.put(worldId, recipe.world_names.get(worldId));
                }
            }
        }
        config.global_library = library;
        config.world_configs = worlds;
        config.world_names = names;
        config.recipe_target_version = 1;
    }

    /** Gson copying preserves new metadata without risking shared recipe lists. */
    private static CustomRecipeEntry copyForTarget(CustomRecipeEntry source) {
        if (source == null) return null;
        CustomRecipeEntry copy = GSON.fromJson(GSON.toJson(source), CustomRecipeEntry.class);
        copy.world_ids = null;
        copy.world_names = null;
        return copy;
    }

    private static void normalizeRecipeConfig(WorldRecipeConfig recipeConfig) {
        if (recipeConfig.disabled_builtin == null) recipeConfig.disabled_builtin = new ArrayList<>();
        if (recipeConfig.known_by_default_builtin == null) recipeConfig.known_by_default_builtin = new ArrayList<>();
        if (recipeConfig.disabled_recipes == null) recipeConfig.disabled_recipes = new ArrayList<>();
        if (recipeConfig.disabled_recipe_variants == null) recipeConfig.disabled_recipe_variants = new ArrayList<>();
        if (recipeConfig.enabled_recipes == null) recipeConfig.enabled_recipes = new ArrayList<>();
        if (recipeConfig.enabled_recipe_variants == null) recipeConfig.enabled_recipe_variants = new ArrayList<>();
        if (recipeConfig.hidden_quick_add_builtin == null) recipeConfig.hidden_quick_add_builtin = new ArrayList<>();
        if (recipeConfig.quick_add_recipes == null) recipeConfig.quick_add_recipes = new ArrayList<>();
        if (recipeConfig.custom_recipes == null) recipeConfig.custom_recipes = new ArrayList<>();
        for (CustomRecipeEntry recipe : recipeConfig.quick_add_recipes) {
            normalizeIngredientCounts(recipe);
            recipe.global_auto_add = null;
            recipe.global_library_source_id = null;
        }

        // v1.3.0 stores Quick Add as independent snapshots rather than a flag
        // linking back to a mutable library/world recipe.
        for (CustomRecipeEntry recipe : recipeConfig.custom_recipes) {
            if (!Boolean.TRUE.equals(recipe.quick_add)) continue;
            CustomRecipeEntry snapshot = copyForTarget(recipe);
            snapshot.quick_add = null;
            recipeConfig.quick_add_recipes.add(snapshot);
            recipe.quick_add = null;
        }
        for (CustomRecipeEntry recipe : recipeConfig.quick_add_recipes) {
            normalizeIngredientCounts(recipe);
            recipe.global_auto_add = null;
            recipe.global_library_source_id = null;
        }

        Set<String> variantKeys = new HashSet<>();
        recipeConfig.disabled_recipe_variants.removeIf(rule -> rule == null || rule.recipe_id == null || rule.recipe_id.isBlank()
                || rule.material_id == null || rule.material_id.isBlank()
                || !variantKeys.add(rule.recipe_id + "\u0000" + rule.material_id));
        recipeConfig.disabled_recipes.removeIf(value -> value == null || value.isBlank());
        recipeConfig.disabled_recipes = new ArrayList<>(new LinkedHashSet<>(recipeConfig.disabled_recipes));
        recipeConfig.enabled_recipes.removeIf(value -> value == null || value.isBlank()
                || recipeConfig.disabled_recipes.contains(value));
        recipeConfig.enabled_recipes = new ArrayList<>(new LinkedHashSet<>(recipeConfig.enabled_recipes));

        Set<String> enabledVariantKeys = new HashSet<>();
        recipeConfig.enabled_recipe_variants.removeIf(rule -> {
            String key = variantKey(rule);
            return key == null || variantKeys.contains(key) || !enabledVariantKeys.add(key);
        });

        Map<String, CustomRecipeEntry> legacyRecipes = new LinkedHashMap<>();
        List<CustomRecipeEntry> normalizedRecipes = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Set<String> usedServerRecipeIds = new HashSet<>();
        for (CustomRecipeEntry recipe : recipeConfig.custom_recipes) {
            if (recipe == null) continue;
            normalizeIngredientCounts(recipe);
            if (recipe.required_mods == null) recipe.required_mods = new LinkedHashMap<>();
            if (recipe.missing_items == null) recipe.missing_items = new ArrayList<>();
            if (recipe.conflicting_recipes == null) recipe.conflicting_recipes = new ArrayList<>();
            if (recipe.global_library_source_id != null && recipe.global_library_source_id.isBlank()) {
                recipe.global_library_source_id = null;
            }
            if (recipe.world_ids != null) {
                recipe.world_ids.removeIf(worldId -> worldId == null || worldId.isBlank());
                recipe.world_ids = new ArrayList<>(new LinkedHashSet<>(recipe.world_ids));
                if (recipe.world_names == null) recipe.world_names = new LinkedHashMap<>();
                recipe.world_names.keySet().removeIf(worldId -> !recipe.world_ids.contains(worldId));
            } else {
                recipe.world_names = null;
            }
            // Conflict metadata written before the same-output rule existed is invalid:
            // it contained every recipe with matching inputs, including different outputs.
            if (recipe.same_shape_recipes == null) {
                recipe.same_shape_recipes = new ArrayList<>();
                recipe.conflicting_recipes.clear();
            }
            RecipeIntegrity.rememberRequiredMods(recipe);
            if (recipe.id == null || recipe.id.isBlank()) {
                String fingerprint = recipeFingerprint(recipe);
                CustomRecipeEntry existing = legacyRecipes.get(fingerprint);
                if (existing != null) {
                    // Old configs had no ID. A disabled server state wins over an enabled duplicate.
                    if (Boolean.FALSE.equals(recipe.enabled) && !Boolean.FALSE.equals(existing.enabled)) {
                        normalizedRecipes.set(normalizedRecipes.indexOf(existing), recipe);
                        legacyRecipes.put(fingerprint, recipe);
                    }
                    continue;
                }
                recipe.id = "legacy-" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
                legacyRecipes.put(fingerprint, recipe);
            }
            if (!usedIds.add(recipe.id)) {
                recipe.id = "duplicate-" + UUID.nameUUIDFromBytes(
                        (recipeFingerprint(recipe) + "#" + normalizedRecipes.size()).getBytes(StandardCharsets.UTF_8));
                usedIds.add(recipe.id);
            }
            // serverRecipeId lower-cases and replaces unsupported characters. Two
            // distinct editor IDs can therefore otherwise overwrite one another
            // inside Minecraft's recipe manager.
            int serverIdSuffix = 0;
            while (!usedServerRecipeIds.add(recipe.serverRecipeId().toString())) {
                recipe.id = "duplicate-" + UUID.nameUUIDFromBytes(
                        (recipeFingerprint(recipe) + "#server#" + normalizedRecipes.size() + "#" + serverIdSuffix++)
                                .getBytes(StandardCharsets.UTF_8));
                usedIds.add(recipe.id);
            }
            normalizedRecipes.add(recipe);
        }
        recipeConfig.custom_recipes = normalizedRecipes;
    }

    private static void normalizeGlobalDefaultRecipeRules(ModConfig config) {
        config.global_disabled_recipes.removeIf(value -> value == null || value.isBlank());
        config.global_disabled_recipes = new ArrayList<>(new LinkedHashSet<>(config.global_disabled_recipes));
        Set<String> variantKeys = new HashSet<>();
        config.global_disabled_recipe_variants.removeIf(rule -> {
            String key = variantKey(rule);
            return key == null || !variantKeys.add(key);
        });
    }

    /** Missing quantity metadata belongs to recipes made before 1.3.9 and therefore means one item. */
    private static void normalizeIngredientCounts(CustomRecipeEntry recipe) {
        if (recipe == null) return;
        if (recipe.ingredients == null) recipe.ingredients = new ArrayList<>();
        if (recipe.pattern == null) recipe.pattern = new ArrayList<>();
        if (recipe.keys == null) recipe.keys = new LinkedHashMap<>();
        if (recipe.ingredient_counts == null) recipe.ingredient_counts = new ArrayList<>();
        while (recipe.ingredient_counts.size() < recipe.ingredients.size()) recipe.ingredient_counts.add(1);
        while (recipe.ingredient_counts.size() > recipe.ingredients.size()) {
            recipe.ingredient_counts.remove(recipe.ingredient_counts.size() - 1);
        }
        for (int i = 0; i < recipe.ingredient_counts.size(); i++) {
            Integer value = recipe.ingredient_counts.get(i);
            recipe.ingredient_counts.set(i, CustomRecipeEntry.clampIngredientCount(value == null ? 1 : value));
        }
        if (recipe.key_counts == null) recipe.key_counts = new LinkedHashMap<>();
        recipe.key_counts.keySet().removeIf(key -> !recipe.keys.containsKey(key));
        for (String key : recipe.keys.keySet()) {
            Integer value = recipe.key_counts.get(key);
            recipe.key_counts.put(key, CustomRecipeEntry.clampIngredientCount(value == null ? 1 : value));
        }
        recipe.count = CustomRecipeEntry.clampIngredientCount(recipe.count);
    }

    private static String recipeFingerprint(CustomRecipeEntry recipe) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", recipe.type);
        data.put("ingredients", recipe.ingredients);
        data.put("ingredient_counts", recipe.ingredient_counts);
        data.put("pattern", recipe.pattern);
        data.put("keys", recipe.keys);
        data.put("key_counts", recipe.key_counts);
        data.put("result", recipe.result);
        data.put("count", recipe.count);
        return GSON.toJson(data);
    }

    private ConfigLoader() {}
}
