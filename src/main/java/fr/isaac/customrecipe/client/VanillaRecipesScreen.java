package fr.isaac.customrecipe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.isaac.customrecipe.VanillaRecipePage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarFile;

/** Server-filtered vanilla crafting recipe browser for OPs. */
@Environment(EnvType.CLIENT)
public class VanillaRecipesScreen extends Screen {
    private static final int ROW = 20;
    private final ConfigScreen parent;
    private final boolean localMode;
    private String query = "";
    private boolean matchIngredients = true;
    private boolean matchOutput = true;
    private final List<VanillaRecipePage.VanillaRecipeInfo> recipes = new ArrayList<>();
    private int total;
    private int nextPage;
    private int scroll;
    private boolean loading;
    private boolean searchStarted;
    private TextFieldWidget searchField;

    public VanillaRecipesScreen(ConfigScreen parent) {
        this(parent, false);
    }

    /** Local ModMenu mode reads the vanilla recipe data already loaded by the client. */
    public VanillaRecipesScreen(ConfigScreen parent, boolean localMode) {
        super(Text.literal("Vanilla Crafting Recipes"));
        this.parent = parent;
        this.localMode = localMode;
    }

    @Override
    protected void init() {
        searchField = addDrawableChild(new TextFieldWidget(textRenderer, 8, 26, width - 244, 18, Text.literal("Search item or recipe ID")));
        searchField.setText(query);
        searchField.setChangedListener(value -> query = value);

        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), b -> resetSearch())
                .dimensions(width - 236, 26, 60, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(matchIngredients ? "Ingredient: ON" : "Ingredient: OFF"), b -> {
            matchIngredients = !matchIngredients;
            resetSearch();
        }).dimensions(width - 172, 26, 90, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(matchOutput ? "Output: ON" : "Output: OFF"), b -> {
            matchOutput = !matchOutput;
            resetSearch();
        }).dimensions(width - 78, 26, 70, 18).build());

        int visibleRows = visibleRows();
        for (int i = 0; i < visibleRows && scroll + i < recipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = recipes.get(scroll + i);
            int y = 52 + i * ROW;
            boolean disabled = parent.disabledRecipes.contains(recipe.id());
            addDrawableChild(ButtonWidget.builder(recipeLabel(recipe), b -> client.setScreen(new VanillaRecipeDetailsScreen(this, recipe)))
                    .dimensions(30, y + 1, width - 122, 18).build());
            addDrawableChild(ButtonWidget.builder(disabled ? Text.literal("Disabled").withColor(0xFF5555)
                            : Text.literal("Enabled").withColor(0x55FF55), b -> toggle(recipe.id()))
                    .dimensions(width - 84, y + 1, 76, 18).build());
        }

        int bottom = height - 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
                .dimensions(width / 2 - 50, bottom, 100, 18).build());

        if (!searchStarted) resetSearch();
    }

    void applyResult(VanillaRecipePage page) {
        if (page.page() == 0) {
            recipes.clear();
            scroll = 0;
        }
        Set<String> present = new HashSet<>();
        for (VanillaRecipePage.VanillaRecipeInfo recipe : recipes) present.add(recipe.id());
        for (VanillaRecipePage.VanillaRecipeInfo recipe : page.recipes()) {
            if (present.add(recipe.id())) recipes.add(recipe);
        }
        total = page.total();
        nextPage = page.page() + 1;
        loading = false;
        clearAndInit();
    }

    private void resetSearch() {
        searchStarted = true;
        scroll = 0;
        nextPage = 0;
        total = 0;
        recipes.clear();
        loading = true;
        if (localMode) {
            VanillaRecipePage page = findLocalRecipes();
            recipes.addAll(page.recipes());
            total = page.total();
            nextPage = 1;
            loading = false;
            clearAndInit();
            return;
        }
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput, 0);
    }

    private void loadMore() {
        if (localMode || loading || recipes.size() >= total) return;
        loading = true;
        ClientServerConfigNetworking.searchVanilla(query, matchIngredients, matchOutput, nextPage);
    }

    private VanillaRecipePage findLocalRecipes() {
        String loweredQuery = query.trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();
        Map<Identifier, Resource> resources = client.getResourceManager().findResources("recipe",
                id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> resource : resources.entrySet()) {
            try (var input = resource.getValue().getInputStream()) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                String recipeId = "minecraft:" + resource.getKey().getPath()
                        .substring("recipe/".length(), resource.getKey().getPath().length() - ".json".length());
                addLocalRecipe(matches, recipeId, json, loweredQuery);
            } catch (Exception ignored) {
                // A malformed optional resource is simply omitted from the local browser.
            }
        }

        // At the title screen ModMenu has not mounted server-data resources yet.
        // Vanilla recipes are still available in Minecraft's own JAR, so use it as a fallback.
        if (matches.isEmpty()) loadBundledVanillaRecipes(matches, loweredQuery);

        matches.sort(java.util.Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
        return new VanillaRecipePage(matches, 0, matches.size());
    }

    private void addLocalRecipe(List<VanillaRecipePage.VanillaRecipeInfo> matches, String recipeId, String json, String loweredQuery) {
        if (!json.contains("crafting_")) return;
        String resultId = findResultId(json, recipeId);
        boolean outputMatch = matchOutput && resultId.toLowerCase(Locale.ROOT).contains(loweredQuery);
        boolean ingredientMatch = matchIngredients && json.toLowerCase(Locale.ROOT).contains(loweredQuery);
        if (loweredQuery.isEmpty() || outputMatch || ingredientMatch) {
            RecipeLayout layout = findLocalRecipeLayout(json);
            matches.add(new VanillaRecipePage.VanillaRecipeInfo(recipeId, resultId, toPreviewSlots(layout),
                    layout.width(), layout.height(), layout.shapeless()));
        }
    }

    private void loadBundledVanillaRecipes(List<VanillaRecipePage.VanillaRecipeInfo> matches, String loweredQuery) {
        try (JarFile jar = minecraftJar()) {
            if (jar == null) return;
            Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String path = entry.getName();
                if (!path.startsWith("data/minecraft/recipe/") || !path.endsWith(".json")) continue;
                try (var input = jar.getInputStream(entry)) {
                    String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    String recipeId = "minecraft:" + path.substring("data/minecraft/recipe/".length(), path.length() - ".json".length());
                    addLocalRecipe(matches, recipeId, json, loweredQuery);
                }
            }
        } catch (Exception ignored) {
            // Normal in unusual launchers that do not expose a Minecraft JAR code source.
        }
    }

    private String findResultId(String json, String fallback) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement result = root.get("result");
            if (result == null) return fallback;
            if (result.isJsonPrimitive()) return result.getAsString();
            if (result.isJsonObject() && result.getAsJsonObject().has("id")) {
                return result.getAsJsonObject().get("id").getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private RecipeLayout findLocalRecipeLayout(String json) {
        List<String> ingredients = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";
            if (type.contains("crafting_shaped") && root.has("pattern") && root.has("key")) {
                var pattern = root.getAsJsonArray("pattern");
                JsonObject key = root.getAsJsonObject("key");
                int width = 0;
                for (JsonElement row : pattern) width = Math.max(width, row.getAsString().length());
                for (JsonElement row : pattern) {
                    String symbols = row.getAsString();
                    for (int x = 0; x < width; x++) {
                        ingredients.add(x >= symbols.length() || symbols.charAt(x) == ' ' ? ""
                                : firstLocalIngredientId(key.get(String.valueOf(symbols.charAt(x)))));
                    }
                }
                return new RecipeLayout(ingredients, width, pattern.size(), false);
            }
            collectIngredientIds(root.get("ingredients"), ingredients);
        } catch (Exception ignored) {}
        return new RecipeLayout(ingredients, 0, 0, true);
    }

    private String firstLocalIngredientId(JsonElement element) {
        List<String> choices = new ArrayList<>();
        collectIngredientIds(element, choices);
        return choices.isEmpty() ? "" : choices.getFirst();
    }

    private void collectIngredientIds(JsonElement element, List<String> ingredients) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (!raw.isBlank()) ingredients.add(raw);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectIngredientIds(child, ingredients);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) {
            ingredients.add(object.get("item").getAsString());
        } else if (object.has("tag")) {
            ingredients.add("#" + object.get("tag").getAsString());
        } else {
            for (Map.Entry<String, JsonElement> child : object.entrySet()) collectIngredientIds(child.getValue(), ingredients);
        }
    }

    private record RecipeLayout(List<String> ingredients, int width, int height, boolean shapeless) {}

    private List<String> toPreviewSlots(RecipeLayout layout) {
        List<String> slots = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        if (layout.shapeless()) {
            for (int i = 0; i < layout.ingredients().size() && i < 9; i++) slots.set(i, layout.ingredients().get(i));
            return slots;
        }
        for (int row = 0; row < layout.height() && row < 3; row++) {
            for (int column = 0; column < layout.width() && column < 3; column++) {
                int source = row * layout.width() + column;
                slots.set(row * 3 + column, source < layout.ingredients().size() ? layout.ingredients().get(source) : "");
            }
        }
        return slots;
    }

    private void toggle(String recipeId) {
        if (!parent.disabledRecipes.remove(recipeId)) parent.disabledRecipes.add(recipeId);
        clearAndInit();
    }

    void requestDetails(VanillaRecipeDetailsScreen screen, String recipeId) {
        if (localMode) {
            client.execute(() -> screen.applyDetails(findLocalRecipeDetails(recipeId)));
        } else {
            ClientServerConfigNetworking.requestVanillaDetails(recipeId);
        }
    }

    /** Mirrors the server variant query using the vanilla JSON and client item tags. */
    private fr.isaac.customrecipe.VanillaRecipeDetails findLocalRecipeDetails(String recipeId) {
        Identifier id = Identifier.tryParse(recipeId);
        if (id == null) return new fr.isaac.customrecipe.VanillaRecipeDetails(recipeId, List.of());
        Identifier resourceId = Identifier.of(id.getNamespace(), "recipe/" + id.getPath() + ".json");
        Optional<String> json = readLocalRecipeJson(resourceId);
        if (json.isEmpty()) return new fr.isaac.customrecipe.VanillaRecipeDetails(recipeId, List.of());

        try {
            JsonObject root = JsonParser.parseString(json.get()).getAsJsonObject();
            List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, List.of()));
            boolean shaped = root.has("type") && root.get("type").getAsString().contains("crafting_shaped");
            if (shaped && root.has("pattern") && root.has("key")) {
                var pattern = root.getAsJsonArray("pattern");
                JsonObject key = root.getAsJsonObject("key");
                int width = 0;
                for (JsonElement row : pattern) width = Math.max(width, row.getAsString().length());
                for (int row = 0; row < pattern.size() && row < 3; row++) {
                    String symbols = pattern.get(row).getAsString();
                    for (int column = 0; column < width && column < 3; column++) {
                        JsonElement ingredient = column < symbols.length() && symbols.charAt(column) != ' '
                                ? key.get(String.valueOf(symbols.charAt(column))) : null;
                        choices.set(row * 3 + column, localIngredientChoices(ingredient));
                    }
                }
            } else if (root.has("ingredients") && root.get("ingredients").isJsonArray()) {
                var ingredients = root.getAsJsonArray("ingredients");
                for (int slot = 0; slot < ingredients.size() && slot < 9; slot++) {
                    choices.set(slot, localIngredientChoices(ingredients.get(slot)));
                }
            }

            TreeSet<String> variants = new TreeSet<>();
            for (List<String> choice : choices) if (choice.size() > 1) variants.addAll(choice);
            List<fr.isaac.customrecipe.VanillaRecipeDetails.VariantPreview> previews = new ArrayList<>();
            for (String material : variants.stream().limit(48).toList()) {
                List<String> slots = new ArrayList<>(9);
                for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.getFirst()));
                previews.add(new fr.isaac.customrecipe.VanillaRecipeDetails.VariantPreview(material, slots));
            }
            return new fr.isaac.customrecipe.VanillaRecipeDetails(recipeId, previews);
        } catch (Exception ignored) {
            return new fr.isaac.customrecipe.VanillaRecipeDetails(recipeId, List.of());
        }
    }

    private Optional<String> readLocalRecipeJson(Identifier resourceId) {
        try {
            var resource = client.getResourceManager().getResource(resourceId).orElse(null);
            if (resource != null) {
                try (var input = resource.getInputStream()) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            try (JarFile jar = minecraftJar()) {
                if (jar == null) return Optional.empty();
                var entry = jar.getJarEntry("data/" + resourceId.getNamespace() + "/" + resourceId.getPath());
                if (entry == null) return Optional.empty();
                try (var input = jar.getInputStream(entry)) {
                    return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private JarFile minecraftJar() throws Exception {
        var source = client.getClass().getProtectionDomain().getCodeSource();
        if (source == null) return null;
        Path path = Path.of(source.getLocation().toURI());
        return java.nio.file.Files.isRegularFile(path) ? new JarFile(path.toFile()) : null;
    }

    private List<String> localIngredientChoices(JsonElement element) {
        TreeSet<String> choices = new TreeSet<>();
        collectLocalIngredientChoices(element, choices);
        return new ArrayList<>(choices);
    }

    private void collectLocalIngredientChoices(JsonElement element, Set<String> choices) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(raw.substring(1));
                if (tagId != null) collectLocalTagItems(tagId, choices, new HashSet<>());
            } else if (!raw.isBlank()) {
                choices.add(raw);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectLocalIngredientChoices(child, choices);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) {
            choices.add(object.get("item").getAsString());
            return;
        }
        if (object.has("tag")) {
            Identifier tagId = Identifier.tryParse(object.get("tag").getAsString());
            if (tagId != null) collectLocalTagItems(tagId, choices, new HashSet<>());
        }
    }

    /** Reads tag JSON too, so variants are available from ModMenu before joining a world. */
    private void collectLocalTagItems(Identifier tagId, Set<String> choices, Set<Identifier> visited) {
        if (!visited.add(tagId)) return;
        Identifier tagResource = Identifier.of(tagId.getNamespace(), "tags/item/" + tagId.getPath() + ".json");
        Optional<String> json = readLocalRecipeJson(tagResource);
        if (json.isEmpty()) return;
        try {
            JsonObject root = JsonParser.parseString(json.get()).getAsJsonObject();
            if (!root.has("values") || !root.get("values").isJsonArray()) return;
            for (JsonElement value : root.getAsJsonArray("values")) {
                String raw = value.isJsonPrimitive() ? value.getAsString()
                        : value.isJsonObject() && value.getAsJsonObject().has("id")
                        ? value.getAsJsonObject().get("id").getAsString() : "";
                if (raw.startsWith("#")) {
                    Identifier nested = Identifier.tryParse(raw.substring(1));
                    if (nested != null) collectLocalTagItems(nested, choices, visited);
                } else if (!raw.isBlank()) {
                    choices.add(raw);
                }
            }
        } catch (Exception ignored) {
            // An optional malformed tag must not prevent the recipe preview from opening.
        }
    }

    boolean isVariantDisabled(String recipeId, String materialId) {
        return parent.disabledRecipeVariants.stream().anyMatch(rule -> recipeId.equals(rule.recipe_id) && materialId.equals(rule.material_id));
    }

    void toggleVariant(String recipeId, String materialId) {
        for (int i = 0; i < parent.disabledRecipeVariants.size(); i++) {
            var rule = parent.disabledRecipeVariants.get(i);
            if (recipeId.equals(rule.recipe_id) && materialId.equals(rule.material_id)) {
                parent.disabledRecipeVariants.remove(i);
                return;
            }
        }
        parent.disabledRecipeVariants.add(new fr.isaac.customrecipe.RecipeVariantRule(recipeId, materialId));
    }

    boolean isRecipeDisabled(String recipeId) { return parent.disabledRecipes.contains(recipeId); }

    void toggleAllVariants(String recipeId) { toggle(recipeId); }

    private int visibleRows() {
        return Math.max(1, (height - 84) / ROW);
    }

    private Text recipeLabel(VanillaRecipePage.VanillaRecipeInfo recipe) {
        return Text.literal(itemName(recipe.result()))
                .append(Text.literal("  " + shortId(recipe.id())).withColor(0xAAAAAA));
    }

    private String itemName(String id) {
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        return item == null || item == Items.AIR ? shortId(id) : new ItemStack(item).getName().getString();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        super.render(ctx, mouseX, mouseY, delta);
        if (loading && recipes.isEmpty()) {
            ctx.drawText(textRenderer, "Loading recipes...", 8, 54, 0xBBBBBB, false);
            return;
        }

        ctx.drawText(textRenderer, "Found " + total + " recipes - scroll to browse - click a name to preview", 8, 8, 0xFFFFEE88, false);
        if (recipes.isEmpty()) {
            ctx.drawText(textRenderer, "No recipe found. Edit the search field, then press Search.", 8, 54, 0xFFBBBBBB, false);
        }
        for (int i = 0; i < visibleRows() && scroll + i < recipes.size(); i++) {
            VanillaRecipePage.VanillaRecipeInfo recipe = recipes.get(scroll + i);
            int y = 52 + i * ROW;
            ctx.fill(6, y, width - 88, y + ROW - 1, parent.disabledRecipes.contains(recipe.id()) ? 0x44550000 : 0x22005500);
            var item = Registries.ITEM.get(Identifier.tryParse(recipe.result()));
            if (item != null && item != Items.AIR) ctx.drawItem(new ItemStack(item), 10, y + 2);
        }
        if (loading) ctx.drawText(textRenderer, "Loading more...", 8, height - 42, 0xFFBBBBBB, false);
    }

    private String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, recipes.size() - visibleRows());
        int oldScroll = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        if (scroll != oldScroll) clearAndInit();
        if (scroll + visibleRows() >= recipes.size() - 3) loadMore();
        return true;
    }

    @Override public boolean shouldPause() { return false; }
}
