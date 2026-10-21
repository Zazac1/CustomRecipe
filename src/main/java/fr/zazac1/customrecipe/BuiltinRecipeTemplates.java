package fr.zazac1.customrecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Single runtime source for the optional recipes exposed by Quick Add. */
public final class BuiltinRecipeTemplates {
    private record Template(String id, String result, String[][] grid) {}

    private static final List<Template> ALL = List.of(
            new Template("totem_of_undying", "minecraft:totem_of_undying", new String[][] {
                    {null, "minecraft:emerald", null},
                    {"minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"},
                    {null, "minecraft:gold_block", null}
            }),
            new Template("enchanted_golden_apple", "minecraft:enchanted_golden_apple", new String[][] {
                    {"minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"},
                    {"minecraft:gold_block", "minecraft:apple", "minecraft:gold_block"},
                    {"minecraft:gold_block", "minecraft:gold_block", "minecraft:gold_block"}
            }),
            new Template("elytra", "minecraft:elytra", new String[][] {
                    {null, "minecraft:string", null},
                    {"minecraft:phantom_membrane", "minecraft:feather", "minecraft:phantom_membrane"},
                    {"minecraft:phantom_membrane", null, "minecraft:phantom_membrane"}
            }),
            new Template("experience_bottle", "minecraft:experience_bottle", new String[][] {
                    {null, "minecraft:lapis_lazuli", null},
                    {"minecraft:emerald", "minecraft:glass_bottle", "minecraft:emerald"},
                    {null, "minecraft:lapis_lazuli", null}
            })
    );

    public static final List<String> IDS = ALL.stream().map(Template::id).toList();

    public static int count() { return ALL.size(); }
    public static String id(int index) { return ALL.get(index).id(); }
    public static String result(int index) { return ALL.get(index).result(); }
    public static boolean contains(String id) { return IDS.contains(id); }

    /** Creates an independent recipe; the bundled template is never mutated. */
    public static CustomRecipeEntry create(int index) {
        Template template = ALL.get(index);
        CustomRecipeEntry entry = new CustomRecipeEntry();
        entry.id = UUID.randomUUID().toString();
        entry.type = "shaped";
        entry.result = template.result();
        entry.count = 1;
        entry.enabled = Boolean.TRUE;

        Map<String, Character> itemKeys = new LinkedHashMap<>();
        char next = 'A';
        for (String[] row : template.grid()) {
            StringBuilder patternRow = new StringBuilder();
            for (String itemId : row) {
                if (itemId == null) {
                    patternRow.append(' ');
                } else {
                    if (!itemKeys.containsKey(itemId)) itemKeys.put(itemId, next++);
                    patternRow.append(itemKeys.get(itemId));
                }
            }
            entry.pattern.add(patternRow.toString());
        }
        itemKeys.forEach((itemId, symbol) -> entry.keys.put(String.valueOf(symbol), itemId));
        RecipeIntegrity.rememberRequiredMods(entry);
        return entry;
    }

    private BuiltinRecipeTemplates() {}
}

