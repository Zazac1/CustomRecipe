package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Original GUI sprites supplied for Custom Recipe' actions and recipe states. */
final class CustomRecipeSprites {
    static final ResourceLocation WARNING = sprite("warning");
    static final ResourceLocation WARNING_INFO = sprite("warning_info");
    static final ResourceLocation CORRUPTED = sprite("corrupted");
    static final ResourceLocation SCROLLER_IDLE = sprite("scroller_idle");
    static final ResourceLocation SCROLLER_ACTIVE = sprite("scroller_active");
    static final ResourceLocation ACCEPT = sprite("accept");
    static final ResourceLocation REJECT = sprite("reject");
    static final ResourceLocation ADD = sprite("add");
    static final ResourceLocation SAVE = sprite("save");
    static final ResourceLocation LOCKED_INFO = sprite("locked_info");
    static final ResourceLocation LOCKED_BUTTON = sprite("locked_button");
    static final ResourceLocation UNLOCKED_INFO = sprite("unlocked_info");
    static final ResourceLocation UNLOCKED_BUTTON = sprite("unlocked_button");
    static final ResourceLocation KNOWN_BY_DEFAULT = sprite("known_by_default");
    static final ResourceLocation NOT_KNOWN_BY_DEFAULT = sprite("not_known_by_default");
    static final ResourceLocation KNOWN_BY_DEFAULT_INFO = sprite("known_by_default_info");
    static final ResourceLocation NOT_KNOWN_BY_DEFAULT_INFO = sprite("not_known_by_default_info");
    static final ResourceLocation SLOT = texture("gui/container/slot");
    static final ResourceLocation SLOT_SELECTED = texture("gui/container/slot_selected");
    static final ResourceLocation OUTPUT = texture("gui/container/output");
    static final ResourceLocation OUTPUT_SELECTED = texture("gui/container/output_selected");
    static final ResourceLocation OUTPUT_ARROW = texture("gui/container/output_arrow");
    static final ResourceLocation GRID_3X3 = texture("gui/container/grid_3x3");
    static final ResourceLocation CRAFTING_TABLE = texture("gui/container/crafting_table");
    static final ResourceLocation SHAPELESS_BG = texture("gui/container/shapeless_bg");
    static final ResourceLocation MOVE_DOWN = texture("gui/container/move_down");
    static final ResourceLocation MOVE_DOWN_HIGHLIGHTED = texture("gui/container/move_down_highlighted");

    private static ResourceLocation sprite(String name) {
        return new ResourceLocation(CustomRecipeMod.MOD_ID, "textures/gui/icons/" + name + ".png");
    }

    private static ResourceLocation texture(String path) {
        return new ResourceLocation(CustomRecipeMod.MOD_ID, "textures/" + path + ".png");
    }

    static void draw(GuiGraphics context, ResourceLocation sprite, int x, int y, int width, int height) {
        context.blit(sprite, x, y, 0, 0, width, height, width, height);
    }

    private CustomRecipeSprites() {}
}
