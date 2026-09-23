package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.commands.Commands.literal;

/** Server-side command and permission-checked config synchronization. */
public final class ServerConfigNetworking {
    private static final int MAX_JSON_CHARS = 30_000;
    // Chunks are loaded transparently by the client while the list is scrolled.
    private static final int VANILLA_PAGE_SIZE = 40;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(ServerConfigPayload.ID, ServerConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveServerConfigPayload.ID, SaveServerConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ValidatedServerConfigPayload.ID, ValidatedServerConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ValidateServerConfigPayload.ID, ValidateServerConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VanillaRecipePagePayload.ID, VanillaRecipePagePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VanillaRecipeQueryPayload.ID, VanillaRecipeQueryPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VanillaRecipeDetailsPayload.ID, VanillaRecipeDetailsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VanillaRecipeDetailsQueryPayload.ID, VanillaRecipeDetailsQueryPayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> awardDefaultRecipes(handler.player, server, false));
        ServerLifecycleEvents.SERVER_STARTED.register(ServerConfigNetworking::refreshRecipeConflicts);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                refreshRecipeConflicts(server);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    awardDefaultRecipes(player, server, true);
                }
                ReiCompat.refreshAfterRecipeReload(server);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // This is the dedicated-server editor only. Local worlds use the pause-menu editor.
            if (environment != net.minecraft.commands.Commands.CommandSelection.DEDICATED) return;
            dispatcher.register(
                    literal("customrecipe")
                            .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                            .executes(context -> openEditor(context.getSource()))
            );
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) {
                player.sendSystemMessage(Component.translatable("customrecipe.chat.permission_denied"));
                return;
            }
            if (payload.json().length() > MAX_JSON_CHARS) {
                player.sendSystemMessage(Component.translatable("customrecipe.chat.server_too_large"));
                return;
            }

            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                player.sendSystemMessage(Component.translatable("customrecipe.chat.invalid_json"));
                return;
            }

            config.editor_world_id = "";
            config.editor_world_name = "";
            WorldRecipeAssignments.migrateLegacyRecipes(config);
            ConfigLoader.saveAndInvalidate(config);
            player.sendSystemMessage(Component.translatable("customrecipe.chat.server_applying"));
            context.server().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), "reload");
        });

        ServerPlayNetworking.registerGlobalReceiver(ValidateServerConfigPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))
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
            if (!context.player().permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) {
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
            if (!context.player().permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) return;
            VanillaRecipeDetails details = findVanillaRecipeDetails(context.server(), payload.recipeId());
            String json = GSON.toJson(details);
            if (json.length() <= MAX_JSON_CHARS) {
                ServerPlayNetworking.send(context.player(), new VanillaRecipeDetailsPayload(json));
            }
        });

    }

    /** Tags are bound only after data-pack reload, so conflict comparison must happen here. */
    private static void refreshRecipeConflicts(net.minecraft.server.MinecraftServer server) {
        ModConfig rootConfig = ConfigLoader.get();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
        boolean changed = false;
        for (CustomRecipeEntry entry : config.custom_recipes) {
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            RecipeHolder<?> customEntry = server.getRecipeManager()
                    .byKey(ResourceKey.create(Registries.RECIPE, entry.serverRecipeId())).orElse(null);
            if (customEntry != null && customEntry.value() instanceof CraftingRecipe custom) {
                for (RecipeHolder<?> candidate : server.getRecipeManager().getRecipes()) {
                    Identifier id = candidate.id().identifier();
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
            addCustomRecipeConflicts(entry, config.custom_recipes, conflicts, sameShape);
            conflicts.sort(String::compareTo);
            sameShape.sort(String::compareTo);
            if (!conflicts.equals(entry.conflicting_recipes) || !sameShape.equals(entry.same_shape_recipes)) {
                entry.conflicting_recipes = conflicts;
                entry.same_shape_recipes = sameShape;
                changed = true;
            }
        }
        if (changed) ConfigLoader.saveIntegrityState(rootConfig);
    }

    /** Validates unsaved OP drafts against the server's items, defaults, and other custom recipes. */
    private static void validateProposedConfig(net.minecraft.server.MinecraftServer server, ModConfig config) {
        WorldRecipeConfig targetConfig = ConfigLoader.activeWorldConfig(config);
        for (CustomRecipeEntry entry : targetConfig.custom_recipes) {
            RecipeIntegrity.refresh(entry);
            List<String> conflicts = new ArrayList<>();
            List<String> sameShape = new ArrayList<>();
            RecipeSignature signature = Boolean.TRUE.equals(entry.corrupted) ? null : signatureOf(entry);
            if (signature != null) {
                for (RecipeHolder<?> candidate : server.getRecipeManager().getRecipes()) {
                    Identifier id = candidate.id().identifier();
                    if (!(candidate.value() instanceof CraftingRecipe wrapped)
                            || (id.getNamespace().equals(CustomRecipeMod.MOD_ID) && id.getPath().startsWith("custom/"))) {
                        continue;
                    }
                    CraftingRecipe existing = unwrap(wrapped);
                    if (!signature.equals(signatureOf(existing))) continue;
                    if (sameOutputItem(entry, existing, server)) conflicts.add(id.toString());
                    else sameShape.add(id.toString());
                }
                addCustomRecipeConflicts(entry, targetConfig.custom_recipes, conflicts, sameShape);
            }
            conflicts.sort(String::compareTo);
            sameShape.sort(String::compareTo);
            entry.conflicting_recipes = conflicts;
            entry.same_shape_recipes = sameShape;
        }
    }

    /**
     * Custom recipes can be disabled and therefore absent from RecipeManager.
     * Compare the saved entries directly so every custom-to-custom collision is visible.
     */
    private static void addCustomRecipeConflicts(CustomRecipeEntry entry, List<CustomRecipeEntry> recipes,
                                                 List<String> conflicts, List<String> sameShape) {
        RecipeSignature signature = signatureOf(entry);
        if (signature == null) return;
        for (CustomRecipeEntry candidate : recipes) {
            if (candidate == null || candidate == entry || sameRecipeId(entry, candidate)
                    || Boolean.TRUE.equals(candidate.corrupted)
                    || !WorldRecipeAssignments.isRecipeActive(candidate)
                    || !signature.equals(signatureOf(candidate))) continue;
            if (sameOutputItem(entry, candidate)) conflicts.add(candidate.serverRecipeId().toString());
            else sameShape.add(candidate.serverRecipeId().toString());
        }
    }

    /** Output count is intentionally ignored: the recipe-book ambiguity remains. */
    private static boolean sameOutputItem(CustomRecipeEntry first, CustomRecipeEntry second) {
        return first.result != null && first.result.equals(second.result);
    }

    private static boolean sameRecipeId(CustomRecipeEntry first, CustomRecipeEntry second) {
        return first.id != null && !first.id.isBlank() && first.id.equals(second.id);
    }

    private static boolean sameOutputItem(CustomRecipeEntry entry, CraftingRecipe candidate,
                                          net.minecraft.server.MinecraftServer server) {
        Identifier resultId = Identifier.tryParse(entry.result);
        if (resultId == null) return false;
        try {
            ItemStack result = candidate.assemble(CraftingInput.EMPTY);
            return !result.isEmpty() && resultId.equals(BuiltInRegistries.ITEM.getKey(result.getItem()));
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
        List<String> ingredients = recipe.placementInfo().ingredients().stream()
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

        List<String> firstIngredients = first.placementInfo().ingredients().stream()
                .map(ServerConfigNetworking::ingredientSignature).sorted().toList();
        List<String> secondIngredients = second.placementInfo().ingredients().stream()
                .map(ServerConfigNetworking::ingredientSignature).sorted().toList();
        return firstIngredients.equals(secondIngredients);
    }

    /** The output count may differ; a different output item is safe for the recipe book. */
    private static boolean sameOutputItem(CraftingRecipe first, CraftingRecipe second,
                                          net.minecraft.server.MinecraftServer server) {
        try {
            ItemStack firstResult = first.assemble(CraftingInput.EMPTY);
            ItemStack secondResult = second.assemble(CraftingInput.EMPTY);
            return !firstResult.isEmpty() && !secondResult.isEmpty()
                    && firstResult.getItem() == secondResult.getItem();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String ingredientSignature(Ingredient ingredient) {
        return ingredient.items()
                .map(item -> BuiltInRegistries.ITEM.getKey(item.value()).toString())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** Quietly adds enabled defaults to the recipe book without recipe toasts. */
    private static void awardDefaultRecipes(ServerPlayer player, net.minecraft.server.MinecraftServer server,
                                            boolean refreshBook) {
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default)
                    || Boolean.TRUE.equals(entry.corrupted)) {
                continue;
            }
            server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, entry.serverRecipeId()))
                    .ifPresent(recipes::add);
        }
        for (String builtinId : config.known_by_default_builtin) {
            if (builtinId == null || config.disabled_builtin.contains(builtinId)) continue;
            Identifier id = Identifier.tryParse(CustomRecipeMod.MOD_ID + ":" + builtinId);
            if (id == null) continue;
            server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id))
                    .ifPresent(recipes::add);
        }

        boolean changed = false;
        var book = player.getRecipeBook();
        for (RecipeHolder<?> recipe : recipes) {
            if (!book.contains(recipe.id())) {
                book.add(recipe.id());
                changed = true;
            }
        }
        // A /reload can also hide a previously visible vanilla recipe variant.
        // Resend it after reload so the client drops stale auto-fill entries.
        if (changed || refreshBook) book.sendInitialRecipeBook(player);
    }

    private static int openEditor(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) {
            source.sendFailure(Component.translatable("customrecipe.chat.client_mod_required"));
            return 0;
        }

        sendEditor(player);
        source.sendSuccess(() -> Component.translatable("customrecipe.chat.opening_editor"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendEditor(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) return;
        // The config can have changed through the local editor since the last reload.
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();
        config.editor_world_id = WorldRecipeAssignments.activeWorldId();
        config.editor_world_name = WorldRecipeAssignments.activeWorldName();
        String json = ConfigLoader.toJson(config);
        config.editor_world_id = "";
        config.editor_world_name = "";
        if (json.length() > MAX_JSON_CHARS) {
            player.sendSystemMessage(Component.translatable("customrecipe.chat.server_send_too_large"));
            return;
        }
        ServerPlayNetworking.send(player, new ServerConfigPayload(json));
    }

    private static VanillaRecipePage findVanillaRecipes(net.minecraft.server.MinecraftServer server, RecipeQuery request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        Set<String> disabledRecipeIds = request.disabledRecipeIds() == null
                ? Set.of() : new HashSet<>(request.disabledRecipeIds());
        String statusFilter = request.statusFilter() == null ? "ALL" : request.statusFilter();
        String sourceFilter = request.sourceFilter() == null ? "ALL" : request.sourceFilter();
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (RecipeHolder<?> entry : server.getRecipeManager().getRecipes()) {
            Identifier recipeId = entry.id().identifier();
            if (!(entry.value() instanceof CraftingRecipe wrappedRecipe)
                    // Recipes owned by Custom Recipe are library templates or generated
                    // custom recipes, never entries in the vanilla/mod recipe browser.
                    || recipeId.getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);

            // Special recipes (for example decorated pots) require a real grid and throw on EMPTY.
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.assemble(CraftingInput.EMPTY);
            } catch (RuntimeException ignored) {
                // Their recipe ID remains searchable and they can still be disabled.
            }
            boolean special = result.isEmpty();
            String resultId = result.isEmpty() ? entry.id().identifier().toString()
                    : BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
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
                for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                }
            }

            boolean outputMatch = request.matchOutput() && resultId.contains(query);
            boolean ingredientMatch = request.matchIngredients() && ingredients.stream().anyMatch(id -> id.contains(query));
            boolean disabled = disabledRecipeIds.contains(recipeId.toString());
            boolean statusMatch = switch (statusFilter) {
                case "ENABLED" -> !disabled && !special;
                case "DISABLED" -> disabled && !special;
                case "SPECIAL" -> special;
                default -> true;
            };
            boolean sourceMatch = switch (sourceFilter) {
                case "MODDED" -> !recipeId.getNamespace().equals("minecraft");
                case "VANILLA" -> recipeId.getNamespace().equals("minecraft");
                default -> true;
            };
            if (statusMatch && sourceMatch && (query.isEmpty() || outputMatch || ingredientMatch)) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(
                        entry.id().identifier().toString(), resultId,
                        toPreviewSlots(ingredients, gridWidth, gridHeight, shapeless),
                        gridWidth, gridHeight, shapeless, special));
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
        return ingredient.items()
                .map(item -> BuiltInRegistries.ITEM.getKey(item.value()).toString())
                .findFirst()
                .orElse("");
    }

    private static VanillaRecipeDetails findVanillaRecipeDetails(net.minecraft.server.MinecraftServer server, String rawId) {
        var identifier = Identifier.tryParse(rawId);
        if (identifier == null) return new VanillaRecipeDetails(rawId, List.of());
        var id = ResourceKey.create(Registries.RECIPE, identifier);
        RecipeHolder<?> entry = server.getRecipeManager().byKey(id).orElse(null);
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
            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
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
        return ingredient.items().map(item -> BuiltInRegistries.ITEM.getKey(item.value()).toString()).sorted().toList();
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

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ServerConfigNetworking() {}
}
