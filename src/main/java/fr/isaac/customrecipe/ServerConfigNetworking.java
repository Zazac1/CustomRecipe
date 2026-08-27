package fr.isaac.customrecipe;

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
import java.util.List;
import java.util.Locale;

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
        PayloadTypeRegistry.clientboundPlay().register(VanillaRecipePagePayload.ID, VanillaRecipePagePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VanillaRecipeQueryPayload.ID, VanillaRecipeQueryPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VanillaRecipeDetailsPayload.ID, VanillaRecipeDetailsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VanillaRecipeDetailsQueryPayload.ID, VanillaRecipeDetailsQueryPayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> awardDefaultRecipes(handler.player, server));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) awardDefaultRecipes(player, server);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("customrecipe")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context -> openEditor(context.getSource()))
        ));

        ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) {
                player.sendSystemMessage(Component.literal("[Custom Recipe] Permission denied."));
                return;
            }
            if (payload.json().length() > MAX_JSON_CHARS) {
                player.sendSystemMessage(Component.literal("[Custom Recipe] Server config is too large."));
                return;
            }

            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                player.sendSystemMessage(Component.literal("[Custom Recipe] Invalid JSON; nothing was changed."));
                return;
            }

            ConfigLoader.saveAndInvalidate(config);
            player.sendSystemMessage(Component.literal("[Custom Recipe] Server config saved. Reloading recipes..."));
            context.server().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "reload");
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

    /** Grants only enabled custom recipes explicitly marked as known by default. */
    private static void awardDefaultRecipes(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (CustomRecipeEntry entry : ConfigLoader.get().custom_recipes) {
            if (!Boolean.TRUE.equals(entry.known_by_default)) continue;
            server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, entry.serverRecipeId()))
                    .ifPresent(recipes::add);
        }
        // awardRecipes intentionally highlights entries and shows the vanilla
        // toast. Defaults should quietly exist in the recipe book instead.
        boolean changed = false;
        var book = player.getRecipeBook();
        for (RecipeHolder<?> recipe : recipes) {
            if (!book.contains(recipe.id())) {
                book.add(recipe.id());
                changed = true;
            }
        }
        if (changed) book.sendInitialRecipeBook(player);
    }

    private static int openEditor(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) {
            source.sendFailure(Component.literal("[Custom Recipe] This client needs the Custom Recipe mod to open the editor."));
            return 0;
        }

        sendEditor(player);
        source.sendSuccess(() -> Component.literal("[Custom Recipe] Opening the server recipe editor."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendEditor(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) return;
        // The config can have changed through the local editor since the last reload.
        ConfigLoader.invalidate();
        String json = ConfigLoader.toJson(ConfigLoader.get());
        if (json.length() > MAX_JSON_CHARS) {
            player.sendSystemMessage(Component.literal("[Custom Recipe] The server config is too large to send to the editor."));
            return;
        }
        ServerPlayNetworking.send(player, new ServerConfigPayload(json));
    }

    private static VanillaRecipePage findVanillaRecipes(net.minecraft.server.MinecraftServer server, RecipeQuery request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

        for (RecipeHolder<?> entry : server.getRecipeManager().getRecipes()) {
            if (!entry.id().identifier().getNamespace().equals("minecraft") || !(entry.value() instanceof CraftingRecipe wrappedRecipe)) continue;
            CraftingRecipe recipe = unwrap(wrappedRecipe);

            // Special recipes (for example decorated pots) require a real grid and throw on EMPTY.
            ItemStack result = ItemStack.EMPTY;
            try {
                result = recipe.assemble(CraftingInput.EMPTY);
            } catch (RuntimeException ignored) {
                // Their recipe ID remains searchable and they can still be disabled.
            }
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
            if (query.isEmpty() || outputMatch || ingredientMatch) {
                matches.add(new VanillaRecipePage.VanillaRecipeInfo(
                        entry.id().identifier().toString(), resultId,
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
        return ingredient.items()
                .map(item -> BuiltInRegistries.ITEM.getKey(item.value()).toString())
                .findFirst()
                .orElse("");
    }

    private static VanillaRecipeDetails findVanillaRecipeDetails(net.minecraft.server.MinecraftServer server, String rawId) {
        var identifier = net.minecraft.resources.Identifier.tryParse(rawId);
        if (identifier == null) return new VanillaRecipeDetails(rawId, List.of());
        var id = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, identifier);
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

    private record RecipeQuery(String query, boolean matchIngredients, boolean matchOutput, int page) {}

    private ServerConfigNetworking() {}
}
