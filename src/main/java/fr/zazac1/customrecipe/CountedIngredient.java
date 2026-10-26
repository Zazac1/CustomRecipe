package fr.zazac1.customrecipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.stream.Stream;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Keeps quantities and exact-item requirements intact in vanilla recipe packets. */
public final class CountedIngredient extends Ingredient {
    private static final ResourceLocation ID = new ResourceLocation(CustomRecipeMod.MOD_ID, "counted");
    private static final Serializer SERIALIZER = new Serializer();
    private final ItemStack expected;
    private final boolean exact;

    private CountedIngredient(ItemStack expected, boolean exact) {
        super(Stream.of(new Ingredient.SingleItemList(expected.copy())));
        this.expected = expected.copy();
        this.exact = exact;
    }

    public static Ingredient ofStack(ItemStack stack, boolean exact) {
        return exact || stack.getCount() > 1 ? new CountedIngredient(stack, exact) : Ingredient.of(stack);
    }

    public int amount() { return expected.getCount(); }

    @Override public boolean test(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == expected.getItem()
                && (!exact || Java8.sameItemAndTag(stack, expected));
    }

    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return SERIALIZER; }

    @Override public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.addProperty("item", Registry.ITEM.getKey(expected.getItem()).toString());
        json.addProperty("count", amount());
        json.addProperty("exact", exact);
        if (expected.hasTag()) json.addProperty("nbt", expected.getTag().toString());
        return json;
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CraftingHelper.register(ID, SERIALIZER));
    }

    private static final class Serializer implements IIngredientSerializer<CountedIngredient> {
        @Override public CountedIngredient parse(PacketBuffer buffer) {
            ItemStack stack = buffer.readItem();
            if (stack.isEmpty()) throw new IllegalArgumentException("Empty counted ingredient");
            return new CountedIngredient(stack, buffer.readBoolean());
        }
        @Override public void write(PacketBuffer buffer, CountedIngredient ingredient) {
            buffer.writeItem(ingredient.expected);
            buffer.writeBoolean(ingredient.exact);
        }
        @Override public CountedIngredient parse(JsonObject json) {
            ResourceLocation id = ResourceLocation.tryParse(json.get("item").getAsString());
            if (!Java8.registryContains(Registry.ITEM, id)) throw new JsonSyntaxException("Unknown ingredient item");
            ItemStack stack = new ItemStack(Registry.ITEM.get(id));
            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            if (count < 1 || count > stack.getMaxStackSize()) throw new JsonSyntaxException("Invalid ingredient count");
            stack.setCount(count);
            if (json.has("nbt")) {
                try { stack.setTag(JsonToNBT.parseTag(json.get("nbt").getAsString())); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                    throw new JsonSyntaxException("Invalid ingredient NBT", error);
                }
            }
            return new CountedIngredient(stack, json.has("exact") && json.get("exact").getAsBoolean());
        }
    }
}
