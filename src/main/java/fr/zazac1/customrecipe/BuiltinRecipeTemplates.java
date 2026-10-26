package fr.zazac1.customrecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Single runtime source for the optional recipes exposed by Quick Add. */
public final class BuiltinRecipeTemplates {
    private static final class Template {
        private final String id;
        private final String result;
        private final String[][] grid;
        public Template(String id, String result, String[][] grid) { this.id=id; this.result=result; this.grid=grid; }
        public String id() { return id; }
        public String result() { return result; }
        public String[][] grid() { return grid; }
        @Override public boolean equals(Object obj) { if (this==obj) return true; if (obj==null || obj.getClass()!=getClass()) return false; Template other=(Template)obj; return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(result, other.result) && java.util.Objects.equals(grid, other.grid); }
        @Override public int hashCode() { return java.util.Objects.hash(id, result, grid); }
}

    private static final List<Template> ALL = fr.zazac1.customrecipe.Java8.list(
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

    public static final List<String> IDS = ALL.stream().map(Template::id).collect(java.util.stream.Collectors.toList());

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

