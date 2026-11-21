package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Opens the operating system's native file chooser through Minecraft's bundled LWJGL runtime. */
final class WindowsFileDialogs {
    private static final String JSON_PATTERN = "*.json";
    private static final String ZIP_PATTERN = "*.zip";

    static void openJson(Consumer<Path> selected, Consumer<String> failed) {
        run(I18n.translate("customrecipe.file_dialog.open_title"), initialDirectory(), false,
                JSON_PATTERN, I18n.translate("customrecipe.file_dialog.filter"), selected, failed);
    }

    static void saveJson(String filename, Consumer<Path> selected, Consumer<String> failed) {
        run(I18n.translate("customrecipe.file_dialog.save_title"), Path.of(initialDirectory(), filename).toString(), true,
                JSON_PATTERN, I18n.translate("customrecipe.file_dialog.filter"), selected, failed);
    }

    static void saveZip(String filename, Consumer<Path> selected, Consumer<String> failed) {
        run(I18n.translate("customrecipe.file_dialog.save_title"), Path.of(initialDirectory(), filename).toString(), true,
                ZIP_PATTERN, I18n.translate("customrecipe.file_dialog.datapack_filter"), selected, failed);
    }

    private static void run(String title, String defaultPath, boolean save, String pattern,
                            String filterDescription, Consumer<Path> selected, Consumer<String> failed) {
        Thread dialogThread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.pointers(stack.UTF8(pattern));
                String result = save
                        ? TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, filters, filterDescription)
                        : TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, filters, filterDescription, false);
                if (result == null || result.isBlank()) return; // The player cancelled the dialog.

                Path path = Path.of(result);
                MinecraftClient.getInstance().execute(() -> {
                    restoreMinecraftFocus();
                    selected.accept(path);
                });
            } catch (Throwable error) {
                CustomRecipeMod.LOGGER.error("[Custom Recipe] Native file chooser failed.", error);
                MinecraftClient.getInstance().execute(() ->
                        failed.accept("Impossible d'ouvrir le sélecteur de fichiers : " + error.getMessage()));
            }
        }, "CustomRecipe-NativeFileDialog");
        dialogThread.setDaemon(true);
        dialogThread.start();
    }

    private static void restoreMinecraftFocus() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                GLFW.glfwShowWindow(client.getWindow().getHandle());
                GLFW.glfwFocusWindow(client.getWindow().getHandle());
            }
        } catch (Throwable ignored) {
        }
    }

    private static String initialDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return Files.isDirectory(documents) ? documents.toString() : home.toString();
    }

    private WindowsFileDialogs() {}
}
