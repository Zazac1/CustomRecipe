package fr.zazac1.customrecipe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Keeps custom-recipe availability scoped to the currently loaded world. */
public final class WorldRecipeAssignments {
    private static String activeWorldId = "";
    private static String activeWorldName = "";

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            activeWorldId = worldId(server.getSavePath(WorldSavePath.ROOT));
            // The save root may be reported as "." by an integrated server.
            // Its level name is the name the player actually sees in Minecraft.
            activeWorldName = server.getSaveProperties().getLevelName();
            if (activeWorldName == null || activeWorldName.isBlank()) {
                activeWorldName = "Current world";
            }
            migrateLegacyRecipes();
        });
        // The first data-pack recipe load happens before SERVER_STARTING, so it
        // cannot yet know the active save target. Reapply once the world target
        // is known; this is the same reload path used by the in-game Save button.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
            ConfigLoader.invalidate();
            server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), "reload");
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            activeWorldId = "";
            activeWorldName = "";
        });
    }

    public static String activeWorldId() {
        return activeWorldId;
    }

    public static String activeWorldName() {
        return activeWorldName;
    }

    /** Persists the first-day editor tip directly in the owning world's configuration. */
    public static boolean markEditorTipShown(String worldId, String worldName, String worldInstanceId) {
        if (worldId == null || worldId.isBlank()) return false;
        ModConfig config = ConfigLoader.get();
        WorldRecipeConfig worldConfig = config.getOrCreateWorldConfig(worldId, worldName);
        if (worldConfig.shown_editor_tip && worldInstanceId.equals(worldConfig.editor_tip_world_instance)) return false;
        worldConfig.shown_editor_tip = true;
        worldConfig.editor_tip_world_instance = worldInstanceId;
        ConfigLoader.saveAndInvalidate(config);
        return true;
    }

    /**
     * Schema 2 restores recipes made before target libraries existed into the
     * first world opened after upgrading. ConfigLoader writes a backup first.
     */
    public static void migrateLegacyRecipes() {
        migrateLegacyRecipes(ConfigLoader.get());
    }

    public static void migrateLegacyRecipes(ModConfig config) {
        if (config == null || config.recipe_target_version >= 2 || activeWorldId.isBlank()) return;

        List<CustomRecipeEntry> source = new ArrayList<>();
        if (config.custom_recipes != null) source.addAll(config.custom_recipes);
        if (source.isEmpty() && config.global_library != null && config.global_library.custom_recipes != null) {
            source.addAll(config.global_library.custom_recipes);
        }

        List<CustomRecipeEntry> activeLegacyRecipes = new ArrayList<>();
        for (CustomRecipeEntry recipe : source) {
            if (recipe == null || Boolean.FALSE.equals(recipe.enabled)) continue;
            if (recipe.world_ids != null && !recipe.world_ids.isEmpty() && !recipe.world_ids.contains(activeWorldId)) continue;
            activeLegacyRecipes.add(recipe);
        }

        // Older versions had one global configuration. Restore every setting
        // (disabled recipes, variants, known-by-default) before moving recipes.
        ConfigLoader.migrateLegacySettingsToWorld(config, activeWorldId, activeWorldName);
        ConfigLoader.LibraryImportResult result = ConfigLoader.importLegacyRecipesToWorld(
                config, activeLegacyRecipes, activeWorldId, activeWorldName);
        config.recipe_target_version = 2;
        ConfigLoader.saveAndInvalidate(config);
        CustomRecipeMod.LOGGER.info("[CustomRecipe] Legacy recovery for world '{}': {} recipe(s) added, {} already present.",
                activeWorldName, result.added(), result.alreadyPresent());
    }
    public static boolean isRecipeActive(CustomRecipeEntry recipe) {
        if (recipe == null) return false;
        // A lifecycle event can run after the initial data-pack read; preserve legacy recipes for that one read.
        if (recipe.world_ids == null) {
            return !Boolean.FALSE.equals(recipe.enabled) && !Boolean.FALSE.equals(recipe.server_enabled);
        }
        return !activeWorldId.isBlank() && recipe.world_ids.contains(activeWorldId);
    }

    public static void addToActiveWorld(CustomRecipeEntry recipe) {
        if (recipe == null || activeWorldId.isBlank()) return;
        if (recipe.world_ids == null) recipe.world_ids = new ArrayList<>();
        if (recipe.world_names == null) recipe.world_names = new java.util.LinkedHashMap<>();
        if (!recipe.world_ids.contains(activeWorldId)) recipe.world_ids.add(activeWorldId);
        recipe.world_names.put(activeWorldId, activeWorldName);
    }

    /** Stable ID shared by the solo world picker and the running server. */
    public static String worldId(Path worldPath) {
        String path = worldPath.toAbsolutePath().normalize().toString();
        return "world-" + UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
    }

    private WorldRecipeAssignments() {}
}
