package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/** Global full-stack item library used by every recipe target. */
@Environment(EnvType.CLIENT)
final class CustomItemsScreen extends Screen {
    private static final int MAX_ROW_WIDTH = 560;
    private final ConfigScreen parent;
    private int page;

    CustomItemsScreen(ConfigScreen parent) {
        super(Text.translatable("customrecipe.custom_items.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int rowWidth = rowWidth();
        int x = rowX();
        int top = 52;
        List<CustomItemEntry> items = parent.customItems;
        int pageSize = pageSize();
        int start = page * pageSize;
        boolean inWorld = client.world != null && ClientServerConfigNetworking.canManageServer();
        for (int i = start; i < Math.min(items.size(), start + pageSize); i++) {
            CustomItemEntry entry = items.get(i);
            int rowY = top + (i - start) * 28;
            int deleteX = x + rowWidth - 76;
            int giveX = deleteX - 60;
            int renameX = giveX - 84;
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.custom_items.rename"),
                    button -> client.setScreen(new RenameCustomItemScreen(this, entry)))
                    .dimensions(renameX, rowY + 3, 80, 20).build());
            ButtonWidget give = ButtonWidget.builder(Text.translatable("customrecipe.custom_items.give"),
                    button -> give(entry)).dimensions(giveX, rowY + 3, 56, 20).build();
            give.active = inWorld;
            addDrawableChild(give);
            addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.custom_items.delete"),
                    button -> delete(entry)).dimensions(deleteX, rowY + 3, 72, 20).build());
        }
        ButtonWidget previous = ButtonWidget.builder(Text.literal("<"), button -> { page--; clearAndInit(); })
                .dimensions(width / 2 - 70, height - 50, 30, 20).build();
        previous.active = page > 0;
        addDrawableChild(previous);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), button -> { page++; clearAndInit(); })
                .dimensions(width / 2 + 40, height - 50, 30, 20).build();
        next.active = (page + 1) * pageSize < items.size();
        addDrawableChild(next);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customrecipe.button.back"),
                button -> client.setScreen(parent)).dimensions(width / 2 - 80, height - 25, 160, 20).build());
    }

    private void give(CustomItemEntry entry) {
        if (client.world != null && ClientServerConfigNetworking.canManageServer()) {
            client.getNetworkHandler().sendChatCommand("customrecipe_item give " + entry.id);
        }
    }

    boolean canUseId(CustomItemEntry entry, String requestedId) {
        return parent.canUseCustomItemId(entry, requestedId);
    }

    boolean rename(CustomItemEntry entry, String name, String id) {
        return parent.renameCustomItem(entry, name, id);
    }

    private void delete(CustomItemEntry entry) {
        parent.customItems.remove(entry);
        int maxPage = Math.max(0, (parent.customItems.size() - 1) / pageSize());
        page = Math.min(page, maxPage);
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int rowWidth = rowWidth();
        int x = rowX();
        int top = 52;
        int pageSize = pageSize();
        int start = page * pageSize;
        // Row backgrounds must be queued before widgets; otherwise their tint
        // is painted over the buttons and makes active controls look disabled.
        for (int i = start; i < Math.min(parent.customItems.size(), start + pageSize); i++) {
            int rowY = top + (i - start) * 28;
            context.fill(x, rowY, x + rowWidth, rowY + 26, 0xAA202630);
        }
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.custom_items.command_hint"), width / 2, 33, 0xFFAAAAAA);
        for (int i = start; i < Math.min(parent.customItems.size(), start + pageSize); i++) {
            CustomItemEntry entry = parent.customItems.get(i);
            int rowY = top + (i - start) * 28;
            ItemStack stack = ClientItemStacks.fromReference(CustomItemLibrary.reference(entry.id), parent.rootConfig());
            if (!stack.isEmpty()) context.drawItem(stack, x + 5, rowY + 5);
            context.drawText(textRenderer, entry.name, x + 26, rowY + 4, 0xFFFFFFFF, false);
            context.drawText(textRenderer, entry.id + "  •  " + entry.item_id, x + 26, rowY + 15, 0xFF999999, false);
        }
        int pages = Math.max(1, (parent.customItems.size() + pageSize - 1) / pageSize);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pages), width / 2, height - 44, 0xFFCCCCCC);
        if (parent.customItems.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customrecipe.custom_items.empty"), width / 2, height / 2, 0xFFCCCCCC);
        }
    }

    @Override public void close() { client.setScreen(parent); }

    private int pageSize() { return Math.max(1, (height - 112) / 28); }
    private int rowWidth() { return Math.min(MAX_ROW_WIDTH, width - 24); }
    private int rowX() { return (width - rowWidth()) / 2; }
}
