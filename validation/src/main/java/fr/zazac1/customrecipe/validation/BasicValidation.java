package fr.zazac1.customrecipe.validation;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import fr.zazac1.customrecipe.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

import static net.minecraft.commands.Commands.literal;

/** Development-only integration checks; never included in a released JAR. */
@EventBusSubscriber(modid = CustomRecipeMod.MOD_ID)
public final class BasicValidation {
    private static final List<Map<String, String>> results = new ArrayList<>();
    private static Path output() { return FMLPaths.GAMEDIR.get().resolve("../../logs/basic").normalize(); }
    private static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("customrecipe.json"); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void check(String name, Check check) {
        String status = "PASS", detail = "";
        try { check.run(); } catch (Throwable error) {
            status = "FAIL"; detail = error.toString();
            CustomRecipeMod.LOGGER.error("[BASIC TEST] " + name, error);
        }
        results.add(Map.of("test", name, "status", status, "details", detail));
        CustomRecipeMod.LOGGER.info("[BASIC TEST] {}: {} {}", status, name, detail);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("cr_basic_tests")
                .requires(source -> source.getEntity() == null)
                .then(literal("prepare").executes(context -> prepare(context.getSource().getServer())))
                .then(literal("run").executes(context -> run(context.getSource().getServer()))));
    }
    private static CustomRecipeEntry recipe(String id, String result, int count, String... ingredients) {
        var entry = new CustomRecipeEntry();
        entry.id = id; entry.result = result; entry.count = count;
        entry.enabled = true; entry.known_by_default = true;
        entry.ingredients = new ArrayList<>(List.of(ingredients));
        return entry;
    }
    private static ItemStack named(Item item, String name, int damage) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (damage > 0) stack.set(DataComponents.DAMAGE, damage);
        return stack;
    }
    private static CustomItemEntry item(MinecraftServer server, String id, ItemStack stack) {
        var entry = new CustomItemEntry(); entry.id = id; entry.name = stack.getHoverName().getString();
        entry.item_id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        entry.stack_data = CustomItemLibrary.encode(stack, server.registryAccess());
        return entry;
    }
    private static int prepare(MinecraftServer server) {
        try {
            Files.createDirectories(output());
            if (!Files.exists(output().resolve("server-config-before.json")))
                Files.copy(configPath(), output().resolve("server-config-before.json"));
            var config = new ModConfig(); config.recipe_target_version = 2;
            var world = config.getOrCreateWorldConfig(WorldRecipeAssignments.activeWorldId(), "Basic validation");
            var shapeless = recipe("basic_shapeless_counted", "minecraft:apple", 2, "minecraft:diamond", "minecraft:stick");
            shapeless.ingredient_counts = new ArrayList<>(List.of(2, 3));
            world.custom_recipes.add(shapeless);
            var shaped = recipe("basic_shaped_counted", "minecraft:redstone", 2);
            shaped.type = "shaped"; shaped.pattern = new ArrayList<>(List.of("AB"));
            shaped.keys.put("A", "minecraft:glowstone_dust"); shaped.keys.put("B", "minecraft:paper");
            shaped.key_counts.put("A", 3); shaped.key_counts.put("B", 2);
            world.custom_recipes.add(shaped);
            config.custom_items.add(item(server, "exact_rod", named(Items.FISHING_ROD, "Validation Rod", 7)));
            config.custom_items.add(item(server, "exact_output", named(Items.STICK, "Validation Prize", 0)));
            config.custom_items.add(item(server, "collision", named(Items.GOLDEN_AXE, "Server Collision", 3)));
            var exact = recipe("basic_exact", CustomItemLibrary.reference("exact_output"), 3,
                    CustomItemLibrary.reference("exact_rod"), "minecraft:diamond");
            exact.ingredient_counts = new ArrayList<>(List.of(1, 2)); world.custom_recipes.add(exact);
            var disabled = recipe("basic_disabled", "minecraft:emerald", 1, "minecraft:nether_star");
            disabled.enabled = false; world.custom_recipes.add(disabled);
            world.custom_recipes.add(recipe("basic_missing", "missing_basic_test:absent", 1, "minecraft:bedrock"));
            world.custom_recipes.add(recipe("basic_vanilla_priority", "minecraft:apple", 1, "minecraft:oak_planks"));
            world.custom_recipes.add(recipe("basic_vanilla_replacement", "minecraft:apple", 1, "minecraft:birch_planks"));
            world.disabled_recipes.add("minecraft:birch_button");
            world.disabled_recipe_variants.add(new RecipeVariantRule("minecraft:chest", "minecraft:oak_planks"));
            config.global_disabled_recipes.add("minecraft:torch"); world.enabled_recipes.add("minecraft:torch");
            var automatic = recipe("basic_all_worlds", "minecraft:emerald", 2, "minecraft:nether_star", "minecraft:diamond");
            automatic.global_auto_add = true; config.global_library.custom_recipes.add(automatic);
            var second = config.getOrCreateWorldConfig("basic-other-world", "Other test world");
            second.custom_recipes.add(recipe("basic_other_only", "minecraft:stone", 1, "minecraft:dragon_egg"));
            require(ConfigLoader.saveAndInvalidate(config), "Cannot save test fixtures");
            var local = new ModConfig(); local.recipe_target_version = 2;
            local.custom_items.add(item(server, "collision", named(Items.FISHING_ROD, "Local Collision", 7)));
            var draft = recipe("basic_local_draft", "minecraft:amethyst_shard", 2,
                    CustomItemLibrary.reference("collision"), "minecraft:diamond");
            local.global_library.custom_recipes.add(draft);
            ConfigLoader.exportTo(local, output().resolve("client-fixture.json"));
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
            CustomRecipeMod.LOGGER.info("[BASIC TEST] Fixtures prepared; wait for reload before run.");
            return 1;
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static RecipeHolder<?> holder(MinecraftServer server, String id) {
        var key = ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.parse("customrecipe:custom/" + id));
        return server.getRecipeManager().byKey(key).orElseThrow();
    }
    private static CraftingInput input(ItemStack... pairs) {
        var list = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < pairs.length; i++) list.set(i, pairs[i]);
        return CraftingInput.of(3, 3, list);
    }
    private static Optional<RecipeHolder<CraftingRecipe>> match(MinecraftServer server, CraftingInput input) {
        return server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server.overworld());
    }
    private static CraftingMenu menu(MinecraftServer server, ServerPlayer player, ItemStack... grid) {
        player.getInventory().clearContent();
        var menu = new CraftingMenu(99, player.getInventory(), ContainerLevelAccess.create(server.overworld(), player.blockPosition()));
        for (int i = 0; i < grid.length; i++) menu.getSlot(i + 1).set(grid[i]);
        menu.slotsChanged(menu.getSlot(1).container);
        return menu;
    }
    private static int amount(ServerPlayer player, Item item) {
        int count = 0;
        for (var stack : player.getInventory().getNonEquipmentItems()) if (stack.is(item)) count += stack.getCount();
        return count;
    }
    private static IPayloadContext context(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> method.getName().equals("player") ? player : null);
    }
    private static int run(MinecraftServer server) {
        results.clear();
        var player = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("5c4d6ae7-d546-42cf-b35d-20a41d69e024"), "BasicTests"));
        check("shapeless: unordered ingredients and output count", () -> {
            var in = input(new ItemStack(Items.STICK, 3), ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 2));
            var result = match(server, in).orElseThrow().value().assemble(in);
            require(result.is(Items.APPLE) && result.getCount() == 2, result.toString());
        });
        check("shapeless: insufficient ingredients rejected", () -> require(match(server,
                input(new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK, 3))).isEmpty(), "Incomplete craft matched"));
        check("shaped: shifted mirrored pattern and counts", () -> {
            var list = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
            list.set(4, new ItemStack(Items.PAPER, 2)); list.set(5, new ItemStack(Items.GLOWSTONE_DUST, 3));
            var in = CraftingInput.of(3, 3, list); var out = match(server, in).orElseThrow().value().assemble(in);
            require(out.is(Items.REDSTONE) && out.getCount() == 2, out.toString());
        });
        check("shaped: incorrect pattern rejected", () -> require(match(server,
                input(new ItemStack(Items.GLOWSTONE_DUST, 3), ItemStack.EMPTY, new ItemStack(Items.PAPER, 2))).isEmpty(), "Wrong pattern matched"));
        check("normal craft: exact consumption and cursor result", () -> {
            var menu = menu(server, player, new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.STICK, 8));
            require(menu.getSlot(0).getItem().is(Items.APPLE), "No result");
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(menu.getCarried().is(Items.APPLE) && menu.getCarried().getCount() == 2, "Wrong cursor result");
            require(menu.getSlot(1).getItem().getCount() == 3 && menu.getSlot(2).getItem().getCount() == 5, "Wrong consumption");
        });
        check("shift craft: repeats without duplication or underconsumption", () -> {
            var menu = menu(server, player, new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.STICK, 9));
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            require(amount(player, Items.APPLE) == 6, "Expected 6 apples, got " + amount(player, Items.APPLE));
            require(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Ingredients remain");
            require(menu.getSlot(0).getItem().isEmpty(), "Stale result remains");
        });
        check("stale output: insufficient counted ingredients cannot be taken", () -> {
            var menu = menu(server, player, new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.STICK, 3));
            var stale = menu.getSlot(0).getItem().copy();
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND)); menu.getSlot(0).set(stale);
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(menu.getCarried().isEmpty(), "Stale result was taken");
        });
        check("components: damaged named ingredient and named output", () -> {
            var rod = named(Items.FISHING_ROD, "Validation Rod", 7);
            var menu = menu(server, player, rod, new ItemStack(Items.DIAMOND, 2));
            var result = menu.getSlot(0).getItem();
            require(result.is(Items.STICK) && result.getCount() == 3 && result.getHoverName().getString().equals("Validation Prize"), result.toString());
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            require(amount(player, Items.STICK) == 3 && menu.getSlot(1).getItem().isEmpty(), "Exact craft failed");
        });
        check("components: plain or differently damaged item rejected", () -> {
            require(match(server, input(new ItemStack(Items.FISHING_ROD), new ItemStack(Items.DIAMOND, 2))).isEmpty(), "Plain rod matched");
            require(match(server, input(named(Items.FISHING_ROD, "Validation Rod", 8), new ItemStack(Items.DIAMOND, 2))).isEmpty(), "Wrong damage matched");
        });
        check("recipe book: exact components and quantities autofill", () -> {
            var menu = menu(server, player);
            player.getInventory().setItem(0, named(Items.FISHING_ROD, "Validation Rod", 7));
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 2));
            menu.handlePlacement(false, false, holder(server, "basic_exact"), server.overworld(), player.getInventory());
            require(menu.getSlot(0).getItem().is(Items.STICK), "Recipe book failed to place exact ingredients");
        });
        check("recipe book: maximum placement and shift crafting counted stacks", () -> {
            var menu = menu(server, player);
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 6));
            player.getInventory().setItem(1, new ItemStack(Items.STICK, 9));
            menu.handlePlacement(true, false, holder(server, "basic_shapeless_counted"), server.overworld(), player.getInventory());
            require(menu.getSlot(0).getItem().is(Items.APPLE), "Autofill failed");
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            require(amount(player, Items.APPLE) == 6, "Wrong Shift-craft amount after book fill");
        });
        check("vanilla priority preserved", () -> {
            var in = input(new ItemStack(Items.OAK_PLANKS));
            require(match(server, in).orElseThrow().value().assemble(in).is(Items.OAK_BUTTON), "Custom recipe displaced vanilla");
        });
        check("disabled vanilla recipe permits intentional custom replacement", () -> {
            var in = input(new ItemStack(Items.BIRCH_PLANKS));
            require(match(server, in).orElseThrow().value().assemble(in).is(Items.APPLE), "Replacement unavailable");
        });
        check("material variant: oak chest blocked, spruce chest allowed", () -> {
            for (Item material : List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS)) {
                var grid = new ArrayList<ItemStack>();
                for (int i = 0; i < 9; i++) grid.add(i == 4 ? ItemStack.EMPTY : new ItemStack(material));
                var found = match(server, CraftingInput.of(3, 3, grid));
                require(material == Items.OAK_PLANKS ? found.isEmpty() : found.isPresent(), "Wrong material filtering for " + material);
            }
        });
        check("world override re-enables globally disabled torch", () -> {
            var grid = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
            grid.set(0, new ItemStack(Items.COAL)); grid.set(3, new ItemStack(Items.STICK));
            require(match(server, CraftingInput.of(3, 3, grid)).orElseThrow().value().assemble(CraftingInput.of(3, 3, grid)).is(Items.TORCH), "World override failed");
        });
        check("disabled and corrupted custom recipes excluded", () -> {
            require(match(server, input(new ItemStack(Items.NETHER_STAR))).isEmpty(), "Disabled recipe craftable");
            var key = ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.parse("customrecipe:custom/basic_missing"));
            require(server.getRecipeManager().byKey(key).isEmpty(), "Corrupted recipe registered");
            require(ConfigLoader.activeWorldConfig().custom_recipes.stream().anyMatch(r -> r.id.equals("basic_missing") && Boolean.TRUE.equals(r.corrupted)), "Corruption not recorded");
        });
        check("world isolation and automatic library copies", () -> {
            require(match(server, input(new ItemStack(Items.DRAGON_EGG))).isEmpty(), "Other world's recipe leaked");
            var config = ConfigLoader.get();
            require(config.world_configs.values().stream().allMatch(w -> w.custom_recipes.stream().anyMatch(r -> "basic_all_worlds".equals(r.global_library_source_id))), "Automatic copies missing");
            ConfigLoader.ensureAutomaticLibraryRecipesForWorld("basic-new-world", "New test world");
            require(ConfigLoader.get().findWorldConfig("basic-new-world").custom_recipes.stream().anyMatch(r -> "basic_all_worlds".equals(r.global_library_source_id)), "New world did not get automatic recipe");
        });
        check("known by default unlocked after join/reload", () -> {
            var real = server.getPlayerList().getPlayers().stream().findFirst().orElseThrow();
            require(real.getRecipeBook().contains(holder(server, "basic_shapeless_counted").id()), "Known recipe not unlocked");
        });
        check("full and library export/import preserve content", () -> {
            var config = ConfigLoader.get(); var full = output().resolve("export-full.json"); var library = output().resolve("export-library.json");
            ConfigLoader.exportTo(config, full); ConfigLoader.exportLibraryTo(config, library);
            var restored = ConfigLoader.importFrom(full);
            require(restored.custom_items.size() == config.custom_items.size(), "Custom items lost");
            require(restored.findWorldConfig(WorldRecipeAssignments.activeWorldId()).custom_recipes.size() == ConfigLoader.activeWorldConfig().custom_recipes.size(), "Recipes lost");
            var imported = ConfigLoader.importLibraryFrom(library); var destination = new ModConfig();
            require(ConfigLoader.importLibraryInto(destination, imported).added() == 1, "Library import failed");
            require(ConfigLoader.importLibraryInto(destination, imported).added() == 0, "Library import duplicated recipes");
        });
        check("custom stack codec preserves components and damage", () -> {
            var original = named(Items.FISHING_ROD, "Codec Check", 17);
            var decoded = CustomItemLibrary.decode(item(server, "codec_check", original), server.registryAccess());
            require(ItemStack.isSameItemSameComponents(original, decoded), "Codec lost components");
        });
        check("server save: non-operator rejected without writing", () -> {
            server.getPlayerList().deop(player.nameAndId());
            byte[] before = Files.readAllBytes(configPath());
            ServerConfigNetworking.handleSave(new SaveServerConfigPayload(ConfigLoader.toJson(ConfigLoader.get())), context(player));
            require(Arrays.equals(before, Files.readAllBytes(configPath())), "Non-operator wrote configuration");
        });
        server.getPlayerList().op(player.nameAndId());
        check("server save: invalid JSON rejected without writing", () -> {
            byte[] before = Files.readAllBytes(configPath());
            ServerConfigNetworking.handleSave(new SaveServerConfigPayload("{invalid"), context(player));
            require(Arrays.equals(before, Files.readAllBytes(configPath())), "Invalid JSON wrote configuration");
        });
        check("server save: oversized config rejected without writing", () -> {
            byte[] before = Files.readAllBytes(configPath());
            ServerConfigNetworking.handleSave(new SaveServerConfigPayload(" ".repeat(1_000_001)), context(player));
            require(Arrays.equals(before, Files.readAllBytes(configPath())), "Oversized configuration was written");
        });
        check("server save: write failure preserves previous file", () -> {
            byte[] before = Files.readAllBytes(configPath());
            Path temporary = configPath().resolveSibling("customrecipe.json.tmp");
            Files.createDirectory(temporary); Files.writeString(temporary.resolve("validation-blocker"), "intentional write failure");
            try {
                ServerConfigNetworking.handleSave(new SaveServerConfigPayload(ConfigLoader.toJson(ConfigLoader.get())), context(player));
                require(Arrays.equals(before, Files.readAllBytes(configPath())), "Write failure changed the live file");
            } finally { Files.deleteIfExists(temporary.resolve("validation-blocker")); Files.deleteIfExists(temporary); }
        });
        server.getPlayerList().deop(player.nameAndId());
        player.getInventory().clearContent();
        try {
            Files.writeString(output().resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (Exception error) { throw new RuntimeException(error); }
        long failures = results.stream().filter(r -> r.get("status").equals("FAIL")).count();
        CustomRecipeMod.LOGGER.info("[BASIC TEST] FINISHED: {} passed, {} failed", results.size() - failures, failures);
        return failures == 0 ? 1 : 0;
    }
}
