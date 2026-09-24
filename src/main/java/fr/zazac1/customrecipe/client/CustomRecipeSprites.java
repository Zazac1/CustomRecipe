package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomRecipeMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Original GUI sprites supplied for Custom Recipe' actions and recipe states. */
final class CustomRecipeSprites {
    static final Identifier WARNING = sprite("warning");
    static final Identifier WARNING_INFO = sprite("warning_info");
    static final Identifier CORRUPTED = sprite("corrupted");
    static final Identifier SCROLLER_IDLE = sprite("scroller_idle");
    static final Identifier SCROLLER_ACTIVE = sprite("scroller_active");
    static final Identifier ACCEPT = sprite("accept");
    static final Identifier REJECT = sprite("reject");
    static final Identifier ADD = sprite("add");
    static final Identifier SAVE = sprite("save");
    static final Identifier LOCKED_INFO = sprite("locked_info");
    static final Identifier LOCKED_BUTTON = sprite("locked_button");
    static final Identifier UNLOCKED_INFO = sprite("unlocked_info");
    static final Identifier UNLOCKED_BUTTON = sprite("unlocked_button");
    static final Identifier KNOWN_BY_DEFAULT = sprite("known_by_default");
    static final Identifier NOT_KNOWN_BY_DEFAULT = sprite("not_known_by_default");
    static final Identifier KNOWN_BY_DEFAULT_INFO = sprite("known_by_default_info");
    static final Identifier NOT_KNOWN_BY_DEFAULT_INFO = sprite("not_known_by_default_info");
    static final Identifier SLOT = texture("gui/container/slot");
    static final Identifier SLOT_SELECTED = texture("gui/container/slot_selected");
    static final Identifier OUTPUT = texture("gui/container/output");
    static final Identifier OUTPUT_SELECTED = texture("gui/container/output_selected");
    static final Identifier OUTPUT_ARROW = texture("gui/container/output_arrow");
    static final Identifier GRID_3X3 = texture("gui/container/grid_3x3");
    static final Identifier CRAFTING_TABLE = texture("gui/container/crafting_table");
    static final Identifier SHAPELESS_BG = texture("gui/container/shapeless_bg");
    static final Identifier MOVE_DOWN = texture("gui/container/move_down");
    static final Identifier MOVE_DOWN_HIGHLIGHTED = texture("gui/container/move_down_highlighted");

    private static Identifier sprite(String name) {
        return new Identifier(CustomRecipeMod.MOD_ID, "textures/gui/icons/" + name + ".png");
    }

    private static Identifier texture(String path) {
        return new Identifier(CustomRecipeMod.MOD_ID, "textures/" + path + ".png");
    }

    static void draw(DrawContext context, Identifier sprite, int x, int y, int width, int height) {
        context.drawTexture(sprite, x, y, 0, 0, width, height, width, height);
    }

    private CustomRecipeSprites() {}
}
