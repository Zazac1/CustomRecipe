package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.literal;

/** Server-side command and permission-checked config synchronization. */
public final class ServerConfigNetworking {
    private static final int MAX_JSON_CHARS = 30_000;
    // Chunks are loaded transparently by the client while the list is scrolled.
    private static final int VANILLA_PAGE_SIZE = 40;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ServerConfigPayload.ID, ServerConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveServerConfigPayload.ID, SaveServerConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ValidatedServerConfigPayload.ID, ValidatedServerConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ValidateServerConfigPayload.ID, ValidateServerConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VanillaRecipePagePayload.ID, VanillaRecipePagePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VanillaRecipeQueryPayload.ID, VanillaRecipeQueryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VanillaRecipeDetailsPayload.ID, VanillaRecipeDetailsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VanillaRecipeDetailsQueryPayload.ID, VanillaRecipeDetailsQueryPayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> awardDefaultRecipes(handler.player, server, false));
        ServerLifecycleEvents.SERVER_STARTED.register(ServerConfigNetworking::refreshRecipeConflicts);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                refreshRecipeConflicts(server);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    awardDefaultRecipes(player, server, true);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("customrecipe")
                        .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(context -> openEditor(context.getSource()))
        ));

        ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS))) {
                player.sendMessage(Text.literal("[Custom Recipe] Permission denied."), false);
                return;
            }
            if (payload.json().length() > MAX_JSON_CHARS) {
                player.sendMessage(Text.literal("[Custom Recipe] Server config is too large."), false);
                return;
            }

            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                player.sendMessage(Text.literal("[Custom Recipe] Invalid JSON; nothing was changed."), false);
                return;
            }

            ConfigLoader.saveAndInvalidate(config);
            player.sendMessage(Text.literal("[Custom Recipe] Server config saved. Reloading recipes..."), false);
            context.server().getCommandManager().parseAndExecute(player.getCommandSource(), "reload");
        });

        ServerPlayNetworking.registerGlobalReceiver(ValidateServerConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS))
                    || payload.json().length() > MAX_JSON_CHARS) return;
            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) return;

            validateProposedConfig(context.server(), config);
            String json = ConfigLoader.toJson(config);
            if (json.length() <= MAX_JSON_CHARS) {
                ServerPlayNetworking.send(player, new ValidatedServerConfigPayload(json));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VanillaRecipeQueryPayload.ID, (payload, context) -> {
            if (!context.player().getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS))) {
                return;
            }
            RecipeQuery query = GSON.fromJson(payload.json(), RecipeQuery.class);
            if (query == null) return;
            VanillaRecipePage page = findVanillaRecipes(context.server(), query);
            String json = GSON.toJson(page);
            if (json.length() <= MAX_JSON_CHARS) {
                ServerPlayNetworking.send(context.player(), new VanillaRecipePagePayload(json));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsQueryPayload.ID, (payload, context) -> {
            if (!context.player().getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS))) return;
            VanillaRecipeDetails details = findVanillaRecipeDetails(context.server(), payload.recipeId());
            String json = GSON.toJson(details);
            if (json.length() <= MAX_JSON_CHARS) {
                ServerPlayNetworking.send(context.player(), new VanillaRecipeDetailsPayload(json));
            }
        });

    }

    /** Tags are bound only after data-pack reload, so conflict comparison must happen here. */
    private static void refreshRecipeConflicts(net.minecraft.server.MinecraftServer server) {
        ModConfig config = ConfigLoader.get();
        boolean changed = false;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            RecipeEntry<?> customEntry = server.getRecipeManager()
                    .get(RegistryKey.of(RegistryKeys.RECIPE, entry.serverRecipeId())).orElse(null);
            if (customEntry != null && customEntry.value() instanceof CraftingRecipe custom) {
                for (RecipeEntry<?> candidate : server.getRecipeManager().values()) {
                    Identifier id = candidate.id().getValue();
                    if (!(candidate.value() instanceof CraftingRecipe existing)
                            || (id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/"))) {
                        continue;
                    }
                    if (sameExactInputs(custom, existing)) {
                        if (sameOutputItem(custom, existing, server)) conflicts.add(id.toString());
                        else sameShape.add(id.toString());
                    }
                }
            }
            conflicts.sort(String::compareTo);
            sameShape.sort(String::compareTo);
            if (!conflicts.equals(entry.conflicting_recipes) || !sameShape.equals(entry.same_shape_recipes)) {
                entry.conflicting_recipes = conflicts;
                entry.same_shape_recipes = sameShape;
                changed = true;
            }
        }
        if (changed) ConfigLoader.saveIntegrityState(config);
    }

    /** Validates unsaved OP drafts against the server's items and default recipes only. */
    private static void validateProposedConfig(net.minecraft.server.MinecraftServer server, ModConfig config) {
        for (CustomRecipeEntry entry : config.custom_recipes) {
            RecipeIntegrity.refresh(entry);
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            RecipeSignature signature = Boolean.TRUE.equals(entry.corrupted) ? null : signatureOf(entry);
            if (signature != null) {
                for (RecipeEntry<?> candidate : server.getRecipeManager().values()) {
                    Identifier id = candidate.id().getValue();
                    if (!(candidate.value() instanceof CraftingRecipe wrapped)
                            || (id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/"))) {
                        continue;
                    }
                    CraftingRecipe existing = unwrap(wrapped);
                    if (!signature.equals(signatureOf(existing))) continue;
                    if (sameOutputItem(entry, existing, server)) conflicts.add(id.toString());
                    else sameShape.add(id.toString());
                }
            }
            conflicts.sort(String::compareTo);
            sameShape.sort(String::compareTo);
            entry.conflicting_recipes = conflicts;
            entry.same_shape_recipes = sameShape;
        }
    }

    private static boolean sameOutputItem(CustomRecipeEntry entry, CraftingRecipe candidate,
                                          net.minecraft.server.MinecraftServer server) {
        Identifier resultId = Identifier.tryParse(entry.result);
        if (resultId == null) return false;
        try {
            ItemStack result = candidate.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            return !result.isEmpty() && resultId.equals(Registries.ITEM.getId(result.getItem()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static RecipeSignature signatureOf(CustomRecipeEntry entry) {
        if ("shaped".equalsIgnoreCase(entry.type)) {
            if (entry.pattern == null || entry.pattern.isEmpty() || entry.keys == null) return null;
            int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0);
            List<String> slots = new ArrayList<>();
            for (String row : entry.pattern) {
                for (int column = 0; column < width; column++) {
                    char symbol = column < row.length() ? row.charAt(column) : ' ';
                    String item = symbol == ' ' ? "" : entry.keys.get(String.valueOf(symbol));
                    if (symbol != ' ' && (item == null || item.isBlank())) return null;
                    slots.add(item == null ? "" : item.trim());
                }
            }
            return trimSignature(true, width, entry.pattern.size(), slots);
        }
        if (entry.ingredients == null || entry.ingredients.isEmpty()) return null;
        List<String> ingredients = new ArrayList<>();
        for (String item : entry.ingredients) {
            if (item == null || item.isBlank()) return null;
            ingredients.add(item.trim());
        }
        ingredients.sort(String::compareTo);
        return new RecipeSignature(false, ingredients.size(), 1, ingredients);
    }

    private static RecipeSignature signatureOf(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            List<String> slots = new ArrayList<>();
            for (java.util.Optional<Ingredient> ingredient : shaped.getIngredients()) {
                slots.add(ingredient.map(ServerConfigNetworking::ingredientSignature).orElse(""));
            }
            return trimSignature(true, shaped.getWidth(), shaped.getHeight(), slots);
        }
        List<String> ingredients = recipe.getIngredientPlacement().getIngredients().stream()
                .map(ServerConfigNetworking::ingredientSignature).sorted().toList();
        return new RecipeSignature(false, ingredients.size(), 1, ingredients);
    }

    private static RecipeSignature trimSignature(boolean shaped, int sourceWidth, int sourceHeight, List<String> slots) {
        int left = sourceWidth, right = -1, top = sourceHeight, bottom = -1;
        for (int row = 0; row < sourceHeight; row++) {
            for (int column = 0; column < sourceWidth; column++) {
                if (slots.get(row * sourceWidth + column).isBlank()) continue;
                left = Math.min(left, column);
                right = Math.max(right, column);
                top = Math.min(top, row);
                bottom = Math.max(bottom, row);
            }
        }
        if (right < left || bottom < top) return null;
        List<String> trimmed = new ArrayList<>();
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                trimmed.add(slots.get(row * sourceWidth + column));
            }
        }
        return new RecipeSignature(shaped, right - left + 1, bottom - top + 1, trimmed);
    }

    private record RecipeSignature(boolean shaped, int width, int height, List<String> ingredients) {}

    private static boolean sameExactInputs(CraftingRecipe first, CraftingRecipe second) {
        boolean firstShaped = first instanceof ShapedRecipe;
        boolean secondShaped = second instanceof ShapedRecipe;
        if (firstShaped != secondShaped) return false;
        if (firstShaped) {
            ShapedRecipe a = (ShapedRecipe) first;
            ShapedRecipe b = (ShapedRecipe) second;
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
            List<java.util.Optional<Ingredient>> ingredientsA = a.getIngredients();
            List<java.util.Optional<Ingredient>> ingredientsB = b.getIngredients();
            if (ingredientsA.size() != ingredientsB.size()) return false;
            for (int i = 0; i < ingredientsA.size(); i++) {
                if (ingredientsA.get(i).isPresent() != ingredientsB.get(i).isPresent()) return false;
                if (ingredientsA.get(i).isPresent()
                        && !ingredientSignature(ingredientsA.get(i).get()).equals(ingredientSignature(ingredientsB.get(i).get()))) {
                    return false;
                }
            }
            return true;
        }

        List<String> firstIngredients = first.getIngredientPlacement().getIngredients().stream()
                .map(ServerConfigNetworking::ingredientSignature).sorted().toList();
        List<String> secondIngredients = second.getIngredientPlacement().getIngredients().stream()
                .map(ServerConfigNetworking::ingredientSignature).sorted().toList();
        return firstIngredients.equals(secondIngredients);
    }

    /** The output count may differ; a different output item is safe for the recipe book. */
    private static boolean sameOutputItem(CraftingRecipe first, CraftingRecipe second,
                                          net.minecraft.server.MinecraftServer server) {
        try {
            ItemStack firstResult = first.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            ItemStack secondResult = second.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            return !firstResult.isEmpty() && !secondResult.isEmpty()
                    && firstResult.getItem() == secondResult.getItem();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return ingredient.getMatchingItems()
                .map(item -> Registries.ITEM.getId(item.value()).toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** Quietly adds enabled defaults to the recipe book without recipe toasts. */
    private static void awardDefaultRecipes(ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                                            boolean refreshBook) {
        List<RecipeEntry<?>> recipes = new ArrayList<>();
        ModConfig config = ConfigLoader.get();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default)
                    || Boolean.FALSE.equals(entry.enabled)
                    || Boolean.TRUE.equals(entry.corrupted)
                    || (server.isDedicated() && Boolean.FALSE.equals(entry.server_enabled))) {
                continue;
            }
            server.getRecipeManager().get(RegistryKey.of(RegistryKeys.RECIPE, entry.serverRecipeId()))
                    .ifPresent(recipes::add);
        }
        for (String builtinId : config.known_by_default_builtin) {
            if (builtinId == null || config.disabled_builtin.contains(builtinId)) continue;
            Identifier id = Identifier.tryParse(CustomRecipeMod.MOD_ID + ":" + builtinId);
            if (id == null) continue;
            server.getRecipeManager().get(RegistryKey.of(RegistryKeys.RECIPE, id))
                    .ifPresent(recipes::add);
        }

        boolean changed = false;
        var book = player.getRecipeBook();
        for (RecipeEntry<?> recipe : recipes) {
            if (!book.isUnlocked(recipe.id())) {
                book.unlock(recipe.id());
                changed = true;
            }
        }
        // A /reload can also hide a previously visible vanilla recipe variant.
        // Resend it after reload so the client drops stale auto-fill entries.
        if (changed || refreshBook) book.sendInitRecipesPacket(player);
    }

    private static int openEditor(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) {
            source.sendError(Text.literal("[Custom Recipe] This client needs the Custom Recipe mod to open the editor."));
            return 0;
        }

        sendEditor(player);
        source.sendFeedback(() -> Text.literal("[Custom Recipe] Opening the server recipe editor."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendEditor(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) return;
        // The config can have changed through the local editor since the last reload.
        ConfigLoader.invalidate();
        String json = ConfigLoader.toJson(ConfigLoader.get());
        if (json.length() > MAX_JSON_CHARS) {
            player.sendMessage(Text.literal("[Custom Recipe] The server config is too large to send to the editor."), false);
            return;
        }
        ServerPlayNetworking.send(player, new ServerConfigPayload(json));
    }

    private static VanillaRecipePage findVanillaRecipes(net.minecraft.server.MinecraftServer server, RecipeQuery request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            Identifier recipeId = entry.id().getValue();
            if (!(entry.value() instanceof CraftingRecipe wrappedRecipe)
                    || (recipeId.getNamespace().equals(CustomRecipeMod.MOD_ID) && recipeId.getPath().startsWith("custom/"))) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);

            // Special recipes (for example decorated pots) require a real grid and throw on EMPTY.
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            } catch (RuntimeException ignored) {
                // Their recipe ID remains searchable and they can still be disabled.
            }
            String resultId = result.isEmpty() ? entry.id().getValue().toString()
                    : Registries.ITEM.getId(result.getItem()).toString();
            int gridWidth = 0;
            int gridHeight = 0;
            boolean shapeless = !(recipe instanceof ShapedRecipe);
            List<String> ingredients = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                // getIngredients includes blank cells and preserves the declared pattern.
                gridWidth = shaped.getWidth();
                gridHeight = shaped.getHeight();
                for (var ingredient : shaped.getIngredients()) {
                    ingredients.add(ingredient.map(ServerConfigNetworking::firstMatchingId).orElse(""));
                }
            } else {
                // Shapeless recipes deliberately keep the JSON ingredient order.
                for (Ingredient ingredient : recipe.getIngredientPlacement().getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                }
            }

            boolean outputMatch = request.matchOutput() && resultId.contains(query);
            boolean ingredientMatch = request.matchIngredients() && ingredients.stream().anyMatch(id -> id.contains(query));
            if (query.isEmpty() || outputMatch || ingredientMatch) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(
                        entry.id().getValue().toString(), resultId,
                        toPreviewSlots(ingredients, gridWidth, gridHeight, shapeless),
                        gridWidth, gridHeight, shapeless));
            }
        }

        matches.sort(Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
        int total = matches.size();
        int page = Math.max(0, request.page());
        int from = Math.min(page * VANILLA_PAGE_SIZE, total);
        int to = Math.min(from + VANILLA_PAGE_SIZE, total);
        return new VanillaRecipePage(matches.subList(from, to), page, total);
    }

    private static String firstMatchingId(Ingredient ingredient) {
        return ingredient.getMatchingItems()
                .map(item -> Registries.ITEM.getId(item.value()).toString())
                .findFirst()
                .orElse("");
    }

    private static VanillaRecipeDetails findVanillaRecipeDetails(net.minecraft.server.MinecraftServer server, String rawId) {
        var identifier = net.minecraft.util.Identifier.tryParse(rawId);
        if (identifier == null) return new VanillaRecipeDetails(rawId, List.of());
        var id = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.RECIPE, identifier);
        RecipeEntry<?> entry = server.getRecipeManager().get(id).orElse(null);
        if (entry == null || !(entry.value() instanceof CraftingRecipe wrappedRecipe)) return new VanillaRecipeDetails(rawId, List.of());
        CraftingRecipe recipe = unwrap(wrappedRecipe);
        List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, List.of()));
        int gridWidth = 0;
        int gridHeight = 0;
        boolean shapeless = !(recipe instanceof ShapedRecipe);
        if (recipe instanceof ShapedRecipe shaped) {
            gridWidth = shaped.getWidth();
            gridHeight = shaped.getHeight();
            List<java.util.Optional<Ingredient>> ingredients = shaped.getIngredients();
            for (int row = 0; row < gridHeight && row < 3; row++) {
                for (int column = 0; column < gridWidth && column < 3; column++) {
                    int source = row * gridWidth + column;
                    choices.set(row * 3 + column, source < ingredients.size()
                            ? ingredientChoices(ingredients.get(source).orElse(null)) : List.of());
                }
            }
        } else {
            List<Ingredient> ingredients = recipe.getIngredientPlacement().getIngredients();
            for (int slot = 0; slot < ingredients.size() && slot < 9; slot++) choices.set(slot, ingredientChoices(ingredients.get(slot)));
        }

        var variants = new java.util.TreeSet<String>();
        for (List<String> choice : choices) if (choice.size() > 1) variants.addAll(choice);
        List<VanillaRecipeDetails.VariantPreview> previews = new ArrayList<>();
        for (String material : variants.stream().limit(48).toList()) {
            List<String> slots = new ArrayList<>(9);
            for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.getFirst()));
            previews.add(new VanillaRecipeDetails.VariantPreview(material, slots));
        }
        return new VanillaRecipeDetails(rawId, previews);
    }

    private static CraftingRecipe unwrap(CraftingRecipe recipe) {
        while (true) {
            if (recipe instanceof VariantFilteredCraftingRecipe filtered) {
                recipe = filtered.delegate();
            } else if (recipe instanceof DisabledCraftingRecipe disabled) {
                recipe = disabled.delegate();
            } else {
                return recipe;
            }
        }
    }

    private static List<String> ingredientChoices(Ingredient ingredient) {
        if (ingredient == null) return List.of();
        return ingredient.getMatchingItems().map(item -> Registries.ITEM.getId(item.value()).toString()).sorted().toList();
    }

    /** Always send a final 3x3 layout so no client-side axis interpretation is needed. */
    private static List<String> toPreviewSlots(List<String> ingredients, int gridWidth, int gridHeight, boolean shapeless) {
        List<String> slots = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        if (shapeless) {
            for (int i = 0; i < ingredients.size() && i < 9; i++) slots.set(i, ingredients.get(i));
            return slots;
        }
        for (int row = 0; row < gridHeight && row < 3; row++) {
            for (int column = 0; column < gridWidth && column < 3; column++) {
                int source = row * gridWidth + column;
                slots.set(row * 3 + column, source < ingredients.size() ? ingredients.get(source) : "");
            }
        }
        return slots;
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput, int page) {}

    private ServerConfigNetworking() {}
}
