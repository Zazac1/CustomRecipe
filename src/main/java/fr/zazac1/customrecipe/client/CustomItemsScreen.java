package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import fr.zazac1.customrecipe.client.compat.GuiGraphics;
import fr.zazac1.customrecipe.client.compat.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Global full-stack item library used by every recipe target. */
final class CustomItemsScreen extends fr.zazac1.customrecipe.client.compat.LegacyScreen {
    private static final int MAX_ROW_WIDTH = 560;
    private final ConfigScreen parent;
    private int page;

    CustomItemsScreen(ConfigScreen parent) {
        super(fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.title"));
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
        boolean inWorld = minecraft.level != null && ClientServerConfigNetworking.canManageServer();
        for (int i = start; i < Math.min(items.size(), start + pageSize); i++) {
            CustomItemEntry entry = items.get(i);
            int rowY = top + (i - start) * 28;
            int deleteX = x + rowWidth - 76;
            int giveX = deleteX - 60;
            int renameX = giveX - 84;
            addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.rename"),
                    button -> minecraft.setScreen(new RenameCustomItemScreen(this, entry)))
                    .bounds(renameX, rowY + 3, 80, 20).build());
            Button give = Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.give"),
                    button -> give(entry)).bounds(giveX, rowY + 3, 56, 20).build();
            give.active = inWorld;
            addRenderableWidget(give);
            addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.delete"),
                    button -> delete(entry)).bounds(deleteX, rowY + 3, 72, 20).build());
        }
        Button previous = Button.builder(fr.zazac1.customrecipe.Texts.literal("<"), button -> { page--; rebuildWidgets(); })
                .bounds(width / 2 - 70, height - 50, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(fr.zazac1.customrecipe.Texts.literal(">"), button -> { page++; rebuildWidgets(); })
                .bounds(width / 2 + 40, height - 50, 30, 20).build();
        next.active = (page + 1) * pageSize < items.size();
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(fr.zazac1.customrecipe.Texts.translatable("customrecipe.button.back"),
                button -> minecraft.setScreen(parent)).bounds(width / 2 - 80, height - 25, 160, 20).build());
    }

    private void give(CustomItemEntry entry) {
        if (minecraft.level != null && minecraft.getConnection() != null && ClientServerConfigNetworking.canManageServer()) {
            minecraft.player.chat("/customrecipe_item give " + entry.id);
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
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
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
        context.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);
        context.drawCenteredString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.command_hint"), width / 2, 33, 0xFFAAAAAA);
        for (int i = start; i < Math.min(parent.customItems.size(), start + pageSize); i++) {
            CustomItemEntry entry = parent.customItems.get(i);
            int rowY = top + (i - start) * 28;
            ItemStack stack = ClientItemStacks.fromReference(CustomItemLibrary.reference(entry.id), parent.rootConfig());
            if (!stack.isEmpty()) context.renderItem(stack, x + 5, rowY + 5);
            context.drawString(font, entry.name, x + 26, rowY + 4, 0xFFFFFFFF, false);
            context.drawString(font, entry.id + "  •  " + entry.item_id, x + 26, rowY + 15, 0xFF999999, false);
        }
        int pages = Math.max(1, (parent.customItems.size() + pageSize - 1) / pageSize);
        context.drawCenteredString(font, fr.zazac1.customrecipe.Texts.literal((page + 1) + " / " + pages), width / 2, height - 44, 0xFFCCCCCC);
        if (parent.customItems.isEmpty()) {
            context.drawCenteredString(font, fr.zazac1.customrecipe.Texts.translatable("customrecipe.custom_items.empty"), width / 2, height / 2, 0xFFCCCCCC);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private int pageSize() { return Math.max(1, (height - 112) / 28); }
    private int rowWidth() { return Math.min(MAX_ROW_WIDTH, width - 24); }
    private int rowX() { return (width - rowWidth()) / 2; }
}
