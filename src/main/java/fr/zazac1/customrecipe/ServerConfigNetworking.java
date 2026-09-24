package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/** Server-side command and permission-checked config synchronization. */
public final class ServerConfigNetworking {
    // Older versions wrote every recipe in one shared list.  A migration can
    // legitimately make the editor payload much larger than 30 KiB.
    private static final int MAX_JSON_CHARS = 500_000;
    // Chunks are loaded transparently by the client while the list is scrolled.
    private static final int VANILLA_PAGE_SIZE = 40;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("customrecipe")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> openEditor(context.getSource()))
        ));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> awardDefaultRecipes(handler.player, server));
        ServerLifecycleEvents.SERVER_STARTED.register(RecipeConflictChecker::refreshAndSave);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                RecipeConflictChecker.refreshAndSave(server);
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    awardDefaultRecipes(player, server);
                }
                ReiCompat.refreshAfterRecipeReload(server);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (server, player, handler, buffer, sender) -> {
            String json = buffer.readString(MAX_JSON_CHARS);
            server.execute(() -> {
            if (!player.getCommandSource().hasPermissionLevel(2)) {
                player.sendMessage(Text.literal("[Custom Recipe] Permission denied."), false);
                sendSaveResult(player, false, "Permission denied.");
                return;
            }
            ModConfig config = ConfigLoader.fromJson(json);
            if (config == null) {
                player.sendMessage(Text.literal("[Custom Recipe] Invalid JSON; nothing was changed."), false);
                sendSaveResult(player, false, "The saved configuration is invalid.");
                return;
            }

            RecipeConflictChecker.validate(server, config);
            if (!ConfigLoader.saveAndInvalidate(config)) {
                player.sendMessage(Text.literal("[Custom Recipe] Could not write the server config; nothing was applied."), false);
                sendSaveResult(player, false, "The server could not write its configuration.");
                return;
            }
            player.sendMessage(Text.literal("[Custom Recipe] Server config saved. Reloading recipes..."), false);
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
            server.execute(() -> { if (player.getCommandSource().hasPermissionLevel(2) && query != null) { String json = GSON.toJson(findVanillaRecipes(server, query)); if (json.length() <= MAX_JSON_CHARS) ServerPlayNetworking.send(player, VanillaRecipePagePayload.ID, PacketByteBufs.create().writeString(json)); } });
        });

        ServerPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsQueryPayload.ID, (server, player, handler, buffer, sender) -> {
            String id = buffer.readString();
            server.execute(() -> { if (player.getCommandSource().hasPermissionLevel(2)) { String json = GSON.toJson(findVanillaRecipeDetails(server, id)); if (json.length() <= MAX_JSON_CHARS) ServerPlayNetworking.send(player, VanillaRecipeDetailsPayload.ID, PacketByteBufs.create().writeString(json)); } });
        });

    }

    /** Quietly adds enabled defaults to the recipe book without recipe toasts. */
    private static void awardDefaultRecipes(ServerPlayerEntity player, net.minecraft.server.MinecraftServer server) {
        List<net.minecraft.recipe.Recipe<?>> recipes = new ArrayList<>();
        WorldRecipeConfig active = ConfigLoader.activeWorldConfig();
        for (CustomRecipeEntry entry : active.custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default)
                    || Boolean.FALSE.equals(entry.enabled)
                    || Boolean.TRUE.equals(entry.corrupted)
                    || (server.isDedicated() && Boolean.FALSE.equals(entry.server_enabled))) {
                continue;
            }
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
        ModConfig config = ConfigLoader.get();
        config.editor_world_id = WorldRecipeAssignments.activeWorldId();
        config.editor_world_name = WorldRecipeAssignments.activeWorldName();
        String json = ConfigLoader.toJson(config);
        if (json.length() > MAX_JSON_CHARS) {
            player.sendMessage(Text.literal("[Custom Recipe] The server config is too large to send to the editor."), false);
            return;
        }
        ServerPlayNetworking.send(player, ServerConfigPayload.ID,
                PacketByteBufs.create().writeString(json, MAX_JSON_CHARS));
    }

    /** The client keeps the confirmation dialog open until this acknowledgement arrives. */
    private static void sendSaveResult(ServerPlayerEntity player, boolean saved, String reason) {
        if (!ServerPlayNetworking.canSend(player, ServerConfigSaveResultPayload.ID)) return;
        var payload = PacketByteBufs.create();
        payload.writeBoolean(saved);
        payload.writeString(reason == null ? "" : reason, 512);
        ServerPlayNetworking.send(player, ServerConfigSaveResultPayload.ID, payload);
    }

    private static VanillaRecipePage findVanillaRecipes(net.minecraft.server.MinecraftServer server, RecipeQuery request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        Set<String> disabledRecipeIds = request.disabledRecipeIds() == null
                ? Set.of() : new HashSet<>(request.disabledRecipeIds());
        String statusFilter = request.statusFilter() == null ? "ALL" : request.statusFilter();
        String sourceFilter = request.sourceFilter() == null ? "ALL" : request.sourceFilter();
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (net.minecraft.recipe.Recipe<?> entry : server.getRecipeManager().values()) {
            if (!(entry instanceof CraftingRecipe wrappedRecipe)
                    // The namespace also contains bundled library templates;
                    // none of this mod's recipes belongs in Default Recipes.
                    || entry.getId().getNamespace().equals(CustomRecipeMod.MOD_ID)) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);
            // 1.20.1 exposes special crafting recipes through the recipe-book
            // flag instead of the newer isSpecial API.
            boolean special = recipe.isIgnoredInRecipeBook();

            // Special recipes (for example decorated pots) require a real grid and throw on EMPTY.
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.getOutput(server.getRegistryManager());
            } catch (RuntimeException ignored) {
                // Their recipe ID remains searchable and they can still be disabled.
            }
            String resultId = result.isEmpty() ? entry.getId().toString()
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
                    ingredients.add(firstMatchingId(ingredient));
                }
            } else {
                // Shapeless recipes deliberately keep the JSON ingredient order.
                for (Ingredient ingredient : recipe.getIngredients()) {
                    ingredients.add(firstMatchingId(ingredient));
                }
            }

            boolean outputMatch = request.matchOutput() && resultId.contains(query);
            boolean ingredientMatch = request.matchIngredients() && ingredients.stream().anyMatch(id -> id.contains(query));
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
            if (statusMatch && sourceMatch && (query.isEmpty() || outputMatch || ingredientMatch)) {
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
        return Arrays.stream(ingredient.getMatchingStacks())
                .map(stack -> Registries.ITEM.getId(stack.getItem()).toString())
                .findFirst()
                .orElse("");
    }

    private static VanillaRecipeDetails findVanillaRecipeDetails(net.minecraft.server.MinecraftServer server, String rawId) {
        var identifier = net.minecraft.util.Identifier.tryParse(rawId);
        if (identifier == null) return new VanillaRecipeDetails(rawId, List.of());
        net.minecraft.recipe.Recipe<?> entry = server.getRecipeManager().get(identifier).orElse(null);
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
        return Arrays.stream(ingredient.getMatchingStacks())
                .map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).sorted().toList();
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
