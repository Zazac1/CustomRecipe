package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

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
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("customrecipe.json");

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
        WorldRecipeConfig world = config.findWorldConfig(WorldRecipeAssignments.activeWorldId());
        return world == null ? new WorldRecipeConfig() : world;
    }

    /** Forces a reload from disk (called before each recipe injection). */
    public static void invalidate() {
        cached = null;
    }

    public static void saveAndInvalidate(ModConfig config) {
        normalize(config);
        save(config);
        invalidate();
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
        return importMissingRecipes(importedLibrary.custom_recipes, target);
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
    private static void save(ModConfig config) {
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
        } catch (IOException e) {
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to write config safely: {}", e.getMessage());
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
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
        if (config.disabled_builtin == null) config.disabled_builtin = new java.util.ArrayList<>();
        if (config.known_by_default_builtin == null) config.known_by_default_builtin = new java.util.ArrayList<>();
        if (config.disabled_recipes == null) config.disabled_recipes = new java.util.ArrayList<>();
        if (config.disabled_recipe_variants == null) config.disabled_recipe_variants = new java.util.ArrayList<>();
        if (config.custom_recipes == null) config.custom_recipes = new java.util.ArrayList<>();

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

    /**
     * Entries in the Global Library are reusable templates.  They must not
     * retain a state that belongs to a world or to a staged server edit.
     */
    private static void normalizeLibraryRecipes(WorldRecipeConfig library) {
        if (library == null || library.custom_recipes == null) return;
        for (CustomRecipeEntry recipe : library.custom_recipes) {
            if (recipe == null) continue;
            recipe.enabled = null;
            recipe.server_enabled = null;
        }
    }

    private static void stampSaveMetadata(ModConfig config) {
        config.saved_with_mod_version = FabricLoader.getInstance().getModContainer(CustomRecipeMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        config.saved_with_minecraft_version = FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
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
        return importMissingRecipes(config.global_library.custom_recipes, config.getOrCreateWorldConfig(worldId, worldName));
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
        return importMissingRecipes(legacyRecipes, config.getOrCreateWorldConfig(worldId, worldName));
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

    private static LibraryImportResult importMissingRecipes(List<CustomRecipeEntry> source, WorldRecipeConfig target) {
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
                target.custom_recipes.add(copy);
                added++;
            }
        }
        return new LibraryImportResult(added, alreadyPresent);
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
        if (recipeConfig.hidden_quick_add_builtin == null) recipeConfig.hidden_quick_add_builtin = new ArrayList<>();
        if (recipeConfig.quick_add_recipes == null) recipeConfig.quick_add_recipes = new ArrayList<>();
        if (recipeConfig.custom_recipes == null) recipeConfig.custom_recipes = new ArrayList<>();

        // v1.3.0 stores Quick Add as independent snapshots rather than a flag
        // linking back to a mutable library/world recipe.
        for (CustomRecipeEntry recipe : recipeConfig.custom_recipes) {
            if (!Boolean.TRUE.equals(recipe.quick_add)) continue;
            CustomRecipeEntry snapshot = copyForTarget(recipe);
            snapshot.quick_add = null;
            recipeConfig.quick_add_recipes.add(snapshot);
            recipe.quick_add = null;
        }

        Set<String> variantKeys = new HashSet<>();
        recipeConfig.disabled_recipe_variants.removeIf(rule -> rule == null || rule.recipe_id == null || rule.recipe_id.isBlank()
                || rule.material_id == null || rule.material_id.isBlank()
                || !variantKeys.add(rule.recipe_id + "\u0000" + rule.material_id));

        Map<String, CustomRecipeEntry> legacyRecipes = new LinkedHashMap<>();
        List<CustomRecipeEntry> normalizedRecipes = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (CustomRecipeEntry recipe : recipeConfig.custom_recipes) {
            if (recipe == null) continue;
            if (recipe.required_mods == null) recipe.required_mods = new LinkedHashMap<>();
            if (recipe.missing_items == null) recipe.missing_items = new ArrayList<>();
            if (recipe.conflicting_recipes == null) recipe.conflicting_recipes = new ArrayList<>();
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
            normalizedRecipes.add(recipe);
        }
        recipeConfig.custom_recipes = normalizedRecipes;
    }

    private static String recipeFingerprint(CustomRecipeEntry recipe) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", recipe.type);
        data.put("ingredients", recipe.ingredients);
        data.put("pattern", recipe.pattern);
        data.put("keys", recipe.keys);
        data.put("result", recipe.result);
        data.put("count", recipe.count);
        return GSON.toJson(data);
    }

    private ConfigLoader() {}
}
