package fr.zazac1.customrecipe.client;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Uses the native Windows chooser in a separate process, never AWT inside the LWJGL client. */
final class WindowsFileDialogs {
    private static final String FILTER = "Custom Recipe config (*.json)|*.json|All files (*.*)|*.*";

    static void openJson(Consumer<Path> selected, Consumer<String> failed) {
        run("OpenFileDialog", "", selected, failed);
    }

    static void saveJson(String filename, Consumer<Path> selected, Consumer<String> failed) {
        run("SaveFileDialog", filename, selected, failed);
    }

    private static void run(String type, String filename, Consumer<Path> selected, Consumer<String> failed) {
        Thread dialogThread = new Thread(() -> {
            try {
                String command = "$ErrorActionPreference = 'Stop'; Add-Type -AssemblyName System.Windows.Forms; "
                        + "$d = New-Object System.Windows.Forms." + type + "; "
                        + "$d.Filter = '" + FILTER + "'; "
                        + "$d.InitialDirectory = [Environment]::GetFolderPath('MyDocuments'); "
                        + (filename.isBlank() ? "" : "$d.FileName = '" + filename.replace("'", "''") + "'; ")
                        + "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write($d.FileName) }";
                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", command)
                        .redirectErrorStream(true).start();
                String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exit = process.waitFor();
                MinecraftClient.getInstance().execute(() -> {
                    if (exit != 0) {
                        failed.accept("Windows file dialog failed.");
                    } else if (!result.isBlank()) {
                        selected.accept(Path.of(result));
                    }
                });
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                MinecraftClient.getInstance().execute(() -> failed.accept(e.getMessage()));
            }
        }, "CustomRecipe-WindowsFileDialog");
        dialogThread.setDaemon(true);
        dialogThread.start();
    }

    private WindowsFileDialogs() {}
}
