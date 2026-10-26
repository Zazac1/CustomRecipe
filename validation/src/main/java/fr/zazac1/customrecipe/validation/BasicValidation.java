package fr.zazac1.customrecipe.validation;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import fr.zazac1.customrecipe.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.util.IWorldPosCallable;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.WorkbenchContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;
import static net.minecraft.command.Commands.literal;

/** Runs only in an isolated development server; excluded from released jars. */
@Mod.EventBusSubscriber(modid = CustomRecipeMod.MOD_ID)
public final class BasicValidation {
    private static final List<Map<String,String>> results=new ArrayList<>();
    private static Path output() { return FMLPaths.GAMEDIR.get().resolve("../../logs/basic").normalize(); }
    private static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("customrecipe.json"); }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void check(String name,Check check) {
        String status="PASS",detail="";
        try { check.run(); } catch (Throwable error) { status="FAIL"; detail=error.toString(); CustomRecipeMod.LOGGER.error("[BASIC TEST] "+name,error); }
        Map<String,String> result=new LinkedHashMap<>(); result.put("test",name); result.put("status",status); result.put("details",detail); results.add(result);
        CustomRecipeMod.LOGGER.info("[BASIC TEST] {}: {} {}",status,name,detail);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("cr_basic_tests").requires(source -> source.getEntity()==null)
                .then(literal("prepare").executes(context -> prepare(context.getSource().getServer())))
                .then(literal("run").executes(context -> run(context.getSource().getServer()))));
    }
    private static CustomRecipeEntry recipe(String id,String result,int count,String... ingredients) {
        CustomRecipeEntry entry=new CustomRecipeEntry(); entry.id=id; entry.result=result; entry.count=count; entry.enabled=true; entry.known_by_default=true;
        entry.ingredients=new ArrayList<>(Arrays.asList(ingredients)); return entry;
    }
    private static ItemStack named(Item item,String name,int damage) {
        ItemStack stack=new ItemStack(item); stack.setHoverName(fr.zazac1.customrecipe.Texts.literal(name)); if (damage>0) stack.setDamageValue(damage); return stack;
    }
    private static CustomItemEntry item(MinecraftServer server,String id,ItemStack stack) {
        CustomItemEntry entry=new CustomItemEntry(); entry.id=id; entry.name=stack.getHoverName().getString();
        entry.item_id=Registry.ITEM.getKey(stack.getItem()).toString(); entry.stack_data=CustomItemLibrary.encode(stack,server.registryAccess()); return entry;
    }
    private static int prepare(MinecraftServer server) {
        try {
            Files.createDirectories(output());
            if (Files.exists(configPath()) && !Files.exists(output().resolve("server-config-before.json"))) Files.copy(configPath(),output().resolve("server-config-before.json"));
            ModConfig config=new ModConfig(); config.recipe_target_version=2;
            WorldRecipeConfig world=config.getOrCreateWorldConfig(WorldRecipeAssignments.activeWorldId(),"Port validation");
            CustomRecipeEntry shapeless=recipe("basic_shapeless_counted","minecraft:apple",2,"minecraft:diamond","minecraft:stick");
            shapeless.ingredient_counts=new ArrayList<>(Arrays.asList(2,3)); world.custom_recipes.add(shapeless);
            CustomRecipeEntry shaped=recipe("basic_shaped_counted","minecraft:redstone",2); shaped.type="shaped"; shaped.pattern=new ArrayList<>(Arrays.asList("AB"));
            shaped.keys.put("A","minecraft:glowstone_dust"); shaped.keys.put("B","minecraft:paper"); shaped.key_counts.put("A",3); shaped.key_counts.put("B",2); world.custom_recipes.add(shaped);
            config.custom_items.add(item(server,"exact_rod",named(Items.FISHING_ROD,"Validation Rod",7)));
            config.custom_items.add(item(server,"exact_output",named(Items.STICK,"Validation Prize",0)));
            config.custom_items.add(item(server,"collision",named(Items.GOLDEN_AXE,"Server Collision",3)));
            CustomRecipeEntry exact=recipe("basic_exact",CustomItemLibrary.reference("exact_output"),3,CustomItemLibrary.reference("exact_rod"),"minecraft:diamond");
            exact.ingredient_counts=new ArrayList<>(Arrays.asList(1,2)); world.custom_recipes.add(exact);
            CustomRecipeEntry disabled=recipe("basic_disabled","minecraft:emerald",1,"minecraft:nether_star"); disabled.enabled=false; world.custom_recipes.add(disabled);
            world.custom_recipes.add(recipe("basic_missing","missing_basic_test:absent",1,"minecraft:bedrock"));
            world.custom_recipes.add(recipe("basic_vanilla_priority","minecraft:apple",1,"minecraft:oak_planks"));
            world.custom_recipes.add(recipe("basic_vanilla_replacement","minecraft:apple",1,"minecraft:birch_planks")); world.disabled_recipes.add("minecraft:birch_button");
            world.disabled_recipe_variants.add(new RecipeVariantRule("minecraft:chest","minecraft:oak_planks"));
            config.global_disabled_recipes.add("minecraft:torch"); world.enabled_recipes.add("minecraft:torch");
            CustomRecipeEntry automatic=recipe("basic_all_worlds","minecraft:emerald",2,"minecraft:nether_star","minecraft:diamond"); automatic.global_auto_add=true; config.global_library.custom_recipes.add(automatic);
            config.getOrCreateWorldConfig("basic-other-world","Other test world").custom_recipes.add(recipe("basic_other_only","minecraft:stone",1,"minecraft:dragon_egg"));
            require(ConfigLoader.saveAndInvalidate(config),"Cannot save fixtures");
            ModConfig local=new ModConfig(); local.recipe_target_version=2; local.custom_items.add(item(server,"collision",named(Items.FISHING_ROD,"Local Collision",7)));
            local.global_library.custom_recipes.add(recipe("basic_local_draft","minecraft:quartz",2,CustomItemLibrary.reference("collision"),"minecraft:diamond"));
            ConfigLoader.exportTo(local,output().resolve("client-fixture.json"));
            server.getCommands().performCommand(server.createCommandSourceStack(),"reload");
            CustomRecipeMod.LOGGER.info("[BASIC TEST] Fixtures prepared; wait for reload before run."); return 1;
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static ICraftingRecipe recipe(MinecraftServer server,String id) { return (ICraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("customrecipe","custom/"+id)).orElseThrow(IllegalStateException::new); }
    private static WorkbenchContainer menu(MinecraftServer server,ServerPlayerEntity player,ItemStack... grid) {
        player.inventory.clearContent(); WorkbenchContainer menu=new WorkbenchContainer(99,player.inventory,IWorldPosCallable.create(server.overworld(),player.blockPosition()));
        player.containerMenu=menu; for (int i=0;i<grid.length;i++) menu.getSlot(i+1).set(grid[i]); menu.slotsChanged(menu.getSlot(1).container); return menu;
    }
    private static CraftingInventory input(ItemStack... grid) { CraftingInventory input=new CraftingInventory(new WorkbenchContainer(98,FakePlayerFactory.getMinecraft(net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer().overworld()).inventory),3,3); for (int i=0;i<grid.length;i++) input.setItem(i,grid[i]); return input; }
    private static Optional<ICraftingRecipe> match(MinecraftServer server,CraftingInventory input) { return server.getRecipeManager().getRecipeFor(IRecipeType.CRAFTING,input,server.overworld()); }
    private static int amount(ServerPlayerEntity player,Item item) { int total=0; for (int i=0;i<player.inventory.getContainerSize();i++) { ItemStack stack=player.inventory.getItem(i); if (stack.getItem()==item) total+=stack.getCount(); } return total; }
    private static void save(ServerPlayerEntity player,String json) throws Exception { Method method=ServerConfigNetworking.class.getDeclaredMethod("handleSave",ServerPlayerEntity.class,String.class); method.setAccessible(true); method.invoke(null,player,json); }
    private static int run(MinecraftServer server) {
        results.clear(); ServerPlayerEntity player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.fromString("5c4d6ae7-d546-42cf-b35d-20a41d69e024"),"BasicTests"));
        check("Quick Add: templates are independent and not bundled recipes",() -> {
            for (int i=0;i<BuiltinRecipeTemplates.count();i++) { CustomRecipeEntry first=BuiltinRecipeTemplates.create(i),second=BuiltinRecipeTemplates.create(i); require(!first.id.equals(second.id),"Copies share ID"); require(!server.getRecipeManager().byKey(new ResourceLocation("customrecipe",BuiltinRecipeTemplates.id(i))).isPresent(),"Template registered without adding"); }
        });
        check("shapeless: unordered ingredients and multiple output",() -> { CraftingInventory in=input(new ItemStack(Items.STICK,3),ItemStack.EMPTY,new ItemStack(Items.DIAMOND,2)); ItemStack out=match(server,in).get().assemble(in); require(out.getItem()==Items.APPLE && out.getCount()==2,out.toString()); });
        check("shapeless: insufficient ingredients rejected",() -> require(!match(server,input(new ItemStack(Items.DIAMOND),new ItemStack(Items.STICK,3))).isPresent(),"Incomplete craft matched"));
        check("shaped: shifted mirrored pattern and counts",() -> { CraftingInventory in=input(); in.setItem(4,new ItemStack(Items.PAPER,2)); in.setItem(5,new ItemStack(Items.GLOWSTONE_DUST,3)); require(match(server,in).get().assemble(in).getItem()==Items.REDSTONE,"Shifted/mirrored match failed"); });
        check("normal craft: exact consumption and cursor result",() -> { WorkbenchContainer menu=menu(server,player,new ItemStack(Items.DIAMOND,5),new ItemStack(Items.STICK,8)); require(menu.getSlot(0).getItem().getItem()==Items.APPLE,"No output"); menu.clicked(0,0,ClickType.PICKUP,player); require(player.inventory.getCarried().getCount()==2,"Wrong cursor result"); require(menu.getSlot(1).getItem().getCount()==3 && menu.getSlot(2).getItem().getCount()==5,"Wrong consumption"); });
        check("shift craft: repeats without duplication",() -> { WorkbenchContainer menu=menu(server,player,new ItemStack(Items.DIAMOND,6),new ItemStack(Items.STICK,9)); menu.clicked(0,0,ClickType.QUICK_MOVE,player); require(amount(player,Items.APPLE)==6,"Wrong output amount"); require(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(),"Ingredients remain"); });
        check("exact NBT: damaged named ingredient and named output",() -> { WorkbenchContainer menu=menu(server,player,named(Items.FISHING_ROD,"Validation Rod",7),new ItemStack(Items.DIAMOND,2)); ItemStack out=menu.getSlot(0).getItem(); require(out.getItem()==Items.STICK && out.getCount()==3 && out.getHoverName().getString().equals("Validation Prize"),out.toString()); });
        check("exact NBT: plain or differently damaged ingredient rejected",() -> { require(!match(server,input(new ItemStack(Items.FISHING_ROD),new ItemStack(Items.DIAMOND,2))).isPresent(),"Plain rod matched"); require(!match(server,input(named(Items.FISHING_ROD,"Validation Rod",8),new ItemStack(Items.DIAMOND,2))).isPresent(),"Wrong damage matched"); });
        check("recipe book: counted ingredients autofill and shift craft",() -> {
            WorkbenchContainer menu=menu(server,player);
            ICraftingRecipe recipe=recipe(server,"basic_shapeless_counted");
            player.getRecipeBook().add(recipe);
            player.inventory.setItem(0,new ItemStack(Items.DIAMOND,6));
            player.inventory.setItem(1,new ItemStack(Items.STICK,9));
            // Use the same entry point as a recipe-book click, including the mod's hooks.
            menu.handlePlacement(true,recipe,player);
            require(menu.getSlot(0).getItem().getItem()==Items.APPLE,"Book did not fill");
            menu.clicked(0,0,ClickType.QUICK_MOVE,player);
            require(amount(player,Items.APPLE)==6,"Wrong count after book fill");
        });
        check("vanilla priority and deliberate replacement",() -> { CraftingInventory oak=input(new ItemStack(Items.OAK_PLANKS)),birch=input(new ItemStack(Items.BIRCH_PLANKS)); require(match(server,oak).get().assemble(oak).getItem()==Items.OAK_BUTTON,"Vanilla priority lost"); require(match(server,birch).get().assemble(birch).getItem()==Items.APPLE,"Replacement unavailable"); });
        check("material variants: oak chest blocked, spruce allowed",() -> { for (Item item:Arrays.asList(Items.OAK_PLANKS,Items.SPRUCE_PLANKS)) { CraftingInventory in=input(); for (int i=0;i<9;i++) if (i!=4) in.setItem(i,new ItemStack(item)); require(match(server,in).isPresent()==(item==Items.SPRUCE_PLANKS),"Wrong material filtering"); } });
        check("world override: globally disabled torch re-enabled",() -> { CraftingInventory in=input(new ItemStack(Items.COAL)); in.setItem(3,new ItemStack(Items.STICK)); require(match(server,in).get().assemble(in).getItem()==Items.TORCH,"World override failed"); });
        check("disabled/corrupted recipes and world isolation",() -> { require(!match(server,input(new ItemStack(Items.NETHER_STAR))).isPresent(),"Disabled recipe craftable"); require(!match(server,input(new ItemStack(Items.DRAGON_EGG))).isPresent(),"Other world's recipe leaked"); require(!server.getRecipeManager().byKey(new ResourceLocation("customrecipe","custom/basic_missing")).isPresent(),"Missing item recipe registered"); });
        check("automatic library copies in existing and new worlds",() -> { require(ConfigLoader.get().world_configs.values().stream().allMatch(w -> w.custom_recipes.stream().anyMatch(r -> "basic_all_worlds".equals(r.global_library_source_id))),"Copies missing"); ConfigLoader.ensureAutomaticLibraryRecipesForWorld("basic-new-world","New test world"); require(ConfigLoader.get().findWorldConfig("basic-new-world").custom_recipes.stream().anyMatch(r -> "basic_all_worlds".equals(r.global_library_source_id)),"New world's copy missing"); });
        check("export/import retains custom items and deduplicates library",() -> { ModConfig config=ConfigLoader.get(); Path full=output().resolve("export-full.json"),library=output().resolve("export-library.json"); ConfigLoader.exportTo(config,full); ConfigLoader.exportLibraryTo(config,library); require(ConfigLoader.importFrom(full).custom_items.size()==config.custom_items.size(),"Items lost"); ModConfig destination=new ModConfig(); WorldRecipeConfig imported=ConfigLoader.importLibraryFrom(library); require(ConfigLoader.importLibraryInto(destination,imported).added()==1,"Import failed"); require(ConfigLoader.importLibraryInto(destination,imported).added()==0,"Import duplicated recipes"); });
        check("NBT codec preserves name and damage",() -> { ItemStack original=named(Items.FISHING_ROD,"Codec Check",17),decoded=CustomItemLibrary.decode(item(server,"codec_check",original),server.registryAccess()); require(fr.zazac1.customrecipe.Java8.sameItemAndTag(original,decoded),"NBT changed"); });
        check("save: non-operator refused without writing",() -> { server.getPlayerList().deop(player.getGameProfile()); byte[] before=Files.readAllBytes(configPath()); save(player,ConfigLoader.toJson(ConfigLoader.get())); require(Arrays.equals(before,Files.readAllBytes(configPath())),"Permission refusal wrote file"); });
        server.getPlayerList().op(player.getGameProfile());
        check("save: invalid JSON refused without writing",() -> { byte[] before=Files.readAllBytes(configPath()); save(player,"{invalid"); require(Arrays.equals(before,Files.readAllBytes(configPath())),"Invalid JSON wrote file"); });
        check("save: oversized input refused without writing",() -> { byte[] before=Files.readAllBytes(configPath()); char[] chars=new char[1_000_001]; Arrays.fill(chars,' '); save(player,new String(chars)); require(Arrays.equals(before,Files.readAllBytes(configPath())),"Oversized input wrote file"); });
        check("save: blocked write preserves live configuration",() -> { byte[] before=Files.readAllBytes(configPath()); Path temporary=configPath().resolveSibling("customrecipe.json.tmp"); Files.createDirectory(temporary); Path blocker=temporary.resolve("validation-blocker"); Files.write(blocker,new byte[]{1}); try { save(player,ConfigLoader.toJson(ConfigLoader.get())); require(Arrays.equals(before,Files.readAllBytes(configPath())),"Failed write changed file"); } finally { Files.deleteIfExists(blocker); Files.deleteIfExists(temporary); } });
        server.getPlayerList().deop(player.getGameProfile()); player.inventory.clearContent();
        try { Files.createDirectories(output()); Files.write(output().resolve("results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results).getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception error) { throw new RuntimeException(error); }
        long failures=results.stream().filter(r -> "FAIL".equals(r.get("status"))).count(); CustomRecipeMod.LOGGER.info("[BASIC TEST] FINISHED: {} passed, {} failed",results.size()-failures,failures); return failures==0?1:0;
    }
}
