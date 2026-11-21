package fr.zazac1.customrecipe.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Uses the native Windows chooser in a separate process, never AWT inside the LWJGL client. */
final class WindowsFileDialogs {
    private static final String JSON_FILTER = "Custom Recipe share file (*.json)|*.json|All files (*.*)|*.*";
    private static final String ZIP_FILTER = "Minecraft datapack (*.zip)|*.zip|All files (*.*)|*.*";

    static void openJson(Consumer<Path> selected, Consumer<String> failed) {
        run("OpenFileDialog", "", JSON_FILTER, "json", selected, failed);
    }

    static void saveJson(String filename, Consumer<Path> selected, Consumer<String> failed) {
        run("SaveFileDialog", filename, JSON_FILTER, "json", selected, failed);
    }

    static void saveZip(String filename, Consumer<Path> selected, Consumer<String> failed) {
        run("SaveFileDialog", filename, ZIP_FILTER, "zip", selected, failed);
    }

    private static void run(String type, String filename, String filter, String extension,
                            Consumer<Path> selected, Consumer<String> failed) {
        Thread dialogThread = new Thread(() -> {
            try {
                String command = "$ErrorActionPreference = 'Stop'; Add-Type -AssemblyName System.Windows.Forms; "
                        + "$d = New-Object System.Windows.Forms." + type + "; "
                        + "$d.Filter = '" + filter.replace("'", "''") + "'; "
                        + "$d.DefaultExt = '" + extension + "'; $d.AddExtension = $true; "
                        + "$d.RestoreDirectory = $true; "
                        + "$d.InitialDirectory = [Environment]::GetFolderPath('MyDocuments'); "
                        + (filename.isBlank() ? "" : "$d.FileName = '" + filename.replace("'", "''") + "'; ")
                        + "$owner = New-Object System.Windows.Forms.Form; "
                        + "$owner.ShowInTaskbar = $false; $owner.TopMost = $true; "
                        + "$owner.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::None; "
                        + "$owner.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen; "
                        + "$owner.Size = New-Object System.Drawing.Size(1, 1); $owner.Opacity = 0.01; "
                        + "$owner.Show(); $owner.Activate(); "
                        + "$answer = $d.ShowDialog($owner); $owner.Close(); $owner.Dispose(); "
                        + "if ($answer -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write($d.FileName) }";
                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", command)
                        .redirectErrorStream(true).start();
                String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exit = process.waitFor();
                Minecraft.getInstance().execute(() -> {
                    restoreMinecraftFocus();
                    if (exit != 0) {
                        failed.accept("Windows file dialog failed.");
                    } else if (!result.isBlank()) {
                        selected.accept(Path.of(result));
                    }
                });
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                Minecraft.getInstance().execute(() -> failed.accept(e.getMessage()));
            }
        }, "CustomRecipe-NativeFileDialog");
        dialogThread.setDaemon(true);
        dialogThread.start();
    }

    private static void restoreMinecraftFocus() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getWindow() != null) {
                SDLVideo.SDL_ShowWindow(client.getWindow().handle());
                SDLVideo.SDL_RaiseWindow(client.getWindow().handle());
            }
        } catch (Throwable ignored) {
            // File selection already completed; focus restoration must never
            // turn a successful export/import into an error.
        }
    }

    private WindowsFileDialogs() {}
}
