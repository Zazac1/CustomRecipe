package fr.zazac1.customrecipe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Keeps recipe availability scoped to the currently loaded save. */
public final class WorldRecipeAssignments {
    private static String activeWorldId = "";
    private static String activeWorldName = "";

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            activeWorldId = worldId(server.getSavePath(WorldSavePath.ROOT));
            activeWorldName = server.getSaveProperties().getLevelName();
            if (activeWorldName == null || activeWorldName.isBlank()) activeWorldName = "Current world";
            migrateLegacyRecipes();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
            ConfigLoader.invalidate();
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            activeWorldId = "";
            activeWorldName = "";
        });
    }

    public static String activeWorldId() { return activeWorldId; }
    public static String activeWorldName() { return activeWorldName; }

    public static void migrateLegacyRecipes() { migrateLegacyRecipes(ConfigLoader.get()); }

    public static void migrateLegacyRecipes(ModConfig config) {
        if (config == null || config.recipe_target_version >= 2 || activeWorldId.isBlank()) return;
        List<CustomRecipeEntry> source = new ArrayList<>();
        if (config.custom_recipes != null) source.addAll(config.custom_recipes);
        if (source.isEmpty() && config.global_library != null && config.global_library.custom_recipes != null) {
            source.addAll(config.global_library.custom_recipes);
        }
        List<CustomRecipeEntry> active = new ArrayList<>();
        for (CustomRecipeEntry recipe : source) {
            if (recipe == null || Boolean.FALSE.equals(recipe.enabled)) continue;
            if (recipe.world_ids != null && !recipe.world_ids.isEmpty() && !recipe.world_ids.contains(activeWorldId)) continue;
            active.add(recipe);
        }
        ConfigLoader.migrateLegacySettingsToWorld(config, activeWorldId, activeWorldName);
        ConfigLoader.importLegacyRecipesToWorld(config, active, activeWorldId, activeWorldName);
        config.recipe_target_version = 2;
        ConfigLoader.saveAndInvalidate(config);
    }

    public static String worldId(Path worldPath) {
        String path = worldPath.toAbsolutePath().normalize().toString();
        return "world-" + UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
    }

    private WorldRecipeAssignments() {}
}
