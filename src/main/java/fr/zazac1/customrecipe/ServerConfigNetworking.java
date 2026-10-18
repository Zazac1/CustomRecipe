package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Server-side command and permission-checked config synchronization. */
public final class ServerConfigNetworking {
    private static final int MAX_JSON_CHARS = 1_000_000;
    private static final int VANILLA_PAGE_SIZE = 40;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("customrecipe_item")
                    .requires(source -> !environment.dedicated || source.hasPermissionLevel(2))
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

            if (environment.dedicated) {
                dispatcher.register(literal("customrecipe_server")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> openEditor(context.getSource())));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> awardDefaultRecipes(handler.player, server));
        ServerLifecycleEvents.SERVER_STARTED.register(RecipeConflictChecker::refreshAndSave);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success) return;
            RecipeConflictChecker.refreshAndSave(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) awardDefaultRecipes(player, server);
            ReiCompat.refreshAfterRecipeReload(server);
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (server, player, handler, buffer, sender) -> {
            String json = buffer.readString(MAX_JSON_CHARS);
            server.execute(() -> {
                if (!player.getCommandSource().hasPermissionLevel(2)) {
                    player.sendMessage(Text.translatable("customrecipe.chat.permission_denied"), false);
                    sendSaveResult(player, false, "Permission denied.");
                    return;
                }
                ModConfig config = ConfigLoader.fromJson(json);
                if (config == null) {
                    player.sendMessage(Text.translatable("customrecipe.chat.invalid_json"), false);
                    sendSaveResult(player, false, "The saved configuration is invalid.");
                    return;
                }
                config.editor_world_id = "";
                config.editor_world_name = "";
                WorldRecipeAssignments.migrateLegacyRecipes(config);
                RecipeConflictChecker.validate(server, config);
                if (!ConfigLoader.saveAndInvalidate(config)) {
                    sendSaveResult(player, false, "The server could not write its configuration.");
                    return;
                }
                player.sendMessage(Text.translatable("customrecipe.chat.server_applying"), false);
                sendSaveResult(player, true, "");
                server.getCommandManager().executeWithPrefix(player.getCommandSource(), "reload");
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ValidateServerConfigPayload.ID, (server, player, handler, buffer, sender) -> {
            String json = buffer.readString(MAX_JSON_CHARS);
            server.execute(() -> {
                if (!player.getCommandSource().hasPermissionLevel(2)) return;
                ModConfig config = ConfigLoader.fromJson(json);
                if (config == null) return;
                for (CustomRecipeEntry entry : ConfigLoader.activeWorldConfig(config).custom_recipes) {
                    RecipeIntegrity.refresh(entry, config.custom_items);
                }
                RecipeConflictChecker.validate(server, config);
                String checkedJson = ConfigLoader.toJson(config);
                if (checkedJson.length() <= MAX_JSON_CHARS) {
                    ServerPlayNetworking.send(player, ValidatedServerConfigPayload.ID,
                            PacketByteBufs.create().writeString(checkedJson, MAX_JSON_CHARS));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(VanillaRecipeQueryPayload.ID, (server, player, handler, buffer, sender) -> {
            RecipeQuery query = GSON.fromJson(buffer.readString(MAX_JSON_CHARS), RecipeQuery.class);
            server.execute(() -> {
                if (!player.getCommandSource().hasPermissionLevel(2) || query == null) return;
                String json = GSON.toJson(findVanillaRecipes(server, query));
                if (json.length() <= MAX_JSON_CHARS) {
                    ServerPlayNetworking.send(player, VanillaRecipePagePayload.ID, PacketByteBufs.create().writeString(json));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsQueryPayload.ID, (server, player, handler, buffer, sender) -> {
            String id = buffer.readString();
            server.execute(() -> {
                if (!player.getCommandSource().hasPermissionLevel(2)) return;
                String json = GSON.toJson(findVanillaRecipeDetails(server, id));
                if (json.length() <= MAX_JSON_CHARS) {
                    ServerPlayNetworking.send(player, VanillaRecipeDetailsPayload.ID, PacketByteBufs.create().writeString(json));
                }
            });
        });
    }

    private static void awardDefaultRecipes(ServerPlayerEntity player, net.minecraft.server.MinecraftServer server) {
        List<net.minecraft.recipe.Recipe<?>> recipes = new ArrayList<>();
        WorldRecipeConfig active = ConfigLoader.activeWorldConfig();
        for (CustomRecipeEntry entry : active.custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default) || Boolean.FALSE.equals(entry.enabled)
                    || Boolean.TRUE.equals(entry.corrupted)
                    || (server.isDedicated() && Boolean.FALSE.equals(entry.server_enabled))) continue;
            server.getRecipeManager().get(entry.serverRecipeId()).ifPresent(recipes::add);
        }
        for (String builtinId : active.known_by_default_builtin) {
            if (builtinId == null || active.disabled_builtin.contains(builtinId)) continue;
            Identifier id = Identifier.tryParse(CustomRecipeMod.MOD_ID + ":" + builtinId);
            if (id != null) server.getRecipeManager().get(id).ifPresent(recipes::add);
        }
        boolean changed = false;
        var book = player.getRecipeBook();
        for (net.minecraft.recipe.Recipe<?> recipe : recipes) {
            if (!book.contains(recipe)) {
                book.add(recipe);
                changed = true;
            }
        }
        if (changed) book.sendInitRecipesPacket(player);
    }

    private static int openEditor(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) {
            source.sendError(Text.translatable("customrecipe.chat.client_mod_required"));
            return 0;
        }
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
        String name = requestedName == null || requestedName.isBlank() ? held.getName().getString() : requestedName.strip();
        String id = requestedId == null || requestedId.isBlank()
                ? CustomItemLibrary.uniqueId(config.custom_items, name) : CustomItemLibrary.sanitizeId(requestedId);
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
        for (CustomItemEntry entry : items) source.sendFeedback(() -> Text.literal("- " + entry.id + " — " + entry.name), false);
        return items.size();
    }

    private static void sendEditor(ServerPlayerEntity player) {
        if (!player.getCommandSource().hasPermissionLevel(2)
                || !ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) return;
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
        ServerPlayNetworking.send(player, ServerConfigPayload.ID,
                PacketByteBufs.create().writeString(json, MAX_JSON_CHARS));
    }

    private static void sendSaveResult(ServerPlayerEntity player, boolean saved, String reason) {
        if (!ServerPlayNetworking.canSend(player, ServerConfigSaveResultPayload.ID)) return;
        var payload = PacketByteBufs.create();
        payload.writeBoolean(saved);
        payload.writeString(reason == null ? "" : reason, 512);
        ServerPlayNetworking.send(player, ServerConfigSaveResultPayload.ID, payload);
    }

    private static VanillaRecipePage findVanillaRecipes(net.minecraft.server.MinecraftServer server, RecipeQuery request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        Set<String> disabledRecipeIds = request.disabledRecipeIds() == null ? Set.of() : new HashSet<>(request.disabledRecipeIds());
        String statusFilter = request.statusFilter() == null ? "ALL" : request.statusFilter();
        String sourceFilter = request.sourceFilter() == null ? "ALL" : request.sourceFilter();
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();
        for (net.minecraft.recipe.Recipe<?> entry : server.getRecipeManager().values()) {
            if (!(entry instanceof CraftingRecipe wrappedRecipe)
                    || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);
            boolean special = recipe.isIgnoredInRecipeBook();
            ItemStack result = ItemStack.EMPTY;
            try { result = recipe.getOutput(server.getRegistryManager()); } catch (RuntimeException ignored) {}
            String resultId = result.isEmpty() ? entry.getId().toString() : Registries.ITEM.getId(result.getItem()).toString();
            int gridWidth = 0, gridHeight = 0;
            boolean shapeless = !(recipe instanceof ShapedRecipe);
            List<String> ingredients = new ArrayList<>();
            List<Ingredient> ingredientMatchers = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                gridWidth = shaped.getWidth();
                gridHeight = shaped.getHeight();
                for (Ingredient ingredient : shaped.getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                    ingredientMatchers.add(ingredient);
                }
            } else {
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                    ingredientMatchers.add(ingredient);
                }
            }
            boolean outputMatch = request.matchOutput() && itemMatchesQuery(resultId, query);
            boolean ingredientMatch = request.matchIngredients() && ingredientMatchers.stream()
                    .anyMatch(ingredient -> ingredientMatchesQuery(ingredient, query));
            boolean disabled = disabledRecipeIds.contains(entry.getId().toString());
            boolean statusMatch = switch (statusFilter) {
                case "ENABLED" -> !disabled && !special;
                case "DISABLED" -> disabled && !special;
                case "SPECIAL" -> special;
                default -> true;
            };
            boolean sourceMatch = switch (sourceFilter) {
                case "MODDED" -> !entry.getId().getNamespace().equals("minecraft");
                case "VANILLA" -> entry.getId().getNamespace().equals("minecraft");
                default -> true;
            };
            if (statusMatch && sourceMatch && (query.isEmpty() || entry.getId().toString().toLowerCase(Locale.ROOT).contains(query)
                    || outputMatch || ingredientMatch)) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(entry.getId().toString(), resultId,
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
        return Arrays.stream(ingredient.getMatchingStacks())
                .map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).findFirst().orElse("");
    }

    private static boolean itemMatchesQuery(String itemId, String query) {
        Identifier identifier = Identifier.tryParse(itemId);
        var item = identifier == null ? null : Registries.ITEM.get(identifier);
        String name = item == null ? "" : new ItemStack(item).getName().getString().toLowerCase(Locale.ROOT);
        return itemId.toLowerCase(Locale.ROOT).contains(query) || name.contains(query);
    }

    private static boolean ingredientMatchesQuery(Ingredient ingredient, String query) {
        return Arrays.stream(ingredient.getMatchingStacks()).anyMatch(stack -> itemMatchesQuery(
                Registries.ITEM.getId(stack.getItem()).toString(), query));
    }

    private static VanillaRecipeDetails findVanillaRecipeDetails(net.minecraft.server.MinecraftServer server, String rawId) {
        Identifier identifier = Identifier.tryParse(rawId);
        if (identifier == null) return new VanillaRecipeDetails(rawId, List.of());
        net.minecraft.recipe.Recipe<?> entry = server.getRecipeManager().get(identifier).orElse(null);
        if (entry == null || !(entry instanceof CraftingRecipe wrappedRecipe)) return new VanillaRecipeDetails(rawId, List.of());
        CraftingRecipe recipe = unwrap(wrappedRecipe);
        List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, List.of()));
        int gridWidth = 0, gridHeight = 0;
        if (recipe instanceof ShapedRecipe shaped) {
            gridWidth = shaped.getWidth();
            gridHeight = shaped.getHeight();
            List<Ingredient> ingredients = shaped.getIngredients();
            for (int row = 0; row < gridHeight && row < 3; row++) for (int column = 0; column < gridWidth && column < 3; column++) {
                int source = row * gridWidth + column;
                choices.set(row * 3 + column, source < ingredients.size() ? ingredientChoices(ingredients.get(source)) : List.of());
            }
        } else {
            List<Ingredient> ingredients = recipe.getIngredients();
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
            if (recipe instanceof VariantFilteredCraftingRecipe filtered) recipe = filtered.delegate();
            else if (recipe instanceof DisabledCraftingRecipe disabled) recipe = disabled.delegate();
            else return recipe;
        }
    }

    private static List<String> ingredientChoices(Ingredient ingredient) {
        if (ingredient == null) return List.of();
        return Arrays.stream(ingredient.getMatchingStacks())
                .map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).sorted().toList();
    }

    private static List<String> toPreviewSlots(List<String> ingredients, int gridWidth, int gridHeight, boolean shapeless) {
        List<String> slots = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        if (shapeless) {
            for (int i = 0; i < ingredients.size() && i < 9; i++) slots.set(i, ingredients.get(i));
            return slots;
        }
        for (int row = 0; row < gridHeight && row < 3; row++) for (int column = 0; column < gridWidth && column < 3; column++) {
            int source = row * gridWidth + column;
            slots.set(row * 3 + column, source < ingredients.size() ? ingredients.get(source) : "");
        }
        return slots;
    }

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput,
                               String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page) {}

    private ServerConfigNetworking() {}
}
