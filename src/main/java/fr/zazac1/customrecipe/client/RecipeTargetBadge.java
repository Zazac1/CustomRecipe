package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import fr.zazac1.customrecipe.RecipeTarget;
import fr.zazac1.customrecipe.WorldRecipeAssignments;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    static void draw(GuiGraphicsExtractor context, Minecraft client, RecipeTarget target, String label) {
        // Outer frame begins at x=8, exactly aligned with the recipe-table border.
        int x = 9;
        int y = 3;
        context.fill(x - 1, y - 1, x + 25, y + 25, 0xFF505050);
        if (target.isWorld()) {
            WorldIcon icon = worldIcon(client, target);
            if (icon != null) {
                context.blit(RenderPipelines.GUI_TEXTURED, icon.id(), x, y, 0, 0, 24, 24,
                        icon.width(), icon.height());
            } else {
                context.item(ClientItemStacks.fromItem(Items.GRASS_BLOCK), x + 4, y + 4);
            }
        } else {
            Identifier globe = Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID, "textures/gui/world_globe.png");
            context.blit(RenderPipelines.GUI_TEXTURED, globe, x + 4, y + 4,
                    0, 0, 16, 16, 16, 16);
        }
        context.text(client.font, label, x + 31, y + 8, 0xFFFFFFFF, true);
    }

    private static WorldIcon worldIcon(Minecraft client, RecipeTarget target) {
        if (client == null || MISSING_WORLD_ICONS.contains(target.id())) return null;
        WorldIcon cached = WORLD_ICONS.get(target.id());
        if (cached != null) return cached;
        try {
            Path saves = client.getLevelSource().getBaseDir();
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
                Identifier id = Identifier.fromNamespaceAndPath(CustomRecipeMod.MOD_ID,
                        "dynamic/world_icons/" + target.id().replaceAll("[^a-z0-9_./-]", "_"));
                WorldIcon icon = new WorldIcon(id, image.getWidth(), image.getHeight());
                client.getTextureManager().register(id,
                        new DynamicTexture(() -> "RecipesCreator world icon", image));
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
