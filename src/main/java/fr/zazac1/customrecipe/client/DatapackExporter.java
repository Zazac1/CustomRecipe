package fr.zazac1.customrecipe.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.CustomRecipeEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Converts vanilla-compatible Custom Recipe entries into a portable datapack ZIP. */
final class DatapackExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, VersionSpec> VERSIONS = versions();

    static final class Preview {
        private final int exportable;
        private final int skipped;
        private final List<String> reasons;

        Preview(int exportable, int skipped, List<String> reasons) {
            this.exportable = exportable;
            this.skipped = skipped;
            this.reasons = reasons;
        }

        int exportable() { return exportable; }
        int skipped() { return skipped; }
        List<String> reasons() { return reasons; }
    }

    private static final class VersionSpec {
        private final String version;
        private final int packMajor;
        private final int packMinor;
        private final boolean modernIngredients;
        private final boolean modernResult;
        private final boolean singularRecipeFolder;
        private final boolean modernPackMetadata;

        VersionSpec(String version, int packMajor, int packMinor, boolean modernIngredients,
                    boolean modernResult, boolean singularRecipeFolder, boolean modernPackMetadata) {
            this.version = version;
            this.packMajor = packMajor;
            this.packMinor = packMinor;
            this.modernIngredients = modernIngredients;
            this.modernResult = modernResult;
            this.singularRecipeFolder = singularRecipeFolder;
            this.modernPackMetadata = modernPackMetadata;
        }

        String version() { return version; }
        int packMajor() { return packMajor; }
        int packMinor() { return packMinor; }
        boolean modernIngredients() { return modernIngredients; }
        boolean modernResult() { return modernResult; }
        boolean singularRecipeFolder() { return singularRecipeFolder; }
        boolean modernPackMetadata() { return modernPackMetadata; }
    }

    static String supportedVersions() {
        return String.join(", ", VERSIONS.keySet());
    }

    static boolean supportsVersion(String version) {
        return spec(version) != null;
    }

    static Preview preview(List<CustomRecipeEntry> recipes, String version) {
        if (!supportsVersion(version)) return new Preview(0, recipes == null ? 0 : recipes.size(),
                Collections.singletonList("Unsupported Minecraft version"));
        int exportable = 0;
        int skipped = 0;
        Set<String> reasons = new LinkedHashSet<>();
        if (recipes != null) {
            for (CustomRecipeEntry recipe : recipes) {
                String reason = skipReason(recipe);
                if (reason == null) exportable++;
                else {
                    skipped++;
                    reasons.add(reason);
                }
            }
        }
        return new Preview(exportable, skipped, Collections.unmodifiableList(new ArrayList<>(reasons)));
    }

    static void export(Path destination, String packName, String description, String version,
                       List<CustomRecipeEntry> recipes, String targetName) throws IOException {
        VersionSpec spec = spec(version);
        if (spec == null) throw new IOException("Unsupported Minecraft version: " + version);
        Preview preview = preview(recipes, version);
        if (preview.exportable() == 0) throw new IOException("No vanilla-compatible custom recipe to export.");

        String namespace = safeNamespace(packName);
        Set<String> usedNames = new LinkedHashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination), StandardCharsets.UTF_8)) {
            put(zip, "pack.mcmeta", GSON.toJson(packMetadata(spec, description)) + "\n");
            put(zip, "CREDITS.txt", credits(packName, spec.version(), targetName, preview));
            put(zip, "EXPORT_REPORT.txt", exportReport(recipes, preview));
            put(zip, "LICENSE.txt", LICENSE_TEXT);

            for (CustomRecipeEntry recipe : recipes) {
                if (skipReason(recipe) != null) continue;
                String fileName = uniqueRecipeName(recipe, usedNames);
                String recipeFolder = spec.singularRecipeFolder() ? "recipe" : "recipes";
                String path = "data/" + namespace + "/" + recipeFolder + "/" + fileName + ".json";
                put(zip, path, GSON.toJson(recipeJson(recipe, spec)) + "\n");
            }
        }
    }

    static String safeFileStem(String value) {
        String safe = value == null ? "custom-recipe-datapack" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "-");
        return safe.trim().isEmpty() ? "custom-recipe-datapack" : safe;
    }

    private static VersionSpec spec(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("minecraft", "").trim();
        if (normalized.equals("26.1.x") || normalized.equals("26.1.2")) normalized = "26.1";
        return VERSIONS.get(normalized);
    }

    private static Map<String, VersionSpec> versions() {
        Map<String, VersionSpec> versions = new LinkedHashMap<>();
        add(versions, "1.16.5", 6, false, false, false, false);
        add(versions, "1.18.2", 9, false, false, false, false);
        add(versions, "1.19.2", 10, false, false, false, false);
        add(versions, "1.20.1", 15, false, false, false, false);
        add(versions, "1.21.1", 48, false, true, true, false);
        add(versions, "1.21.8", 81, true, true, true, false);
        add(versions, "1.21.10", 88, true, true, true, false);
        add(versions, "1.21.11", 94, true, true, true, false);
        add(versions, "26.1", 101, true, true, true, true);
        add(versions, "26.2", 107, true, true, true, true);
        add(versions, "26.3", 121, true, true, true, true);
        return Collections.unmodifiableMap(versions);
    }

    private static void add(Map<String, VersionSpec> versions, String version, int packMajor,
                            boolean modernIngredients, boolean modernResult,
                            boolean singularRecipeFolder,
                            boolean modernPackMetadata) {
        versions.put(version, new VersionSpec(version, packMajor, 0, modernIngredients, modernResult,
                singularRecipeFolder, modernPackMetadata));
    }

    private static String skipReason(CustomRecipeEntry recipe) {
        if (recipe == null) return "Empty recipe entry";
        if (Boolean.FALSE.equals(recipe.enabled)) return "Disabled recipes";
        if (Boolean.TRUE.equals(recipe.corrupted)) return "Recipes with missing items";
        if (recipe.hasCountedIngredients()) return "Ingredient quantities greater than one";
        if (recipe.hasDistinctIngredients()) return "Distinct-alternative constraints";
        if (isCustomReference(recipe.result)) return "Custom Item references";
        if (!validItemId(recipe.result)) return "Invalid result item IDs";

        if ("shaped".equalsIgnoreCase(recipe.type)) {
            if (recipe.pattern == null || recipe.pattern.isEmpty() || recipe.keys == null || recipe.keys.isEmpty()) {
                return "Incomplete shaped recipes";
            }
            for (String key : recipe.keys.keySet()) {
                String invalid = invalidOptionReason(recipe.keyOptions(key));
                if (invalid != null) return invalid;
            }
        } else {
            if (recipe.ingredients == null || recipe.ingredients.isEmpty()) return "Empty shapeless recipes";
            for (int index = 0; index < recipe.ingredients.size(); index++) {
                String invalid = invalidOptionReason(recipe.ingredientOptions(index));
                if (invalid != null) return invalid;
            }
        }
        return null;
    }

    private static String invalidOptionReason(List<String> options) {
        if (options == null || options.isEmpty()) return "Ingredients without an item";
        for (String option : options) {
            if (isCustomReference(option)) return "Custom Item references";
            if (!validItemId(option)) return "Invalid ingredient item IDs";
        }
        return null;
    }

    private static boolean isCustomReference(String value) {
        return value != null && CustomItemLibrary.isReference(value);
    }

    private static boolean validItemId(String value) {
        return value != null && !value.trim().isEmpty()
                && value.matches("(?:(?:[a-z0-9_.-]+):)?[a-z0-9_./-]+");
    }

    private static JsonObject packMetadata(VersionSpec spec, String description) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description == null || description.trim().isEmpty()
                ? "Recipes exported from Custom Recipe" : description.trim());
        if (spec.modernPackMetadata()) {
            pack.add("min_format", packFormat(spec));
            pack.add("max_format", packFormat(spec));
        } else {
            pack.addProperty("pack_format", spec.packMajor());
        }
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static JsonArray packFormat(VersionSpec spec) {
        JsonArray format = new JsonArray();
        format.add(spec.packMajor());
        format.add(spec.packMinor());
        return format;
    }

    private static JsonObject recipeJson(CustomRecipeEntry recipe, VersionSpec spec) {
        JsonObject json = new JsonObject();
        boolean shaped = "shaped".equalsIgnoreCase(recipe.type);
        json.addProperty("type", shaped ? "minecraft:crafting_shaped" : "minecraft:crafting_shapeless");
        if (shaped) {
            JsonArray pattern = new JsonArray();
            recipe.pattern.forEach(pattern::add);
            json.add("pattern", pattern);
            JsonObject key = new JsonObject();
            recipe.keys.forEach((symbol, ignored) -> key.add(symbol,
                    ingredientJson(recipe.keyOptions(symbol), spec.modernIngredients())));
            json.add("key", key);
        } else {
            JsonArray ingredients = new JsonArray();
            for (int index = 0; index < recipe.ingredients.size(); index++) {
                ingredients.add(ingredientJson(recipe.ingredientOptions(index), spec.modernIngredients()));
            }
            json.add("ingredients", ingredients);
        }
        JsonObject result = new JsonObject();
        result.addProperty(spec.modernResult() ? "id" : "item", recipe.result);
        if (recipe.count > 1) result.addProperty("count", recipe.count);
        json.add("result", result);
        return json;
    }

    private static com.google.gson.JsonElement ingredientJson(List<String> options, boolean modern) {
        if (options.size() == 1) return ingredientOption(options.get(0), modern);
        JsonArray alternatives = new JsonArray();
        options.forEach(option -> alternatives.add(ingredientOption(option, modern)));
        return alternatives;
    }

    private static com.google.gson.JsonElement ingredientOption(String itemId, boolean modern) {
        if (modern) return new JsonPrimitive(itemId);
        JsonObject item = new JsonObject();
        item.addProperty("item", itemId);
        return item;
    }

    private static String uniqueRecipeName(CustomRecipeEntry recipe, Set<String> used) {
        String source = recipe.id == null || recipe.id.trim().isEmpty() ? recipe.result : recipe.id;
        int colon = source == null ? -1 : source.indexOf(':');
        if (colon >= 0) source = source.substring(colon + 1);
        String base = source == null ? "recipe" : source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
        if (base.trim().isEmpty()) base = "recipe";
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private static String safeNamespace(String packName) {
        String namespace = safeFileStem(packName).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return namespace.trim().isEmpty() ? "customrecipe_export" : namespace;
    }

    private static void put(ZipOutputStream zip, String path, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String credits(String packName, String version, String targetName, Preview preview) {
        return "Datapack: " + packName + "\n"
                + "Target Minecraft version: " + version + "\n"
                + "Source library/world: " + targetName + "\n"
                + "Exported recipes: " + preview.exportable() + "\n"
                + "Skipped recipes: " + preview.skipped() + "\n\n"
                + "Generated with Custom Recipe by Zazac1.\n"
                + "Project: https://modrinth.com/mod/custom-recipe\n";
    }

    private static String exportReport(List<CustomRecipeEntry> recipes, Preview preview) {
        StringBuilder report = new StringBuilder()
                .append("Custom Recipe datapack export report\n")
                .append("Exported recipes: ").append(preview.exportable()).append('\n')
                .append("Skipped recipes: ").append(preview.skipped()).append("\n\n")
                .append("Only enabled, vanilla-compatible custom crafting recipes are exported.\n")
                .append("Settings that disable or modify existing recipes require Custom Recipe and are not part of a vanilla datapack.\n");
        if (preview.skipped() > 0) {
            report.append("\nSkipped entries:\n");
            int index = 1;
            for (CustomRecipeEntry recipe : recipes) {
                String reason = skipReason(recipe);
                if (reason == null) continue;
                String name = recipe == null ? "<empty>"
                        : recipe.id != null && !recipe.id.trim().isEmpty() ? recipe.id
                        : recipe.result == null || recipe.result.trim().isEmpty() ? "recipe " + index : recipe.result;
                report.append("- ").append(name).append(": ").append(reason).append('\n');
                index++;
            }
        }
        return report.toString();
    }

    private static final String LICENSE_TEXT =
            "MIT License\n\n"
            + "Copyright (c) 2026 Zazac1\n\n"
            + "Permission is hereby granted, free of charge, to any person obtaining a copy\n"
            + "of this software and associated documentation files (the \"Software\"), to deal\n"
            + "in the Software without restriction, including without limitation the rights\n"
            + "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n"
            + "copies of the Software, and to permit persons to whom the Software is\n"
            + "furnished to do so, subject to the following conditions:\n\n"
            + "The above copyright notice and this permission notice shall be included in all\n"
            + "copies or substantial portions of the Software.\n\n"
            + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n"
            + "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n"
            + "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n"
            + "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n"
            + "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n"
            + "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n"
            + "SOFTWARE.\n";

    private DatapackExporter() {}
}


