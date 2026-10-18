package fr.zazac1.customrecipe.client;

import fr.zazac1.customrecipe.CustomItemEntry;
import fr.zazac1.customrecipe.CustomItemLibrary;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Global full-stack item library used by every recipe target. */
@Environment(EnvType.CLIENT)
final class CustomItemsScreen extends Screen {
    private final ConfigScreen parent;
    private int page;

    CustomItemsScreen(ConfigScreen parent) {
        super(Component.translatable("customrecipe.custom_items.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 170;
        int top = 52;
        List<CustomItemEntry> items = parent.customItems;
        int pageSize = pageSize();
        int start = page * pageSize;
        boolean inWorld = minecraft.level != null && ClientServerConfigNetworking.canManageServer();
        for (int i = start; i < Math.min(items.size(), start + pageSize); i++) {
            CustomItemEntry entry = items.get(i);
            int rowY = top + (i - start) * 28;
            Button give = Button.builder(Component.translatable("customrecipe.custom_items.give"),
                    button -> give(entry)).bounds(x + 222, rowY + 3, 48, 20).build();
            give.active = inWorld;
            addRenderableWidget(give);
            addRenderableWidget(Button.builder(Component.translatable("customrecipe.custom_items.delete"),
                    button -> delete(entry)).bounds(x + 274, rowY + 3, 64, 20).build());
        }
        Button previous = Button.builder(Component.literal("<"), button -> { page--; rebuildWidgets(); })
                .bounds(width / 2 - 70, height - 50, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), button -> { page++; rebuildWidgets(); })
                .bounds(width / 2 + 40, height - 50, 30, 20).build();
        next.active = (page + 1) * pageSize < items.size();
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable("customrecipe.button.back"),
                button -> minecraft.setScreen(parent)).bounds(width / 2 - 80, height - 25, 160, 20).build());
    }

    private void give(CustomItemEntry entry) {
        if (minecraft.level != null && ClientServerConfigNetworking.canManageServer()) {
            minecraft.getConnection().sendCommand("customrecipe_item give " + entry.id);
        }
    }

    private void delete(CustomItemEntry entry) {
        parent.customItems.remove(entry);
        int maxPage = Math.max(0, (parent.customItems.size() - 1) / pageSize());
        page = Math.min(page, maxPage);
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        int x = width / 2 - 170;
        int top = 52;
        int pageSize = pageSize();
        int start = page * pageSize;
        // Row backgrounds must be queued before widgets; otherwise their tint
        // is painted over the buttons and makes active controls look disabled.
        for (int i = start; i < Math.min(parent.customItems.size(), start + pageSize); i++) {
            int rowY = top + (i - start) * 28;
            context.fill(x, rowY, x + 340, rowY + 26, 0xAA202630);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        context.centeredText(font, Component.translatable("customrecipe.custom_items.command_hint"), width / 2, 33, 0xFFAAAAAA);
        for (int i = start; i < Math.min(parent.customItems.size(), start + pageSize); i++) {
            CustomItemEntry entry = parent.customItems.get(i);
            int rowY = top + (i - start) * 28;
            ItemStack stack = ClientItemStacks.fromReference(CustomItemLibrary.reference(entry.id), parent.rootConfig());
            if (!stack.isEmpty()) context.item(stack, x + 5, rowY + 5);
            context.text(font, entry.name, x + 26, rowY + 4, 0xFFFFFFFF, false);
            context.text(font, entry.id + "  •  " + entry.item_id, x + 26, rowY + 15, 0xFF999999, false);
        }
        int pages = Math.max(1, (parent.customItems.size() + pageSize - 1) / pageSize);
        context.centeredText(font, Component.literal((page + 1) + " / " + pages), width / 2, height - 44, 0xFFCCCCCC);
        if (parent.customItems.isEmpty()) {
            context.centeredText(font, Component.translatable("customrecipe.custom_items.empty"), width / 2, height / 2, 0xFFCCCCCC);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private int pageSize() { return Math.max(1, (height - 112) / 28); }
}
