package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Read-only crafting preview before enabling or disabling a recipe. */
@Environment(EnvType.CLIENT)
public final class VanillaRecipeDetailsScreen extends Screen {
    private static final int VARIANT_COLUMNS = 6;
    private static final int VARIANT_CELL = 24;
    private final VanillaRecipesScreen parent;
    private final VanillaRecipePage.VanillaRecipeInfo recipe;
    private VanillaRecipeDetails details;
    private VanillaRecipeDetails.VariantPreview selectedVariant;

    VanillaRecipeDetailsScreen(VanillaRecipesScreen parent, VanillaRecipePage.VanillaRecipeInfo recipe) {
        super(Text.literal("Recipe Preview"));
        this.parent = parent;
        this.recipe = recipe;
    }

    @Override
    protected void init() {
        if (details == null) parent.requestDetails(this, recipe.id());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());

        if (details != null) {
            int x = variantGridX();
            for (VanillaRecipeDetails.VariantPreview variant : details.variants().stream().limit(48).toList()) {
                int index = details.variants().indexOf(variant);
                int buttonX = x + (index % VARIANT_COLUMNS) * VARIANT_CELL;
                int buttonY = 44 + (index / VARIANT_COLUMNS) * VARIANT_CELL;
                ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    selectedVariant = variant;
                    clearAndInit();
                }).dimensions(buttonX, buttonY, 20, 20).build());
                button.setTooltip(Tooltip.of(Text.literal(itemName(variant.materialId()))));
            }
            if (!details.variants().isEmpty()) {
                String material = selectedVariant == null ? details.variants().getFirst().materialId() : selectedVariant.materialId();
                boolean blocked = parent.isVariantDisabled(recipe.id(), material);
                int actionsY = variantActionsY();
                addDrawableChild(ButtonWidget.builder(blocked ? Text.literal("Variant disabled").styled(s -> s.withColor(0xFF5555))
                                : Text.literal("Disable this variant").styled(s -> s.withColor(0xFF5555)), b -> {
                            parent.toggleVariant(recipe.id(), material);
                            clearAndInit();
                        }).dimensions(x, actionsY, 142, 20).build());
            }
        }
        if (details != null && !details.variants().isEmpty()) {
            boolean allDisabled = parent.isRecipeDisabled(recipe.id());
            addDrawableChild(ButtonWidget.builder(allDisabled ? Text.literal("All variants disabled").styled(s -> s.withColor(0xFF5555))
                            : Text.literal("Disable all variants").styled(s -> s.withColor(0xFF5555)), b -> {
                        parent.toggleAllVariants(recipe.id());
                        clearAndInit();
                    }).dimensions(variantGridX(), variantActionsY() + 24, 142, 20).build());
        }
    }

    void applyDetails(VanillaRecipeDetails details) {
        if (!recipe.id().equals(details.recipeId())) return;
        this.details = details;
        if (!details.variants().isEmpty() && selectedVariant == null) selectedVariant = details.variants().getFirst();
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xD0101010, 0xE0101010);
        super.render(ctx, mouseX, mouseY, delta);

        int left = width / 2 - 86;
        int top = height / 2 - 74;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Recipe preview"), width / 2, top - 30, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(recipe.id()), width / 2, top - 16, 0xFFAAAAAA);

        for (int slot = 0; slot < 9; slot++) {
            int column = slot % 3;
            int row = slot / 3;
            int x = left + column * 24;
            int y = top + row * 24;
            ctx.fill(x, y, x + 20, y + 20, 0xFF303030);
            List<String> slots = selectedVariant == null ? recipe.slots() : selectedVariant.slots();
            if (slot < slots.size()) {
                drawItem(ctx, slots.get(slot), x + 2, y + 2);
            }
        }
        ctx.drawTextWithShadow(textRenderer, "->", left + 82, top + 27, 0xFFFFFFFF);
        ctx.fill(left + 108, top + 24, left + 132, top + 48, 0xFF303030);
        drawItem(ctx, recipe.result(), left + 112, top + 28);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(itemName(recipe.result())), width / 2, top + 82, 0xFFFFFFFF);
        String layout = recipe.shapeless() ? "Shapeless: JSON ingredient order" : "Shaped: " + recipe.gridWidth() + "x" + recipe.gridHeight() + " pattern";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(layout), width / 2, top + 98, 0xFFAAAAAA);
        if (details != null && !details.variants().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "Material variants", variantGridX(), 26, 0xFFFFFFFF);
            for (int index = 0; index < details.variants().size() && index < 48; index++) {
                VanillaRecipeDetails.VariantPreview variant = details.variants().get(index);
                int x = variantGridX() + (index % VARIANT_COLUMNS) * VARIANT_CELL;
                int y = 44 + (index / VARIANT_COLUMNS) * VARIANT_CELL;
                // A full recipe disable applies visually to every selectable material too.
                boolean disabled = parent.isRecipeDisabled(recipe.id())
                        || parent.isVariantDisabled(recipe.id(), variant.materialId());
                int border = disabled ? 0xFFFF3333 : 0xFF22DD55;
                int background = disabled ? 0xAA4A0000 : 0xAA004A18;
                ctx.fill(x, y, x + 20, y + 20, background);
                drawBorder(ctx, x, y, border, true);
                if (selectedVariant != null && selectedVariant.materialId().equals(variant.materialId())) {
                    drawSelectionCorners(ctx, x, y);
                }
                drawItem(ctx, variant.materialId(), x + 2, y + 2);
            }
        } else if (details != null) {
            ctx.drawTextWithShadow(textRenderer, "No interchangeable material", width - 180, 26, 0xFFAAAAAA);
        }
    }

    private void drawItem(DrawContext ctx, String id, int x, int y) {
        if (id == null || id.isBlank() || id.startsWith("#")) return;
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        if (item != null && item != Items.AIR) ctx.drawItem(new ItemStack(item), x, y);
    }

    private String itemName(String id) {
        var item = Registries.ITEM.get(Identifier.tryParse(id));
        return item == null || item == Items.AIR ? id : new ItemStack(item).getName().getString();
    }

    private int variantGridX() { return width - 150; }

    private int variantActionsY() {
        if (details == null) return 44;
        int rows = (Math.min(48, details.variants().size()) + VARIANT_COLUMNS - 1) / VARIANT_COLUMNS;
        return 44 + rows * VARIANT_CELL + 6;
    }

    private void drawBorder(DrawContext ctx, int x, int y, int color, boolean selected) {
        int thickness = selected ? 2 : 1;
        ctx.fill(x - thickness, y - thickness, x + 20 + thickness, y, color);
        ctx.fill(x - thickness, y + 20, x + 20 + thickness, y + 20 + thickness, color);
        ctx.fill(x - thickness, y, x, y + 20, color);
        ctx.fill(x + 20, y, x + 20 + thickness, y + 20, color);
    }

    /** Selection is white only; red/green always means the saved server state. */
    private void drawSelectionCorners(DrawContext ctx, int x, int y) {
        int color = 0xFFFFFFFF;
        ctx.fill(x - 3, y - 3, x + 5, y - 1, color);
        ctx.fill(x - 3, y - 3, x - 1, y + 5, color);
        ctx.fill(x + 15, y - 3, x + 23, y - 1, color);
        ctx.fill(x + 21, y - 3, x + 23, y + 5, color);
        ctx.fill(x - 3, y + 21, x + 5, y + 23, color);
        ctx.fill(x - 3, y + 15, x - 1, y + 23, color);
        ctx.fill(x + 15, y + 21, x + 23, y + 23, color);
        ctx.fill(x + 21, y + 15, x + 23, y + 23, color);
    }

    @Override public boolean shouldPause() { return false; }
}
