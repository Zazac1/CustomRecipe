package fr.zazac1.customrecipe;

/** A target backed by one stable Minecraft save-folder ID. */
public final class WorldRecipeTarget implements RecipeTarget {
    private final String worldId;
    private final String worldName;

    public WorldRecipeTarget(String worldId, String worldName) {
        this.worldId = worldId == null ? "" : worldId;
        this.worldName = worldName == null || worldName.isBlank() ? "World" : worldName;
    }

    @Override public String id() { return worldId; }
    @Override public String displayName() { return worldName; }
    @Override public WorldRecipeConfig resolve(ModConfig config) {
        return config.getOrCreateWorldConfig(worldId, worldName);
    }
    @Override public boolean isWorld() { return true; }
}
