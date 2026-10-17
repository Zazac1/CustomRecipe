package fr.zazac1.customrecipe;

/** One full ItemStack saved in the global Custom Items library. */
public final class CustomItemEntry {
    public String id = "";
    public String name = "";
    /** Base registry item, kept readable and usable as a safe fallback. */
    public String item_id = "minecraft:air";
    /** ItemStack.CODEC JSON. The stored stack always has a count of one. */
    public String stack_data = "";
}
