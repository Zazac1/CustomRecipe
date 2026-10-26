package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.VanillaRecipePage;
import fr.zazac1.customrecipe.VanillaRecipeDetails;
import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import net.minecraft.client.gui.screen.Screen;
import fr.zazac1.customrecipe.client.compat.Button;
import fr.zazac1.customrecipe.client.compat.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/** Read-only crafting preview before enabling or disabling a recipe. */
public final class VanillaRecipeDetailsScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    private static final int VARIANT_COLUMNS = 6;
    private static final int VARIANT_CELL = 24;
    private final VanillaRecipesScreen parent;
    private final VanillaRecipePage.VanillaRecipeInfo recipe;
    private VanillaRecipeDetails details;
    private VanillaRecipeDetails.VariantPreview selectedVariant;

    VanillaRecipeDetailsScreen(VanillaRecipesScreen parent, VanillaRecipePage.VanillaRecipeInfo recipe) {
        super(fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.title"));
        this.parent = parent;
        this.recipe = recipe;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        ConfigScreen config = parent.configScreen();
        addLegacyRenderable((ctx, mx, my, d) -> RecipeTargetBadge.draw(ctx, minecraft, config.target(),
                config.target().isWorld() ? config.target().displayName() : "Global Library"));
        if (details == null) parent.requestDetails(this, recipe.id());
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.back"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 - 50, height - 28, 100, 20).build());

        if (details != null) {
            int x = variantGridX();
            for (VanillaRecipeDetails.VariantPreview variant : details.variants().stream().limit(48).collect(java.util.stream.Collectors.toList())) {
                int index = details.variants().indexOf(variant);
                int buttonX = x + (index % VARIANT_COLUMNS) * VARIANT_CELL;
                int buttonY = 44 + (index / VARIANT_COLUMNS) * VARIANT_CELL;
                Button button = addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.empty(), b -> {
                    selectedVariant = variant;
                    rebuildWidgets();
                }).bounds(buttonX, buttonY, 20, 20).build());
                button.setTooltip(Tooltip.create(fr.zazac1.customrecipe.Texts.literal(itemName(variant.materialId()))));
            }
            if (!details.variants().isEmpty()) {
                String material = selectedVariant == null ? details.variants().get(0).materialId() : selectedVariant.materialId();
                boolean blocked = parent.isVariantDisabled(recipe.id(), material);
                int actionsY = variantActionsY();
                addRenderableWidget(Button.builder(blocked ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.variant_disabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555)))
                                : fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.disable_variant").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555))), b -> {
                            parent.toggleVariant(recipe.id(), material);
                            rebuildWidgets();
                        }).bounds(x, actionsY, 142, 20).build());
            }
        }
        if (details != null && !details.variants().isEmpty()) {
            boolean allDisabled = parent.isRecipeDisabled(recipe.id());
            addRenderableWidget(Button.builder(allDisabled ? fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.variants_disabled").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555)))
                            : fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.disable_variants").setStyle(net.minecraft.util.text.Style.EMPTY.withColor(net.minecraft.util.text.Color.fromRgb(0xFF5555))), b -> {
                        parent.toggleAllVariants(recipe.id());
                        rebuildWidgets();
                    }).bounds(variantGridX(), variantActionsY() + 24, 142, 20).build());
        }
    }

    void applyDetails(VanillaRecipeDetails details) {
        if (!recipe.id().equals(details.recipeId())) return;
        this.details = details;
        if (!details.variants().isEmpty() && selectedVariant == null) selectedVariant = details.variants().get(0);
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xD0101010, 0xE0101010);
        super.render(ctx, mouseX, mouseY, delta);

        int left = width / 2 - 86;
        int top = height / 2 - 74;
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.details.title"), width / 2, top - 30, 0xFFFFFFFF);
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal(recipe.id()), width / 2, top - 16, 0xFFAAAAAA);

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
        ctx.drawString(font, "->", left + 82, top + 27, 0xFFFFFFFF);
        ctx.fill(left + 108, top + 24, left + 132, top + 48, 0xFF303030);
        drawItem(ctx, recipe.result(), left + 112, top + 28);
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal(itemName(recipe.result())), width / 2, top + 82, 0xFFFFFFFF);
        String layout = recipe.shapeless() ? "Shapeless: JSON ingredient order" : "Shaped: " + recipe.gridWidth() + "x" + recipe.gridHeight() + " pattern";
        ctx.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal(layout), width / 2, top + 98, 0xFFAAAAAA);
        if (details != null && !details.variants().isEmpty()) {
            ctx.drawString(font, "Material variants", variantGridX(), 26, 0xFFFFFFFF);
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
            ctx.drawString(font, "No interchangeable material", width - 180, 26, 0xFFAAAAAA);
        }
    }

    private void drawItem(GuiGraphics ctx, String id, int x, int y) {
        if (id == null || id.trim().isEmpty() || id.startsWith("#")) return;
        var item = Registry.ITEM.get(ResourceLocation.tryParse(id));
        if (item != null && item != Items.AIR) ctx.renderItem(new ItemStack(item), x, y);
    }

    private String itemName(String id) {
        var item = Registry.ITEM.get(ResourceLocation.tryParse(id));
        return item == null || item == Items.AIR ? id : new ItemStack(item).getHoverName().getString();
    }

    private int variantGridX() { return width - 150; }

    private int variantActionsY() {
        if (details == null) return 44;
        int rows = (Math.min(48, details.variants().size()) + VARIANT_COLUMNS - 1) / VARIANT_COLUMNS;
        return 44 + rows * VARIANT_CELL + 6;
    }

    private void drawBorder(GuiGraphics ctx, int x, int y, int color, boolean selected) {
        int thickness = selected ? 2 : 1;
        ctx.fill(x - thickness, y - thickness, x + 20 + thickness, y, color);
        ctx.fill(x - thickness, y + 20, x + 20 + thickness, y + 20 + thickness, color);
        ctx.fill(x - thickness, y, x, y + 20, color);
        ctx.fill(x + 20, y, x + 20 + thickness, y + 20, color);
    }

    /** Selection is white only; red/green always means the saved server state. */
    private void drawSelectionCorners(GuiGraphics ctx, int x, int y) {
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

    @Override public boolean isPauseScreen() { return true; }
}
