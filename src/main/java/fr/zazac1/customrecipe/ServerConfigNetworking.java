package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Server-side command and permission-checked config synchronization. */
public final class ServerConfigNetworking {
    // Older versions wrote every recipe in one shared list.  A migration can
    // legitimately make the editor payload much larger than 30 KiB.
    private static final int MAX_JSON_CHARS = 1_000_000;
    // Chunks are loaded transparently by the client while the list is scrolled.
    private static final int VANILLA_PAGE_SIZE = 40;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        MinecraftForge.EVENT_BUS.addListener(ServerConfigNetworking::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(ServerConfigNetworking::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> RecipeConflictChecker.refreshAndSave(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener(ServerConfigNetworking::onDatapackSync);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("customrecipe_item")
                .requires(source -> source.hasPermission(2))
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

        // Keep the server editor out of integrated-server suggestions entirely.
        // The local editor is registered by ClientInit as /customrecipe_solo.
        if (event.getEnvironment() != net.minecraft.commands.Commands.CommandSelection.DEDICATED) return;
        event.getDispatcher().register(literal("customrecipe_server").requires(source -> source.hasPermission(2))
                .executes(context -> openEditor(context.getSource())));
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) awardDefaultRecipes(player, player.server);
    }

    /**
     * Forge synchronizes all datapacks after a successful /reload with a null
     * player.  This is the equivalent of Fabric's END_DATA_PACK_RELOAD hook.
     * Per-player syncs are handled by the login hook above and must not reload
     * REI or recalculate every recipe conflict.
     */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        RecipeConflictChecker.refreshAndSave(server);
        for (ServerPlayer player : event.getPlayerList().getPlayers()) awardDefaultRecipes(player, server);
        ReiCompat.refreshAfterRecipeReload(server);
    }

    static void handleSave(ServerPlayer player, String json) {
        if (player == null) return;
        var server = player.server;
        if (!player.hasPermissions(2)) {
            sendSaveResult(player, false, "You do not have permission to edit server recipes.");
            return;
        }
        if (json.length() > MAX_JSON_CHARS) {
            sendSaveResult(player, false, "The submitted configuration is too large.");
            return;
        }
        ModConfig config = ConfigLoader.fromJson(json);
        if (config == null) {
            sendSaveResult(player, false, "The submitted configuration is invalid.");
            return;
        }
        config.editor_world_id = "";
        config.editor_world_name = "";
        WorldRecipeAssignments.migrateLegacyRecipes(config);
        for (CustomRecipeEntry entry : ConfigLoader.activeWorldConfig(config).custom_recipes) {
            RecipeIntegrity.refresh(entry, config.custom_items);
        }
        try {
            RecipeConflictChecker.validate(server, config);
        } catch (RuntimeException error) {
            CustomRecipeMod.LOGGER.error("Could not validate a proposed server configuration", error);
            sendSaveResult(player, false, "The server could not validate this configuration.");
            return;
        }
        if (!ConfigLoader.saveAndInvalidate(config)) {
            sendSaveResult(player, false, "The server could not write its configuration to disk.");
            return;
        }
        try {
            server.getCommands().performCommand(player.createCommandSourceStack().withSuppressedOutput(), "reload");
        } catch (RuntimeException error) {
            CustomRecipeMod.LOGGER.error("Saved the server configuration, but could not reload recipes", error);
            sendSaveResult(player, false, "The configuration was saved, but recipes could not be reloaded.");
            return;
        }
        sendSaveResult(player, true, "Server recipes were saved and the reload was started.");
    }

    static void handleValidate(ServerPlayer player, String json) {
        if (player == null || !player.hasPermissions(2)) return;
        ModConfig config = ConfigLoader.fromJson(json);
        if (config == null) return;
        for (CustomRecipeEntry entry : ConfigLoader.activeWorldConfig(config).custom_recipes) {
            RecipeIntegrity.refresh(entry, config.custom_items);
        }
        RecipeConflictChecker.validate(player.server, config);
        String checked = ConfigLoader.toJson(config);
        if (checked.length() <= MAX_JSON_CHARS) ModNetworking.sendValidatedConfig(player, checked);
    }

