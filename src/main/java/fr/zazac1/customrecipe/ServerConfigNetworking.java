package fr.zazac1.customrecipe;

import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.EndDataPackReload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerRecipeBook;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ServerConfigNetworking {
   private static final int MAX_JSON_CHARS = 30000;
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
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> awardDefaultRecipes(handler.player, server, false));
      ServerLifecycleEvents.SERVER_STARTED.register(ServerConfigNetworking::refreshRecipeConflicts);
      ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((EndDataPackReload)(server, resourceManager, success) -> {
         if (success) {
            refreshRecipeConflicts(server);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               awardDefaultRecipes(player, server, true);
            }

            ReiCompat.refreshAfterRecipeReload(server);
         }
      });
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
               if (environment.dedicated) {
                  dispatcher.register(
                     (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("customrecipe_server").requires(source -> source.hasPermissionLevel(2)))
                        .executes(context -> openEditor((ServerCommandSource)context.getSource()))
                  );
               }
            }
         );
      ServerPlayNetworking.registerGlobalReceiver(SaveServerConfigPayload.ID, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.translatable("customrecipe.chat.permission_denied"), false);
         } else if (payload.json().length() > 30000) {
            player.sendMessage(Text.translatable("customrecipe.chat.server_too_large"), false);
         } else {
            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
               player.sendMessage(Text.translatable("customrecipe.chat.invalid_json"), false);
            } else {
               config.editor_world_id = "";
               config.editor_world_name = "";
               WorldRecipeAssignments.migrateLegacyRecipes(config);
               ConfigLoader.saveAndInvalidate(config);
               player.sendMessage(Text.translatable("customrecipe.chat.server_applying"), false);
               context.server().getCommandManager().executeWithPrefix(player.getCommandSource().withSilent(), "reload");
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(ValidateServerConfigPayload.ID, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         if (player.hasPermissionLevel(2) && payload.json().length() <= 30000) {
            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config != null) {
               validateProposedConfig(context.server(), config);
               String json = ConfigLoader.toJson(config);
               if (json.length() <= 30000) {
                  ServerPlayNetworking.send(player, new ValidatedServerConfigPayload(json));
               }
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(
         VanillaRecipeQueryPayload.ID,
         (payload, context) -> {
            if (context.player().hasPermissionLevel(2)) {
               ServerConfigNetworking.RecipeQuery query = (ServerConfigNetworking.RecipeQuery)GSON.fromJson(
                  payload.json(), ServerConfigNetworking.RecipeQuery.class
               );
               if (query != null) {
                  VanillaRecipePage page = findVanillaRecipes(context.server(), query);
                  String json = GSON.toJson(page);
                  if (json.length() <= 30000) {
                     ServerPlayNetworking.send(context.player(), new VanillaRecipePagePayload(json));
                  }
               }
            }
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(VanillaRecipeDetailsQueryPayload.ID, (payload, context) -> {
         if (context.player().hasPermissionLevel(2)) {
            VanillaRecipeDetails details = findVanillaRecipeDetails(context.server(), payload.recipeId());
            String json = GSON.toJson(details);
            if (json.length() <= 30000) {
               ServerPlayNetworking.send(context.player(), new VanillaRecipeDetailsPayload(json));
            }
         }
      });
   }

   private static void refreshRecipeConflicts(MinecraftServer server) {
      ModConfig rootConfig = ConfigLoader.get();
      WorldRecipeConfig config = ConfigLoader.activeWorldConfig(rootConfig);
      boolean changed = false;

      for (CustomRecipeEntry entry : config.custom_recipes) {
         List<String> conflicts = new ArrayList<>();
         List<String> sameShape = new ArrayList<>();
         RecipeEntry<?> customEntry = (RecipeEntry<?>)server.getRecipeManager().get(entry.serverRecipeId()).orElse(null);
         if (customEntry != null && customEntry.value() instanceof CraftingRecipe custom) {
            for (RecipeEntry<?> candidate : server.getRecipeManager().values()) {
               Identifier id = candidate.id();
               if (candidate.value() instanceof CraftingRecipe existing
                  && (!id.getNamespace().equals("customrecipe") || !id.getPath().startsWith("custom/"))
                  && sameExactInputs(custom, existing)) {
                  if (sameOutputItem(custom, existing, server)) {
                     conflicts.add(id.toString());
                  } else {
                     sameShape.add(id.toString());
                  }
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

      if (changed) {
         ConfigLoader.saveIntegrityState(rootConfig);
      }
   }

   private static void validateProposedConfig(MinecraftServer server, ModConfig config) {
      WorldRecipeConfig targetConfig = ConfigLoader.activeWorldConfig(config);

      for (CustomRecipeEntry entry : targetConfig.custom_recipes) {
         RecipeIntegrity.refresh(entry);
         List<String> conflicts = new ArrayList<>();
         List<String> sameShape = new ArrayList<>();
         ServerConfigNetworking.RecipeSignature signature = Boolean.TRUE.equals(entry.corrupted) ? null : signatureOf(entry);
         if (signature != null) {
            for (RecipeEntry<?> candidate : server.getRecipeManager().values()) {
               Identifier id = candidate.id();
               if (candidate.value() instanceof CraftingRecipe wrapped && (!id.getNamespace().equals("customrecipe") || !id.getPath().startsWith("custom/"))) {
                  CraftingRecipe existing = unwrap(wrapped);
                  if (signature.equals(signatureOf(existing))) {
                     if (sameOutputItem(entry, existing, server)) {
                        conflicts.add(id.toString());
                     } else {
                        sameShape.add(id.toString());
                     }
                  }
               }
            }

            addCustomRecipeConflicts(entry, targetConfig.custom_recipes, conflicts, sameShape);
         }

         conflicts.sort(String::compareTo);
         sameShape.sort(String::compareTo);
         entry.conflicting_recipes = conflicts;
         entry.same_shape_recipes = sameShape;
      }
   }

   private static void addCustomRecipeConflicts(CustomRecipeEntry entry, List<CustomRecipeEntry> recipes, List<String> conflicts, List<String> sameShape) {
      ServerConfigNetworking.RecipeSignature signature = signatureOf(entry);
      if (signature != null) {
         for (CustomRecipeEntry candidate : recipes) {
            if (candidate != null
               && candidate != entry
               && !sameRecipeId(entry, candidate)
               && !Boolean.TRUE.equals(candidate.corrupted)
               && WorldRecipeAssignments.isRecipeActive(candidate)
               && signature.equals(signatureOf(candidate))) {
               if (sameOutputItem(entry, candidate)) {
                  conflicts.add(candidate.serverRecipeId().toString());
               } else {
                  sameShape.add(candidate.serverRecipeId().toString());
               }
            }
         }
      }
   }

   private static boolean sameOutputItem(CustomRecipeEntry first, CustomRecipeEntry second) {
      return first.result != null && first.result.equals(second.result);
   }

   private static boolean sameRecipeId(CustomRecipeEntry first, CustomRecipeEntry second) {
      return first.id != null && !first.id.isBlank() && first.id.equals(second.id);
   }

   private static boolean sameOutputItem(CustomRecipeEntry entry, CraftingRecipe candidate, MinecraftServer server) {
      Identifier resultId = Identifier.tryParse(entry.result);
      if (resultId == null) {
         return false;
      } else {
         try {
            ItemStack result = candidate.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            return !result.isEmpty() && resultId.equals(Registries.ITEM.getId(result.getItem()));
         } catch (RuntimeException var5) {
            return false;
         }
      }
   }

   private static ServerConfigNetworking.RecipeSignature signatureOf(CustomRecipeEntry entry) {
      if ("shaped".equalsIgnoreCase(entry.type)) {
         if (entry.pattern != null && !entry.pattern.isEmpty() && entry.keys != null) {
            int width = entry.pattern.stream().mapToInt(String::length).max().orElse(0);
            List<String> slots = new ArrayList<>();

            for (String row : entry.pattern) {
               for (int column = 0; column < width; column++) {
                  char symbol = column < row.length() ? row.charAt(column) : 32;
                  String item = symbol == ' ' ? "" : entry.keys.get(String.valueOf(symbol));
                  if (symbol != ' ' && (item == null || item.isBlank())) {
                     return null;
                  }

                  slots.add(item == null ? "" : item.trim());
               }
            }

            return trimSignature(true, width, entry.pattern.size(), slots);
         } else {
            return null;
         }
      } else if (entry.ingredients != null && !entry.ingredients.isEmpty()) {
         List<String> ingredients = new ArrayList<>();

         for (String item : entry.ingredients) {
            if (item == null || item.isBlank()) {
               return null;
            }

            ingredients.add(item.trim());
         }

         ingredients.sort(String::compareTo);
         return new ServerConfigNetworking.RecipeSignature(false, ingredients.size(), 1, ingredients);
      } else {
         return null;
      }
   }

   private static ServerConfigNetworking.RecipeSignature signatureOf(CraftingRecipe recipe) {
      if (recipe instanceof ShapedRecipe shaped) {
         List<String> slots = new ArrayList<>();

         for (Ingredient ingredient : shaped.getIngredients()) {
            slots.add(ingredient.isEmpty() ? "" : ingredientSignature(ingredient));
         }

         return trimSignature(true, shaped.getWidth(), shaped.getHeight(), slots);
      } else {
         List<String> ingredients = recipe.getIngredients().stream().map(ServerConfigNetworking::ingredientSignature).sorted().toList();
         return new ServerConfigNetworking.RecipeSignature(false, ingredients.size(), 1, ingredients);
      }
   }

   private static ServerConfigNetworking.RecipeSignature trimSignature(boolean shaped, int sourceWidth, int sourceHeight, List<String> slots) {
      int left = sourceWidth;
      int right = -1;
      int top = sourceHeight;
      int bottom = -1;

      for (int row = 0; row < sourceHeight; row++) {
         for (int column = 0; column < sourceWidth; column++) {
            if (!slots.get(row * sourceWidth + column).isBlank()) {
               left = Math.min(left, column);
               right = Math.max(right, column);
               top = Math.min(top, row);
               bottom = Math.max(bottom, row);
            }
         }
      }

      if (right >= left && bottom >= top) {
         List<String> trimmed = new ArrayList<>();

         for (int row = top; row <= bottom; row++) {
            for (int columnx = left; columnx <= right; columnx++) {
               trimmed.add(slots.get(row * sourceWidth + columnx));
            }
         }

         return new ServerConfigNetworking.RecipeSignature(shaped, right - left + 1, bottom - top + 1, trimmed);
      } else {
         return null;
      }
   }

   private static boolean sameExactInputs(CraftingRecipe first, CraftingRecipe second) {
      boolean firstShaped = first instanceof ShapedRecipe;
      boolean secondShaped = second instanceof ShapedRecipe;
      if (firstShaped != secondShaped) {
         return false;
      } else if (firstShaped) {
         ShapedRecipe a = (ShapedRecipe)first;
         ShapedRecipe b = (ShapedRecipe)second;
         if (a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()) {
            List<Ingredient> ingredientsA = a.getIngredients();
            List<Ingredient> ingredientsB = b.getIngredients();
            if (ingredientsA.size() != ingredientsB.size()) {
               return false;
            } else {
               for (int i = 0; i < ingredientsA.size(); i++) {
                  if (!ingredientSignature(ingredientsA.get(i)).equals(ingredientSignature(ingredientsB.get(i)))) {
                     return false;
                  }
               }

               return true;
            }
         } else {
            return false;
         }
      } else {
         List<String> firstIngredients = first.getIngredients().stream().map(ServerConfigNetworking::ingredientSignature).sorted().toList();
         List<String> secondIngredients = second.getIngredients().stream().map(ServerConfigNetworking::ingredientSignature).sorted().toList();
         return firstIngredients.equals(secondIngredients);
      }
   }

   private static boolean sameOutputItem(CraftingRecipe first, CraftingRecipe second, MinecraftServer server) {
      try {
         ItemStack firstResult = first.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
         ItemStack secondResult = second.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
         return !firstResult.isEmpty() && !secondResult.isEmpty() && firstResult.getItem() == secondResult.getItem();
      } catch (RuntimeException var5) {
         return false;
      }
   }

   private static String ingredientSignature(Ingredient ingredient) {
      return Arrays.stream(ingredient.getMatchingStacks())
         .map(stack -> Registries.ITEM.getId(stack.getItem()).toString())
         .sorted()
         .collect(Collectors.joining(","));
   }

   private static void awardDefaultRecipes(ServerPlayerEntity player, MinecraftServer server, boolean refreshBook) {
      List<RecipeEntry<?>> recipes = new ArrayList<>();
      WorldRecipeConfig config = ConfigLoader.activeWorldConfig();

      for (CustomRecipeEntry entry : config.custom_recipes) {
         if (Boolean.TRUE.equals(entry.known_by_default) && !Boolean.TRUE.equals(entry.corrupted)) {
            server.getRecipeManager().get(entry.serverRecipeId()).ifPresent(recipes::add);
         }
      }

      for (String builtinId : config.known_by_default_builtin) {
         if (builtinId != null && !config.disabled_builtin.contains(builtinId)) {
            Identifier id = Identifier.tryParse("customrecipe:" + builtinId);
            if (id != null) {
               server.getRecipeManager().get(id).ifPresent(recipes::add);
            }
         }
      }

      boolean changed = false;
      ServerRecipeBook book = player.getRecipeBook();

      for (RecipeEntry<?> recipe : recipes) {
         if (!book.contains(recipe.id())) {
            book.add(recipe);
            changed = true;
         }
      }

      if (changed || refreshBook) {
         book.sendInitRecipesPacket(player);
      }
   }

   private static int openEditor(ServerCommandSource source) throws CommandSyntaxException {
      ServerPlayerEntity player = source.getPlayerOrThrow();
      if (!ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) {
         source.sendError(Text.translatable("customrecipe.chat.client_mod_required"));
         return 0;
      } else {
         sendEditor(player);
         source.sendFeedback(() -> Text.translatable("customrecipe.chat.opening_editor"), false);
         return 1;
      }
   }

   private static void sendEditor(ServerPlayerEntity player) {
      if (ServerPlayNetworking.canSend(player, ServerConfigPayload.ID)) {
         ConfigLoader.invalidate();
         ModConfig config = ConfigLoader.get();
         config.editor_world_id = WorldRecipeAssignments.activeWorldId();
         config.editor_world_name = WorldRecipeAssignments.activeWorldName();
         String json = ConfigLoader.toJson(config);
         config.editor_world_id = "";
         config.editor_world_name = "";
         if (json.length() > 30000) {
            player.sendMessage(Text.translatable("customrecipe.chat.server_send_too_large"), false);
         } else {
            ServerPlayNetworking.send(player, new ServerConfigPayload(json));
         }
      }
   }

   private static VanillaRecipePage findVanillaRecipes(MinecraftServer server, ServerConfigNetworking.RecipeQuery request) {
      String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
      Set<String> disabledRecipeIds = (Set<String>)(request.disabledRecipeIds() == null ? Set.of() : new HashSet<>(request.disabledRecipeIds()));
      String statusFilter = request.statusFilter() == null ? "ALL" : request.statusFilter();
      String sourceFilter = request.sourceFilter() == null ? "ALL" : request.sourceFilter();
      List<VanillaRecipePage.VanillaRecipeInfo> matches = new ArrayList<>();

      for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
         Identifier recipeId = entry.id();
         if (entry.value() instanceof CraftingRecipe wrappedRecipe && !recipeId.getNamespace().equals("customrecipe")) {
            CraftingRecipe recipe = unwrap(wrappedRecipe);
            ItemStack result = ItemStack.EMPTY;

            try {
               result = recipe.craft(CraftingRecipeInput.EMPTY, server.getRegistryManager());
            } catch (RuntimeException var26) {
            }

            boolean special = result.isEmpty();
            String resultId = result.isEmpty() ? entry.id().toString() : Registries.ITEM.getId(result.getItem()).toString();
            int gridWidth = 0;
            int gridHeight = 0;
            boolean shapeless = !(recipe instanceof ShapedRecipe);
            List<String> ingredients = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
               gridWidth = shaped.getWidth();
               gridHeight = shaped.getHeight();

               for (Ingredient ingredient : shaped.getIngredients()) {
                  ingredients.add(ingredient.isEmpty() ? "" : firstMatchingId(ingredient));
               }
            } else {
               for (Ingredient ingredient : recipe.getIngredients()) {
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
               matches.add(
                  new VanillaRecipePage.VanillaRecipeInfo(
                     entry.id().toString(), resultId, toPreviewSlots(ingredients, gridWidth, gridHeight, shapeless), gridWidth, gridHeight, shapeless, special
                  )
               );
            }
         }
      }

      matches.sort(Comparator.comparing(VanillaRecipePage.VanillaRecipeInfo::id));
      int total = matches.size();
      int page = Math.max(0, request.page());
      int from = Math.min(page * 40, total);
      int to = Math.min(from + 40, total);
      return new VanillaRecipePage(matches.subList(from, to), page, total);
   }

   private static String firstMatchingId(Ingredient ingredient) {
      return Arrays.stream(ingredient.getMatchingStacks()).map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).findFirst().orElse("");
   }

   private static VanillaRecipeDetails findVanillaRecipeDetails(MinecraftServer server, String rawId) {
      Identifier identifier = Identifier.tryParse(rawId);
      if (identifier == null) {
         return new VanillaRecipeDetails(rawId, List.of());
      } else {
         RecipeEntry<?> entry = (RecipeEntry<?>)server.getRecipeManager().get(identifier).orElse(null);
         if (entry != null && entry.value() instanceof CraftingRecipe wrappedRecipe) {
            CraftingRecipe var18 = unwrap(wrappedRecipe);
            List<List<String>> choices = new ArrayList<>(Collections.nCopies(9, List.of()));
            int gridWidth = 0;
            int gridHeight = 0;
            boolean shapeless = !(var18 instanceof ShapedRecipe);
            if (var18 instanceof ShapedRecipe shaped) {
               gridWidth = shaped.getWidth();
               gridHeight = shaped.getHeight();
               List<Ingredient> ingredients = shaped.getIngredients();

               for (int row = 0; row < gridHeight && row < 3; row++) {
                  for (int column = 0; column < gridWidth && column < 3; column++) {
                     int source = row * gridWidth + column;
                     choices.set(row * 3 + column, source < ingredients.size() ? ingredientChoices(ingredients.get(source)) : List.of());
                  }
               }
            } else {
               List<Ingredient> ingredients = var18.getIngredients();

               for (int slot = 0; slot < ingredients.size() && slot < 9; slot++) {
                  choices.set(slot, ingredientChoices(ingredients.get(slot)));
               }
            }

            TreeSet<String> variants = new TreeSet<>();

            for (List<String> choice : choices) {
               if (choice.size() > 1) {
                  variants.addAll(choice);
               }
            }

            List<VanillaRecipeDetails.VariantPreview> previews = new ArrayList<>();

            for (String material : variants.stream().limit(48L).toList()) {
               List<String> slots = new ArrayList<>(9);

               for (List<String> choicex : choices) {
                  slots.add(choicex.contains(material) ? material : (choicex.isEmpty() ? "" : choicex.getFirst()));
               }

               previews.add(new VanillaRecipeDetails.VariantPreview(material, slots));
            }

            return new VanillaRecipeDetails(rawId, previews);
         } else {
            return new VanillaRecipeDetails(rawId, List.of());
         }
      }
   }

   private static CraftingRecipe unwrap(CraftingRecipe recipe) {
      while (true) {
         if (recipe instanceof VariantFilteredCraftingRecipe filtered) {
            recipe = filtered.delegate();
         } else {
            if (!(recipe instanceof DisabledCraftingRecipe disabled)) {
               return recipe;
            }

            recipe = disabled.delegate();
         }
      }
   }

   private static List<String> ingredientChoices(Ingredient ingredient) {
      return ingredient == null
         ? List.of()
         : Arrays.stream(ingredient.getMatchingStacks()).map(stack -> Registries.ITEM.getId(stack.getItem()).toString()).sorted().toList();
   }

   private static List<String> toPreviewSlots(List<String> ingredients, int gridWidth, int gridHeight, boolean shapeless) {
      List<String> slots = new ArrayList<>(Collections.nCopies(9, ""));
      if (shapeless) {
         for (int i = 0; i < ingredients.size() && i < 9; i++) {
            slots.set(i, ingredients.get(i));
         }

         return slots;
      } else {
         for (int row = 0; row < gridHeight && row < 3; row++) {
            for (int column = 0; column < gridWidth && column < 3; column++) {
               int source = row * gridWidth + column;
               slots.set(row * 3 + column, source < ingredients.size() ? ingredients.get(source) : "");
            }
         }

         return slots;
      }
   }

   private ServerConfigNetworking() {
   }

   private record RecipeQuery(
      String query, boolean matchIngredients, boolean matchOutput, String statusFilter, String sourceFilter, List<String> disabledRecipeIds, int page
   ) {
   }

   private record RecipeSignature(boolean shaped, int width, int height, List<String> ingredients) {
   }
}

