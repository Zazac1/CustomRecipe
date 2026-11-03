package fr.zazac1.customrecipe.validation;

import com.google.gson.GsonBuilder;
import fr.zazac1.customrecipe.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.server.command.CommandManager.literal;

/** Development-only integration checks; excluded from every released JAR. */
public final class BasicValidation {
    private static final List<Map<String, String>> results = new ArrayList<>();

    private static Path output() {
        return FabricLoader.getInstance().getGameDir().resolve("../../logs/basic").normalize();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface Check { void run() throws Exception; }

    private static void check(String name, Check check) {
        String status = "PASS", detail = "";
        try {
            check.run();
        } catch (Throwable error) {
            status = "FAIL";
            detail = error.toString();
            CustomRecipeMod.LOGGER.error("[BASIC TEST] " + name, error);
        }
        results.add(Map.of("test", name, "status", status, "details", detail));
        CustomRecipeMod.LOGGER.info("[BASIC TEST] {}: {} {}", status, name, detail);
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("cr_basic_tests")
                        .requires(source -> source.getEntity() == null)
                        .then(literal("prepare").executes(context -> prepare(context.getSource().getServer())))
                        .then(literal("run").executes(context -> run(context.getSource().getServer())))));
    }

    private static CustomRecipeEntry recipe(String id, String result, int count, String... ingredients) {
        var entry = new CustomRecipeEntry();
        entry.id = id;
        entry.result = result;
        entry.count = count;
        entry.enabled = true;
        entry.known_by_default = true;
        entry.ingredients = new ArrayList<>(List.of(ingredients));
        return entry;
    }

    private static int prepare(MinecraftServer server) {
        try {
            Files.createDirectories(output());
            var config = new ModConfig();
            config.recipe_target_version = 2;
            var world = config.getOrCreateWorldConfig(WorldRecipeAssignments.activeWorldId(), "Fabric validation");

            var shapeless = recipe("basic_shapeless_counted", "minecraft:apple", 2,
                    "minecraft:diamond", "minecraft:stick");
            shapeless.ingredient_counts = new ArrayList<>(List.of(2, 3));
            world.custom_recipes.add(shapeless);

            var alternatives = recipe("basic_alternatives", "minecraft:cookie", 3,
                    "minecraft:stone", "minecraft:feather");
            alternatives.ingredient_alternatives.add(new ArrayList<>(List.of(
                    "minecraft:cobblestone", "minecraft:andesite")));
            alternatives.ingredient_alternatives.add(new ArrayList<>());
            alternatives.ingredient_counts = new ArrayList<>(List.of(2, 1));
            world.custom_recipes.add(alternatives);

            var distinctAlternatives = recipe("basic_distinct_alternatives", "minecraft:amethyst_shard", 1,
                    "minecraft:oak_sapling", "minecraft:oak_sapling",
                    "minecraft:oak_sapling", "minecraft:oak_sapling");
            for (int i = 0; i < 4; i++) {
                distinctAlternatives.ingredient_alternatives.add(new ArrayList<>(List.of(
                        "minecraft:spruce_sapling", "minecraft:birch_sapling", "minecraft:jungle_sapling")));
                distinctAlternatives.ingredient_distinct.add(true);
            }
            world.custom_recipes.add(distinctAlternatives);

            var shaped = recipe("basic_shaped_counted", "minecraft:redstone", 2);
            shaped.type = "shaped";
            shaped.pattern = new ArrayList<>(List.of("AB"));
            shaped.keys.put("A", "minecraft:glowstone_dust");
            shaped.keys.put("B", "minecraft:paper");
            shaped.key_counts.put("A", 3);
            shaped.key_counts.put("B", 2);
            world.custom_recipes.add(shaped);

            var shapedDistinct = recipe("basic_shaped_distinct", "minecraft:prismarine_shard", 1);
            shapedDistinct.type = "shaped";
            shapedDistinct.pattern = new ArrayList<>(List.of("AA", "AA"));
            shapedDistinct.keys.put("A", "minecraft:poppy");
            shapedDistinct.key_alternatives.put("A", new ArrayList<>(List.of(
                    "minecraft:dandelion", "minecraft:blue_orchid", "minecraft:allium")));
            shapedDistinct.key_distinct.put("A", true);
            world.custom_recipes.add(shapedDistinct);

            var disabled = recipe("basic_disabled", "minecraft:emerald", 1, "minecraft:nether_star");
            disabled.enabled = false;
            world.custom_recipes.add(disabled);
            world.custom_recipes.add(recipe("basic_missing", "missing_basic_test:absent", 1, "minecraft:bedrock"));

            var automatic = recipe("basic_all_worlds", "minecraft:emerald", 2,
                    "minecraft:nether_star", "minecraft:diamond");
            automatic.global_auto_add = true;
            config.global_library.custom_recipes.add(automatic);
            config.getOrCreateWorldConfig("basic-other-world", "Other test world")
                    .custom_recipes.add(recipe("basic_other_only", "minecraft:stone", 1, "minecraft:dragon_egg"));

            require(ConfigLoader.saveAndInvalidate(config), "Cannot save test fixtures");
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), "reload");
            CustomRecipeMod.LOGGER.info("[BASIC TEST] Fixtures prepared; wait for reload before run.");
            return 1;
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static CraftingRecipeInput input(ItemStack... stacks) {
        var grid = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < stacks.length; i++) grid.set(i, stacks[i]);
        return CraftingRecipeInput.createPositioned(3, 3, grid).input();
    }

    private static Optional<RecipeEntry<CraftingRecipe>> match(MinecraftServer server, CraftingRecipeInput input) {
        return server.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, server.getOverworld());
    }

    private static ItemStack craft(MinecraftServer server, CraftingRecipeInput input) {
        return match(server, input).orElseThrow().value().craft(input, server.getRegistryManager());
    }

    private static boolean recipeMatches(MinecraftServer server, String id, CraftingRecipeInput input) {
        var key = recipe(id, "", 1).serverRecipeId();
        Object value = server.getRecipeManager().get(key).orElseThrow().value();
        require(value instanceof CraftingRecipe, "Recipe is not a crafting recipe: " + key);
        return ((CraftingRecipe) value).matches(input, server.getOverworld());
    }

    private static int run(MinecraftServer server) {
        results.clear();
        check("Quick Add templates stay unregistered until copied", () -> {
            for (int i = 0; i < BuiltinRecipeTemplates.count(); i++) {
                var first = BuiltinRecipeTemplates.create(i);
                var second = BuiltinRecipeTemplates.create(i);
                require(!first.id.equals(second.id), "Template copies share an ID");
                require(server.getRecipeManager().get(first.serverRecipeId()).isEmpty(),
                        "Template registered without adding it");
            }
        });
        check("shapeless: unordered ingredients and output count", () -> {
            var in = input(new ItemStack(Items.STICK, 3), ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 2));
            var result = craft(server, in);
            require(result.isOf(Items.APPLE) && result.getCount() == 2, result.toString());
        });
        check("shapeless: insufficient ingredients rejected", () -> require(match(server,
                input(new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK, 3))).isEmpty(),
                "Incomplete craft matched"));
        check("alternatives: primary and selected items craft with the required count", () -> {
            for (Item item : List.of(Items.STONE, Items.COBBLESTONE, Items.ANDESITE)) {
                var in = input(new ItemStack(item, 2), new ItemStack(Items.FEATHER));
                var result = craft(server, in);
                require(result.isOf(Items.COOKIE) && result.getCount() == 3,
                        "Alternative failed: " + item);
            }
            require(match(server, input(new ItemStack(Items.GRANITE, 2), new ItemStack(Items.FEATHER))).isEmpty(),
                    "Unselected item matched");
            require(match(server, input(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.FEATHER))).isEmpty(),
                    "Alternative ignored required count");
        });
        check("alternatives: repeated groups can require different items", () -> {
            var valid = input(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.SPRUCE_SAPLING),
                    new ItemStack(Items.BIRCH_SAPLING), new ItemStack(Items.JUNGLE_SAPLING));
            var result = craft(server, valid);
            require(result.isOf(Items.AMETHYST_SHARD), "Four different alternatives did not craft");
            require(!recipeMatches(server, "basic_distinct_alternatives",
                    input(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.OAK_SAPLING),
                            new ItemStack(Items.BIRCH_SAPLING), new ItemStack(Items.JUNGLE_SAPLING))),
                    "A duplicated alternative bypassed the distinct rule");
        });
        check("shaped: shifted mirrored pattern and counts", () -> {
            var grid = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
            grid.set(4, new ItemStack(Items.PAPER, 2));
            grid.set(5, new ItemStack(Items.GLOWSTONE_DUST, 3));
            var in = CraftingRecipeInput.createPositioned(3, 3, grid).input();
            var result = craft(server, in);
            require(result.isOf(Items.REDSTONE) && result.getCount() == 2, result.toString());
        });
        check("shaped alternatives: repeated cells can require different items", () -> {
            var valid = input(new ItemStack(Items.POPPY), new ItemStack(Items.DANDELION), ItemStack.EMPTY,
                    new ItemStack(Items.BLUE_ORCHID), new ItemStack(Items.ALLIUM));
            require(recipeMatches(server, "basic_shaped_distinct", valid),
                    "Four different shaped alternatives did not match");
            var duplicated = input(new ItemStack(Items.POPPY), new ItemStack(Items.POPPY), ItemStack.EMPTY,
                    new ItemStack(Items.BLUE_ORCHID), new ItemStack(Items.ALLIUM));
            require(!recipeMatches(server, "basic_shaped_distinct", duplicated),
                    "A duplicated shaped alternative bypassed the distinct rule");
        });
        check("disabled and corrupted recipes are excluded", () -> {
            require(match(server, input(new ItemStack(Items.NETHER_STAR))).isEmpty(),
                    "Disabled recipe is craftable");
            require(server.getRecipeManager().get(recipe("basic_missing", "", 1).serverRecipeId()).isEmpty(),
                    "Recipe with a missing item was registered");
        });
        check("world isolation and automatic library copies", () -> {
            require(match(server, input(new ItemStack(Items.DRAGON_EGG))).isEmpty(),
                    "Another world's recipe leaked");
            require(ConfigLoader.get().world_configs.values().stream().allMatch(world ->
                            world.custom_recipes.stream().anyMatch(recipe ->
                                    "basic_all_worlds".equals(recipe.global_library_source_id))),
                    "Automatic library copies are missing");
        });
        check("save and reload preserve ingredient alternatives", () -> {
            var saved = ConfigLoader.activeWorldConfig().custom_recipes.stream()
                    .filter(recipe -> "basic_alternatives".equals(recipe.id)).findFirst().orElseThrow();
            require(saved.ingredientOptions(0).equals(List.of(
                    "minecraft:stone", "minecraft:cobblestone", "minecraft:andesite")),
                    "Alternatives changed after persistence");
            require(saved.ingredientCount(0) == 2, "Alternative count changed after persistence");
            var distinct = ConfigLoader.activeWorldConfig().custom_recipes.stream()
                    .filter(recipe -> "basic_distinct_alternatives".equals(recipe.id)).findFirst().orElseThrow();
            require(distinct.ingredientRequiresDistinct(0) && distinct.ingredientRequiresDistinct(3),
                    "Distinct alternative rule changed after persistence");
        });

        try {
            Files.writeString(output().resolve("results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        long failures = results.stream().filter(result -> result.get("status").equals("FAIL")).count();
        CustomRecipeMod.LOGGER.info("[BASIC TEST] FINISHED: {} passed, {} failed",
                results.size() - failures, failures);
        return failures == 0 ? 1 : 0;
    }
}