    static void handleVanillaQuery(ServerPlayer player, String json) {
        if (player == null || !player.hasPermissions(2)) return;
        RecipeQuery query = GSON.fromJson(json, RecipeQuery.class);
        if (query == null) return;
        String response = GSON.toJson(findVanillaRecipes(player.server, query));
        if (response.length() <= MAX_JSON_CHARS) ModNetworking.sendVanillaPage(player, response);
    }

    static void handleVanillaDetailsQuery(ServerPlayer player, String id) {
        if (player == null || !player.hasPermissions(2)) return;
        String response = GSON.toJson(findVanillaRecipeDetails(player.server, id));
        if (response.length() <= MAX_JSON_CHARS) ModNetworking.sendVanillaDetails(player, response);
    }

    /** Quietly adds enabled defaults to the recipe book without recipe toasts. */
    private static void awardDefaultRecipes(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        List<net.minecraft.world.item.crafting.Recipe<?>> recipes = new ArrayList<>();
        WorldRecipeConfig active = ConfigLoader.activeWorldConfig();
        for (CustomRecipeEntry entry : active.custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default)
                    || Boolean.FALSE.equals(entry.enabled)
                    || Boolean.TRUE.equals(entry.corrupted)
                    || (server.isDedicatedServer() && Boolean.FALSE.equals(entry.server_enabled))) {
                continue;
            }
            server.getRecipeManager().byKey(entry.serverRecipeId()).ifPresent(recipes::add);
        }

        for (String builtinId : active.known_by_default_builtin) {
            if (builtinId == null || active.disabled_builtin.contains(builtinId)) continue;
            ResourceLocation id = ResourceLocation.tryParse(CustomRecipeMod.MOD_ID + ":" + builtinId);
            if (id != null) server.getRecipeManager().byKey(id).ifPresent(recipes::add);
        }

        var book = player.getRecipeBook();
        book.addRecipes(recipes, player);
    }

    private static int openEditor(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ModNetworking.canSend(player)) {
            source.sendFailure(fr.zazac1.customrecipe.Texts.literal("[Custom Recipe] This client needs the Custom Recipe mod to open the editor."));
            return 0;
        }
        sendEditor(player);
        source.sendSuccess(fr.zazac1.customrecipe.Texts.literal("[Custom Recipe] Opening the server recipe editor."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int saveHeldItem(CommandSourceStack source, String requestedId, String requestedName,
                                    boolean replaceExisting) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.empty_hand"));
            return 0;
        }
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();
        String name = requestedName == null || requestedName.isBlank()
                ? held.getHoverName().getString() : requestedName.strip();
        String id = requestedId == null || requestedId.isBlank()
                ? CustomItemLibrary.uniqueId(config.custom_items, name)
                : CustomItemLibrary.sanitizeId(requestedId);
        if (id.isBlank()) {
            source.sendFailure(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.invalid_id"));
            return 0;
        }
        CustomItemEntry entry = CustomItemLibrary.find(config.custom_items, id);
        if (entry != null && !replaceExisting) id = CustomItemLibrary.uniqueId(config.custom_items, id);
        if (entry == null || !id.equals(entry.id)) {
            entry = new CustomItemEntry();
            config.custom_items.add(entry);
        }
        entry.id = id;
        entry.name = name;
        entry.item_id = Registry.ITEM.getKey(held.getItem()).toString();
        try {
            entry.stack_data = CustomItemLibrary.encode(held, source.getServer().registryAccess());
        } catch (RuntimeException error) {
            source.sendFailure(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.save_failed"));
            CustomRecipeMod.LOGGER.error("[CustomRecipe] Failed to encode custom item {}", id, error);
            return 0;
        }
        ConfigLoader.saveAndInvalidate(config);
        String savedId = id;
        source.sendSuccess(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.saved", name, savedId), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int giveCustomItem(CommandSourceStack source, String requestedId) throws CommandSyntaxException {
        CustomItemEntry entry = CustomItemLibrary.find(ConfigLoader.get().custom_items, requestedId);
        ItemStack stack = CustomItemLibrary.decode(entry, source.getServer().registryAccess());
        if (entry == null || stack.isEmpty()) {
            source.sendFailure(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.not_found", requestedId));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (!player.addItem(stack.copy())) player.drop(stack.copy(), false);
        source.sendSuccess(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.given", entry.name), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeCustomItem(CommandSourceStack source, String requestedId) {
        ModConfig config = ConfigLoader.get();
        CustomItemEntry entry = CustomItemLibrary.find(config.custom_items, requestedId);
        if (entry == null) {
            source.sendFailure(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.not_found", requestedId));
            return 0;
        }
        config.custom_items.remove(entry);
        ConfigLoader.saveAndInvalidate(config);
        source.sendSuccess(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.removed", entry.name), false);
        source.getServer().getCommands().performCommand(source.withSuppressedOutput(), "reload");
        return Command.SINGLE_SUCCESS;
    }

    private static int listCustomItems(CommandSourceStack source) {
        List<CustomItemEntry> items = ConfigLoader.get().custom_items;
        if (items.isEmpty()) {
            source.sendSuccess(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.list_empty"), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(fr.zazac1.customrecipe.Texts.translatable("customrecipe.chat.custom_item.list", items.size()), false);
        for (CustomItemEntry entry : items) {
            source.sendSuccess(fr.zazac1.customrecipe.Texts.literal("- " + entry.id + " — " + entry.name), false);
        }
        return items.size();
    }

    private static void sendEditor(ServerPlayer player) {
        if (!player.hasPermissions(2) || !ModNetworking.canSend(player)) return;
        // The config can have changed through the local editor since the last reload.
        ConfigLoader.invalidate();
        ModConfig config = ConfigLoader.get();
        config.editor_world_id = WorldRecipeAssignments.activeWorldId();
        config.editor_world_name = WorldRecipeAssignments.activeWorldName();
        String json = ConfigLoader.toJson(config);
        if (json.length() > MAX_JSON_CHARS) {
            fr.zazac1.customrecipe.Texts.send(player, fr.zazac1.customrecipe.Texts.literal("[Custom Recipe] The server config is too large to send to the editor."));
            return;
        }
        ModNetworking.sendServerConfig(player, json);
    }

    /** The client keeps the confirmation dialog open until this acknowledgement arrives. */
    private static void sendSaveResult(ServerPlayer player, boolean saved, String reason) {
        ModNetworking.sendSaveResult(player, saved, reason);
    }

    private static VanillaRecipePage findVanillaRecipes(net.minecraft.server.MinecraftServer server, RecipeQuery request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        Set<String> disabledRecipeIds = request.disabledRecipeIds() == null
                ? Set.of() : new HashSet<>(request.disabledRecipeIds());
        String statusFilter = request.statusFilter() == null ? "ALL" : request.statusFilter();
        String sourceFilter = request.sourceFilter() == null ? "ALL" : request.sourceFilter();
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (net.minecraft.world.item.crafting.Recipe<?> entry : server.getRecipeManager().getRecipes()) {
            if (!(entry instanceof CraftingRecipe wrappedRecipe)
                    // The namespace also contains bundled library templates;
                    // none of this mod's recipes belongs in Default Recipes.
                    || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);
            // 1.20.1 exposes special crafting recipes through the recipe-book
            // flag instead of the newer isSpecial API.
            boolean special = recipe.isSpecial();

            // Special recipes (for example decorated pots) require a real grid and throw on EMPTY.
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.getResultItem();
            } catch (RuntimeException ignored) {
                // Their recipe ID remains searchable and they can still be disabled.
            }
            String resultId = result.isEmpty() ? entry.getId().toString()
                    : Registry.ITEM.getKey(result.getItem()).toString();
            int gridWidth = 0;
            int gridHeight = 0;
            boolean shapeless = !(recipe instanceof ShapedRecipe);
            List<String> ingredients = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                // getIngredients includes blank cells and preserves the declared pattern.
                gridWidth = shaped.getWidth();
                gridHeight = shaped.getHeight();
                for (var ingredient : shaped.getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                }
            } else {
                // Shapeless recipes deliberately keep the JSON ingredient order.
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                }
            }

            boolean outputMatch = request.matchOutput() && itemMatchesQuery(resultId, query);
            boolean ingredientMatch = request.matchIngredients() && recipe.getIngredients().stream()
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
            if (statusMatch && sourceMatch && (query.isEmpty()
                    || entry.getId().toString().toLowerCase(Locale.ROOT).contains(query) || outputMatch || ingredientMatch)) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(
                        entry.getId().toString(), resultId,
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
        return Arrays.stream(ingredient.getItems())
                .map(stack -> Registry.ITEM.getKey(stack.getItem()).toString())
                .findFirst()
                .orElse("");
    }

    private static boolean itemMatchesQuery(String itemId, String query) {
        if (itemId.toLowerCase(Locale.ROOT).contains(query)) return true;
        var item = Registry.ITEM.get(ResourceLocation.tryParse(itemId));
        return item != null && !item.equals(net.minecraft.world.item.Items.AIR)
                && new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean ingredientMatchesQuery(Ingredient ingredient, String query) {
        for (ItemStack stack : ingredient.getItems()) {
            if (itemMatchesQuery(Registry.ITEM.getKey(stack.getItem()).toString(), query)) return true;
        }
        return false;
    }

    private static VanillaRecipeDetails findVanillaRecipeDetails(net.minecraft.server.MinecraftServer server, String rawId) {
        var identifier = net.minecraft.resources.ResourceLocation.tryParse(rawId);
        if (identifier == null) return new VanillaRecipeDetails(rawId, List.of());
        net.minecraft.world.item.crafting.Recipe<?> entry = server.getRecipeManager().byKey(identifier).orElse(null);
        if (entry == null || !(entry instanceof CraftingRecipe wrappedRecipe)) return new VanillaRecipeDetails(rawId, List.of());
        CraftingRecipe recipe = unwrap(wrappedRecipe);
        List<List<String>> choices = new ArrayList<>(java.util.Collections.nCopies(9, List.of()));
        int gridWidth = 0;
        int gridHeight = 0;
        boolean shapeless = !(recipe instanceof ShapedRecipe);
        if (recipe instanceof ShapedRecipe shaped) {
            gridWidth = shaped.getWidth();
            gridHeight = shaped.getHeight();
            List<Ingredient> ingredients = shaped.getIngredients();
            for (int row = 0; row < gridHeight && row < 3; row++) {
                for (int column = 0; column < gridWidth && column < 3; column++) {
                    int source = row * gridWidth + column;
                    choices.set(row * 3 + column, source < ingredients.size()
                            ? ingredientChoices(ingredients.get(source)) : List.of());
                }
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
            for (List<String> choice : choices) slots.add(choice.contains(material) ? material : (choice.isEmpty() ? "" : choice.get(0)));
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
        return Arrays.stream(ingredient.getItems())
                .map(stack -> Registry.ITEM.getKey(stack.getItem()).toString()).sorted().toList();
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

    /** The bundled Gson must populate ordinary fields, not final record components. */
    private static final class RecipeQuery {
        private String query;
        private boolean matchIngredients;
        private boolean matchOutput;
        private String statusFilter;
        private String sourceFilter;
        private List<String> disabledRecipeIds;
        private int page;

        public String query() { return query; }
        public boolean matchIngredients() { return matchIngredients; }
        public boolean matchOutput() { return matchOutput; }
        public String statusFilter() { return statusFilter; }
        public String sourceFilter() { return sourceFilter; }
        public List<String> disabledRecipeIds() { return disabledRecipeIds; }
        public int page() { return page; }
    }

    private ServerConfigNetworking() {}
}
