package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.RecipeTarget;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Persistent visual context for every Recipe Creator screen. */
final class RecipeTargetBadge {
    private static final Map<String, WorldIcon> WORLD_ICONS = new HashMap<>();
    private static final Set<String> MISSING_WORLD_ICONS = new HashSet<>();

    static void draw(DrawContext context, MinecraftClient client, RecipeTarget target, String label) {
        // Outer frame begins at x=8, exactly aligned with the recipe-table border.
        int x = 9;
        int y = 3;
        context.fill(x - 1, y - 1, x + 25, y + 25, 0xFF505050);
        if (target.isWorld()) {
            WorldIcon icon = worldIcon(client, target);
            if (icon != null) {
                context.drawTexture(icon.id(), x, y, 0, 0, 24, 24,
                        icon.width(), icon.height());
            } else {
                context.drawItem(new ItemStack(Items.GRASS_BLOCK), x + 4, y + 4);
            }
        } else {
            Identifier globe = Identifier.of(CustomRecipeMod.MOD_ID, "textures/gui/world_globe.png");
            context.drawTexture(globe, x + 4, y + 4,
                    0, 0, 16, 16, 16, 16);
        }
        context.drawText(client.textRenderer, label, x + 31, y + 8, 0xFFFFFFFF, true);
    }

    private static WorldIcon worldIcon(MinecraftClient client, RecipeTarget target) {
        if (client == null || MISSING_WORLD_ICONS.contains(target.id())) return null;
        WorldIcon cached = WORLD_ICONS.get(target.id());
        if (cached != null) return cached;
        try {
            Path saves = client.getLevelStorage().getSavesDirectory();
            if (saves == null) return null;
            Path worldDirectory;
            try (Stream<Path> directories = Files.list(saves)) {
                worldDirectory = directories.filter(Files::isDirectory)
                        .filter(path -> target.id().equals(WorldRecipeAssignments.worldId(path)))
                        .findFirst().orElse(null);
            }
            if (worldDirectory == null) return missing(target.id());
            Path iconPath = worldDirectory.resolve("icon.png");
            if (!Files.isRegularFile(iconPath)) return missing(target.id());
            try (InputStream input = Files.newInputStream(iconPath)) {
                NativeImage image = NativeImage.read(input);
                Identifier id = Identifier.of(CustomRecipeMod.MOD_ID,
                        "dynamic/world_icons/" + target.id().replaceAll("[^a-z0-9_./-]", "_"));
                WorldIcon icon = new WorldIcon(id, image.getWidth(), image.getHeight());
                client.getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(image));
                WORLD_ICONS.put(target.id(), icon);
                return icon;
            }
        } catch (IOException | RuntimeException ignored) {
            return missing(target.id());
        }
    }

    private static WorldIcon missing(String targetId) {
        MISSING_WORLD_ICONS.add(targetId);
        return null;
    }

    private record WorldIcon(Identifier id, int width, int height) {}

    private RecipeTargetBadge() {}
}
