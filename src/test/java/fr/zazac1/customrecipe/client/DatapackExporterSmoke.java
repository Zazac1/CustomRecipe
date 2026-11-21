package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeEntry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/** Dependency-free smoke test, executed by Gradle's testDatapackExport task. */
public final class DatapackExporterSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build", "datapack-smoke");
        Files.createDirectories(output);

        CustomRecipeEntry compatible = new CustomRecipeEntry();
        compatible.id = "test_recipe";
        compatible.type = "shaped";
        compatible.pattern = List.of("AA", " A");
        compatible.keys.put("A", "minecraft:oak_planks");
        compatible.key_alternatives.put("A", List.of("minecraft:spruce_planks"));
        compatible.result = "minecraft:stick";
        compatible.count = 4;

        CustomRecipeEntry incompatible = new CustomRecipeEntry();
        incompatible.id = "counted_recipe";
        incompatible.type = "shapeless";
        incompatible.ingredients = new ArrayList<>(List.of("minecraft:stone"));
        incompatible.ingredient_counts = new ArrayList<>(List.of(2));
        incompatible.result = "minecraft:diamond";

        List<CustomRecipeEntry> recipes = List.of(compatible, incompatible);
        DatapackExporter.Preview preview = DatapackExporter.preview(recipes, "26.3");
        require(preview.exportable() == 1 && preview.skipped() == 1,
                "Compatibility preview returned incorrect totals");

        Path current = output.resolve("current.zip");
        DatapackExporter.export(current, "Smoke Pack", "Smoke test", "26.3", recipes, "Test world");
        try (ZipFile zip = new ZipFile(current.toFile(), StandardCharsets.UTF_8)) {
            requireEntry(zip, "CREDITS.txt");
            requireEntry(zip, "LICENSE.txt");
            requireEntry(zip, "EXPORT_REPORT.txt");
            String metadata = read(zip, "pack.mcmeta");
            require(metadata.contains("\"min_format\"") && metadata.contains("121"),
                    "26.3 pack metadata is missing its current format");
            String recipe = read(zip, "data/smoke-pack/recipe/test_recipe.json");
            require(recipe.contains("\"minecraft:oak_planks\"")
                            && recipe.contains("\"minecraft:spruce_planks\"")
                            && recipe.contains("\"id\": \"minecraft:stick\""),
                    "26.3 recipe JSON does not use the expected schema");
        }

        Path oneTwentyOne = output.resolve("1.21.1.zip");
        DatapackExporter.export(oneTwentyOne, "Smoke Pack", "Smoke test", "1.21.1",
                List.of(compatible), "Test world");
        try (ZipFile zip = new ZipFile(oneTwentyOne.toFile(), StandardCharsets.UTF_8)) {
            String recipe = read(zip, "data/smoke-pack/recipe/test_recipe.json");
            require(recipe.contains("\"item\": \"minecraft:oak_planks\"")
                            && recipe.contains("\"id\": \"minecraft:stick\""),
                    "1.21.1 mixed ingredient/result schema is incorrect");
        }

        Path oneTwenty = output.resolve("1.20.1.zip");
        DatapackExporter.export(oneTwenty, "Smoke Pack", "Smoke test", "1.20.1",
                List.of(compatible), "Test world");
        try (ZipFile zip = new ZipFile(oneTwenty.toFile(), StandardCharsets.UTF_8)) {
            String metadata = read(zip, "pack.mcmeta");
            String recipe = read(zip, "data/smoke-pack/recipes/test_recipe.json");
            require(metadata.contains("\"pack_format\": 15"), "1.20.1 pack format is incorrect");
            require(recipe.contains("\"item\": \"minecraft:oak_planks\"")
                            && recipe.contains("\"item\": \"minecraft:stick\""),
                    "1.20.1 recipe JSON does not use the legacy schema");
        }

        verifyEverySupportedVersion(output, compatible);
        verifyAliasesAndSanitization();
        verifyDuplicateNames(output, compatible);
        verifyRejectedFeatures(output, compatible);
    }

    private static void verifyEverySupportedVersion(Path output, CustomRecipeEntry recipe) throws Exception {
        Map<String, Integer> formats = new LinkedHashMap<>();
        formats.put("1.16.5", 6);
        formats.put("1.18.2", 9);
        formats.put("1.19.2", 10);
        formats.put("1.20.1", 15);
        formats.put("1.21.1", 48);
        formats.put("1.21.8", 81);
        formats.put("1.21.10", 88);
        formats.put("1.21.11", 94);
        formats.put("26.1", 101);
        formats.put("26.2", 107);
        formats.put("26.3", 121);

        require(DatapackExporter.supportedVersions().equals(String.join(", ", formats.keySet())),
                "Supported-version display is incomplete or unordered");
        for (Map.Entry<String, Integer> target : formats.entrySet()) {
            String version = target.getKey();
            require(DatapackExporter.supportsVersion(version), "Version unexpectedly rejected: " + version);
            Path zipPath = output.resolve("matrix-" + version + ".zip");
            DatapackExporter.export(zipPath, "Matrix Pack", "Version matrix", version,
                    List.of(recipe), "Matrix world");
            boolean singular = !version.startsWith("1.16") && !version.startsWith("1.18")
                    && !version.startsWith("1.19") && !version.startsWith("1.20");
            boolean modernIngredient = version.equals("1.21.8") || version.equals("1.21.10")
                    || version.equals("1.21.11") || version.startsWith("26.");
            boolean modernResult = singular;
            try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
                String metadata = read(zip, "pack.mcmeta");
                require(metadata.contains(String.valueOf(target.getValue())),
                        version + " pack format is missing");
                require(metadata.contains(version.startsWith("26.") ? "\"min_format\"" : "\"pack_format\""),
                        version + " pack metadata uses the wrong schema");
                String folder = singular ? "recipe" : "recipes";
                String json = read(zip, "data/matrix-pack/" + folder + "/test_recipe.json");
                require(json.contains(modernIngredient ? "\"minecraft:oak_planks\"" : "\"item\": \"minecraft:oak_planks\""),
                        version + " ingredient schema is incorrect");
                require(json.contains(modernResult ? "\"id\": \"minecraft:stick\"" : "\"item\": \"minecraft:stick\""),
                        version + " result schema is incorrect");
                require(read(zip, "CREDITS.txt").contains("Generated with Custom Recipe by Zazac1"),
                        version + " credits are incomplete");
                require(read(zip, "LICENSE.txt").contains("MIT License"),
                        version + " license is incomplete");
            }
        }
    }

    private static void verifyAliasesAndSanitization() {
        require(DatapackExporter.supportsVersion(" Minecraft 26.1.x "), "26.1.x alias is unsupported");
        require(DatapackExporter.supportsVersion("26.1.2"), "26.1.2 alias is unsupported");
        require(!DatapackExporter.supportsVersion("1.17.1"), "Unsupported version accepted");
        require(DatapackExporter.preview(List.of(), "1.17.1").reasons()
                        .contains("Unsupported Minecraft version"),
                "Unsupported-version preview has no explicit reason");
        require(DatapackExporter.safeFileStem("  My: Pack / Test  ").equals("My_-Pack-_-Test"),
                "Unsafe datapack filename was not sanitized");
        require(DatapackExporter.safeFileStem("   ").equals("custom-recipe-datapack"),
                "Blank datapack name has no safe fallback");
    }

    private static void verifyDuplicateNames(Path output, CustomRecipeEntry original) throws Exception {
        CustomRecipeEntry duplicate = compatibleCopy(original);
        duplicate.result = "minecraft:torch";
        Path destination = output.resolve("duplicates.zip");
        DatapackExporter.export(destination, "Duplicate Pack", "Duplicate IDs", "1.21.1",
                List.of(original, duplicate), "Duplicate world");
        try (ZipFile zip = new ZipFile(destination.toFile(), StandardCharsets.UTF_8)) {
            requireEntry(zip, "data/duplicate-pack/recipe/test_recipe.json");
            requireEntry(zip, "data/duplicate-pack/recipe/test_recipe_2.json");
        }
    }

    private static void verifyRejectedFeatures(Path output, CustomRecipeEntry compatible) throws Exception {
        List<CustomRecipeEntry> recipes = new ArrayList<>();
        recipes.add(compatible);

        CustomRecipeEntry disabled = compatibleCopy(compatible);
        disabled.id = "disabled";
        disabled.enabled = false;
        recipes.add(disabled);

        CustomRecipeEntry corrupted = compatibleCopy(compatible);
        corrupted.id = "corrupted";
        corrupted.corrupted = true;
        recipes.add(corrupted);

        CustomRecipeEntry counted = compatibleCopy(compatible);
        counted.id = "counted";
        counted.key_counts.put("A", 2);
        recipes.add(counted);

        CustomRecipeEntry distinct = compatibleCopy(compatible);
        distinct.id = "distinct";
        distinct.key_distinct.put("A", true);
        recipes.add(distinct);

        CustomRecipeEntry customItem = compatibleCopy(compatible);
        customItem.id = "custom_item";
        customItem.result = "customrecipe:custom_item/test";
        recipes.add(customItem);

        CustomRecipeEntry invalid = compatibleCopy(compatible);
        invalid.id = "invalid";
        invalid.keys.put("A", "Not A Valid ID");
        recipes.add(invalid);

        DatapackExporter.Preview preview = DatapackExporter.preview(recipes, "26.3");
        require(preview.exportable() == 1 && preview.skipped() == 6,
                "Rejected-feature preview returned incorrect totals");
        for (String reason : List.of("Disabled recipes", "Recipes with missing items",
                "Ingredient quantities greater than one", "Distinct-alternative constraints",
                "Custom Item references", "Invalid ingredient item IDs")) {
            require(preview.reasons().contains(reason), "Missing rejection reason: " + reason);
        }

        Path destination = output.resolve("rejections.zip");
        DatapackExporter.export(destination, "Rejection Pack", "Rejected features", "26.3",
                recipes, "Rejection world");
        try (ZipFile zip = new ZipFile(destination.toFile(), StandardCharsets.UTF_8)) {
            String report = read(zip, "EXPORT_REPORT.txt");
            require(report.contains("Skipped recipes: 6"), "Export report has incorrect skipped total");
            for (String id : List.of("disabled", "corrupted", "counted", "distinct", "custom_item", "invalid")) {
                require(report.contains("- " + id + ":"), "Export report omitted rejected recipe " + id);
            }
            requireEntry(zip, "data/rejection-pack/recipe/test_recipe.json");
        }
    }

    private static CustomRecipeEntry compatibleCopy(CustomRecipeEntry source) {
        CustomRecipeEntry copy = new CustomRecipeEntry();
        copy.id = source.id;
        copy.type = source.type;
        copy.pattern = new ArrayList<>(source.pattern);
        copy.keys.putAll(source.keys);
        source.key_alternatives.forEach((key, values) -> copy.key_alternatives.put(key, new ArrayList<>(values)));
        copy.key_counts.putAll(source.key_counts);
        copy.key_distinct.putAll(source.key_distinct);
        copy.result = source.result;
        copy.count = source.count;
        return copy;
    }

    private static void requireEntry(ZipFile zip, String path) {
        require(zip.getEntry(path) != null, "Missing ZIP entry: " + path);
    }

    private static String read(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path);
        require(entry != null, "Missing ZIP entry: " + path);
        try (var input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private DatapackExporterSmoke() {}
}
