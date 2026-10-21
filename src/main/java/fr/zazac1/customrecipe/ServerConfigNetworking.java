package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.text.Style;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/** Server-side command and permission-checked config synchronization. */
public final class ServerConfigNetworking {
    private static final int MAX_JSON_CHARS = 1_000_000;
    // Chunks are loaded transparently by the client while the list is scrolled.
    private static final int VANILLA_PAGE_SIZE = 40;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ServerConfigPayload.ID, ServerConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveServerConfigPayload.ID, SaveServerConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerConfigSaveResultPayload.ID, ServerConfigSaveResultPayload.CODEC);
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
                ReiCompat.refreshAfterRecipeReload(server);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("customrecipe_item")
                    .requires(source -> !environment.dedicated
                            || source.hasPermissionLevel(2))
                    .then(literal("save")
                            .executes(context -> saveHeldItem(context.getSource(), null, null, false))
                            .then(argument("name", StringArgumentType.greedyString())
                                    .executes(context -> saveHeldItem(context.getSource(), null,
                                            StringArgumentType.getString(context, "name"), false))))
                    .then(literal("save_as")
                            .then(argument("id", StringArgumentType.word())
                                    .executes(context -> saveHeldItem(context.getSource(),
                                            StringArgumentType.getString(context, "id"), null, true))
                                    .then(argument("name", StringArgumentType.greedyString())
                                            .executes(context -> saveHeldItem(context.getSource(),
                                                    StringArgumentType.getString(context, "id"),
                                                    StringArgumentType.getString(context, "name"), true)))))
                    .then(literal("give").then(argument("id", StringArgumentType.word())
                            .executes(context -> giveCustomItem(context.getSource(),
                                    StringArgumentType.getString(context, "id")))))
                    .then(literal("remove").then(argument("id", StringArgumentType.word())
                            .executes(context -> removeCustomItem(context.getSource(),
                                    StringArgumentType.getString(context, "id")))))
                    .then(literal("list").executes(context -> listCustomItems(context.getSource()))));

            // This is the dedicated-server editor only. Local worlds use the pause-menu editor.
            if (!environment.dedicated) return;
            dispatcher.register(
                    literal("customrecipe_server")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> openEditor(context.getSource()))
            );
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.hasPermissionLevel(2)) {
                sendSaveResult(player, false, "permission_denied", "You do not have permission to edit server recipes.");
                return;
            }
            if (payload.json().length() > MAX_JSON_CHARS) {
                sendSaveResult(player, false, "too_large", "The submitted configuration is too large.");
                return;
            }

            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                sendSaveResult(player, false, "invalid_config", "The submitted configuration is invalid.");
                return;
            }

            config.editor_world_id = "";
            config.editor_world_name = "";
            WorldRecipeAssignments.migrateLegacyRecipes(config);
            // The editor may have changed after its initial preview. Recompute with
            // the live data-pack recipes before persisting this server configuration.
            try {
                validateProposedConfig(context.server(), config);
            } catch (RuntimeException error) {
                CustomRecipeMod.LOGGER.error("Could not validate a proposed server configuration", error);
                sendSaveResult(player, false, "validation_failed", "The server could not validate this configuration.");
                return;
            }
            if (!ConfigLoader.saveAndInvalidate(config)) {
                sendSaveResult(player, false, "write_failed", "The server could not write its configuration to disk.");
                return;
            }
            try {
                context.server().getCommandManager().executeWithPrefix(player.getCommandSource().withSilent(), "reload");
            } catch (RuntimeException error) {
                CustomRecipeMod.LOGGER.error("Saved the server configuration, but could not reload recipes", error);
                sendSaveResult(player, false, "reload_failed", "The configuration was saved, but recipes could not be reloaded.");
                return;
            }
            sendSaveResult(player, true, "ok", "Server recipes were saved and the reload was started.");
        });

        ServerPlayNetworking.registerGlobalReceiver(ValidateServerConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.hasPermissionLevel(2)
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
            if (!context.player().hasPermissionLevel(2)) {
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
            if (!context.player().hasPermissionLevel(2)) return;
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
        boolean changed = RecipeConflictAnalyzer.refresh(config.custom_recipes, server.getRecipeManager().values(),
                config.disabled_recipes, server.getRegistryManager());
        if (changed) ConfigLoader.saveIntegrityState(rootConfig);
    }

    /** Validates unsaved OP drafts against the server's items, defaults, and other custom recipes. */
    private static void validateProposedConfig(net.minecraft.server.MinecraftServer server, ModConfig config) {
        WorldRecipeConfig targetConfig = ConfigLoader.activeWorldConfig(config);
        for (CustomRecipeEntry entry : targetConfig.custom_recipes) {
            RecipeIntegrity.refresh(entry, config.custom_items);
        }
        RecipeConflictAnalyzer.refresh(targetConfig.custom_recipes, server.getRecipeManager().values(),
                targetConfig.disabled_recipes, server.getRegistryManager());
    }

    /** Quietly adds enabled defaults to the recipe book without recipe toasts. */
    private static void awardDefaultRecipes(ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                                            boolean refreshBook) {
        List<RecipeEntry<?>> recipes = new ArrayList<>();
        WorldRecipeConfig config = ConfigLoader.activeWorldConfig();
        for (CustomRecipeEntry entry : config.custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default)
                    || Boolean.TRUE.equals(entry.corrupted)) {
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
            source.sendError(Text.translatable("customrecipe.chat.client_mod_required"));
            return 0;
        }

        // The command/chat processing still owns the current client screen in
        // this tick. Send the editor payload on the following server tick.
        source.getServer().execute(() -> sendEditor(player));
        source.sendFeedback(() -> Text.translatable("customrecipe.chat.opening_editor"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int saveHeldItem(ServerCommandSource source, String requestedId, String requestedName,
                                    boolean replaceExisting) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            source.sendError(Text.translatable("customrecipe.chat.custom_item.empty_hand"));
            return 0;
        }

        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();
        String name = requestedName == null || requestedName.isBlank()
                ? held.getName().getString() : requestedName.strip();
        String id = requestedId == null || requestedId.isBlank()
                ? CustomItemLibrary.uniqueId(config.custom_items, name)
                : CustomItemLibrary.sanitizeId(requestedId);
        if (id.isBlank()) {
            source.sendError(Text.translatable("customrecipe.chat.custom_item.invalid_id"));
            return 0;
        }

        CustomItemEntry entry = CustomItemLibrary.find(config.custom_items, id);
        if (entry != null && !replaceExisting) id = CustomItemLibrary.uniqueId(config.custom_items, id);
        if (entry == null || !entry.id.equals(id)) {
            entry = new CustomItemEntry();
            config.custom_items.add(entry);
        }
        entry.id = id;
        entry.name = name;
        entry.item_id = Registries.ITEM.getId(held.getItem()).toString();
        try {
            entry.stack_data = CustomItemLibrary.encode(held, source.getServer().getRegistryManager());
        } catch (RuntimeException error) {
            source.sendError(Text.translatable("customrecipe.chat.custom_item.save_failed"));
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to encode custom item {}", id, error);
            return 0;
        }
        ConfigLoader.saveAndInvalidate(config);
        String savedId = id;
        source.sendFeedback(() -> Text.translatable("customrecipe.chat.custom_item.saved", name, savedId), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int giveCustomItem(ServerCommandSource source, String requestedId) throws CommandSyntaxException {
        CustomItemEntry entry = CustomItemLibrary.find(ConfigLoader.get().custom_items, requestedId);
        ItemStack stack = CustomItemLibrary.decode(entry, source.getServer().getRegistryManager());
        if (entry == null || stack.isEmpty()) {
            source.sendError(Text.translatable("customrecipe.chat.custom_item.not_found", requestedId));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!player.giveItemStack(stack.copy())) player.dropItem(stack.copy(), false);
        source.sendFeedback(() -> Text.translatable("customrecipe.chat.custom_item.given", entry.name), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeCustomItem(ServerCommandSource source, String requestedId) {
        ModConfig config = ConfigLoader.get();
        CustomItemEntry entry = CustomItemLibrary.find(config.custom_items, requestedId);
        if (entry == null) {
            source.sendError(Text.translatable("customrecipe.chat.custom_item.not_found", requestedId));
            return 0;
        }
        config.custom_items.remove(entry);
        ConfigLoader.saveAndInvalidate(config);
        source.sendFeedback(() -> Text.translatable("customrecipe.chat.custom_item.removed", entry.name), false);
        source.getServer().getCommandManager().executeWithPrefix(source.withSilent(), "reload");
        return Command.SINGLE_SUCCESS;
    }

    private static int listCustomItems(ServerCommandSource source) {
        List<CustomItemEntry> items = ConfigLoader.get().custom_items;
        if (items.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("customrecipe.chat.custom_item.list_empty"), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(() -> Text.translatable("customrecipe.chat.custom_item.list", items.size()), false);
        for (CustomItemEntry entry : items) {
            source.sendFeedback(() -> Text.literal("- " + entry.id + " — " + entry.name), false);
        }
        return items.size();
    }

    private static void sendEditor(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2)
                || !ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) return;
        // The config can have changed through the local editor since the last reload.
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();
        config.editor_world_id = WorldRecipeAssignments.activeWorldId();
        config.editor_world_name = WorldRecipeAssignments.activeWorldName();
        String json = ConfigLoader.toJson(config);
        config.editor_world_id = "";
        config.editor_world_name = "";
        if (json.length() > MAX_JSON_CHARS) {
            player.sendMessage(Text.translatable("customrecipe.chat.server_send_too_large"), false);
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

        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            Identifier recipeId = entry.id().getValue();
            if (!(entry.value() instanceof CraftingRecipe wrappedRecipe)
                    // Recipes owned by Custom Recipe are library templates or generated
                    // custom recipes, never entries in the vanilla/mod recipe browser.
                    || recipeId.getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);

            // Special recipes (for example decorated pots) require a real grid and throw on EMPTY.
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            } catch (RuntimeException ignored) {
                // Their recipe ID remains searchable and they can still be disabled.
            }
            boolean special = result.isEmpty();
            String resultId = result.isEmpty() ? entry.id().getValue().toString()
                    : Registries.ITEM.getId(result.getItem()).toString();
            int gridWidth = 0;
            int gridHeight = 0;
            boolean shapeless = !(recipe instanceof ShapedRecipe);
            List<String> ingredients = new ArrayList<>();
            List<Ingredient> ingredientMatchers = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                // getIngredients includes blank cells and preserves the declared pattern.
                gridWidth = shaped.getWidth();
                gridHeight = shaped.getHeight();
                for (var ingredient : shaped.getIngredients()) {
                    Ingredient value = ingredient.orElse(null);
                    ingredients.add(value == null ? "" : firstMatchingId(value));
                    if (value != null) ingredientMatchers.add(value);
                }
            } else {
                // Shapeless recipes deliberately keep the JSON ingredient order.
                for (Ingredient ingredient : recipe.getIngredientPlacement().getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                    ingredientMatchers.add(ingredient);
                }
            }

            boolean outputMatch = request.matchOutput() && itemMatchesQuery(resultId, query);
            boolean ingredientMatch = request.matchIngredients() && ingredientMatchers.stream()
                    .anyMatch(ingredient -> ingredientMatchesQuery(ingredient, query));
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
            if (statusMatch && sourceMatch && (query.isEmpty() || recipeId.toString().toLowerCase(Locale.ROOT).contains(query)
                    || outputMatch || ingredientMatch)) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(
                        entry.id().getValue().toString(), resultId,
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
        return ingredient.getMatchingItems()
                .map(item -> Registries.ITEM.getId(item.value()).toString())
                .findFirst()
                .orElse("");
    }

    private static boolean itemMatchesQuery(String itemId, String query) {
        var item = Registries.ITEM.get(Identifier.tryParse(itemId));
        String name = item == null ? "" : new ItemStack(item).getName().getString().toLowerCase(Locale.ROOT);
        return itemId.toLowerCase(Locale.ROOT).contains(query) || name.contains(query);
    }

    private static boolean ingredientMatchesQuery(Ingredient ingredient, String query) {
        return ingredient.getMatchingItems().anyMatch(entry -> itemMatchesQuery(
                Registries.ITEM.getId(entry.value()).toString(), query));
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

    private static void sendSaveResult(ServerPlayerEntity player, boolean saved, String code, String message) {
        ServerPlayNetworking.send(player, new ServerConfigSaveResultPayload(
                GSON.toJson(new SaveResult(saved, code, message))));
    }

    private record SaveResult(boolean saved, String code, String message) {}

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ServerConfigNetworking() {}
}
