package com.economistwars.citizen;

import com.mojang.blaze3d.platform.InputConstants;
import com.economistwars.network.SettlementMarketPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Read-only view of the settlement's live offers and completed barter history. */
public final class SettlementMarketScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 48;
    private static final int TOP = 18;
    private static final int BOTTOM = 18;
    private static final int TAB_HEIGHT = 24;

    private final SettlementMarketPayload market;
    private Tab tab = Tab.CURRENT;
    private int scrollOffset;

    public SettlementMarketScreen(SettlementMarketPayload market) {
        super(Component.translatable("screen.economistwars.market.title"));
        this.market = market;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 24);
        int left = (width - panelWidth) / 2;
        int bottom = height - BOTTOM;
        int tabTop = TOP + 28;
        int viewportTop = tabTop + TAB_HEIGHT + 7;
        int viewportBottom = bottom - 8;
        List<SettlementMarketPayload.Offer> offers = market.offers();
        List<SettlementMarketPayload.Trade> history = market.history();
        int entryCount = tab == Tab.CURRENT ? offers.size() : history.size();
        int contentHeight = Math.max(42, entryCount * ROW_HEIGHT);
        int viewportHeight = Math.max(0, viewportBottom - viewportTop);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);

        graphics.fill(left, TOP, left + panelWidth, bottom, 0xF021252B);
        graphics.fill(left, TOP, left + panelWidth, TOP + 2, 0xFFB68A45);
        graphics.centeredText(font, title, width / 2, TOP + 9, 0xFFFFE6B0);
        drawTabs(graphics, left, tabTop, panelWidth);
        graphics.enableScissor(left + 8, viewportTop, left + panelWidth - 8, viewportBottom);
        if (entryCount == 0) {
            graphics.centeredText(font, Component.translatable(tab.emptyKey), width / 2,
                    viewportTop + 12, 0xFFB8C0C9);
        } else if (tab == Tab.CURRENT) {
            for (int index = 0; index < offers.size(); index++) {
                drawOffer(graphics, left + 12, viewportTop + index * ROW_HEIGHT - scrollOffset,
                        panelWidth - 24, offers.get(index), mouseX, mouseY);
            }
        } else {
            for (int index = 0; index < history.size(); index++) {
                drawTrade(graphics, left + 12, viewportTop + index * ROW_HEIGHT - scrollOffset,
                        panelWidth - 24, history.get(index), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int trackX = left + panelWidth - 5;
            int thumbHeight = Math.max(14, viewportHeight * viewportHeight / contentHeight);
            int thumbTop = viewportTop + (viewportHeight - thumbHeight) * scrollOffset / maxScroll;
            graphics.fill(trackX, viewportTop, trackX + 2, viewportBottom, 0xFF4B535C);
            graphics.fill(trackX, thumbTop, trackX + 2, thumbTop + thumbHeight, 0xFFD1A15D);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawOffer(GuiGraphicsExtractor graphics, int x, int y, int rowWidth,
                           SettlementMarketPayload.Offer offer, int mouseX, int mouseY) {
        graphics.fill(x, y, x + rowWidth, y + ROW_HEIGHT - 3, 0xFF2D333A);
        graphics.text(font, Component.translatable("screen.economistwars.market.seller", shortId(offer.householdId())),
                x + 6, y + 4, 0xFFB8C0C9, false);
        drawItem(graphics, offer.offered(), x + 6, y + 20, mouseX, mouseY);
        graphics.text(font, Component.translatable("screen.economistwars.market.for"), x + 29, y + 25,
                0xFFE0E5EA, false);
        drawItem(graphics, offer.requested(), x + 69, y + 20, mouseX, mouseY);
    }

    private void drawTrade(GuiGraphicsExtractor graphics, int x, int y, int rowWidth,
                           SettlementMarketPayload.Trade trade, int mouseX, int mouseY) {
        graphics.fill(x, y, x + rowWidth, y + ROW_HEIGHT - 3, 0xFF2D333A);
        long day = Math.floorDiv(trade.gameTime(), 24000L) + 1;
        graphics.text(font, Component.translatable("screen.economistwars.market.trade_day", day,
                        shortId(trade.sellerId()), shortId(trade.buyerId())),
                x + 6, y + 4, 0xFFB8C0C9, false);
        drawItem(graphics, trade.sellerItem(), x + 6, y + 21, mouseX, mouseY);
        graphics.text(font, Component.translatable("screen.economistwars.market.for"), x + 29, y + 26,
                0xFFE0E5EA, false);
        drawItem(graphics, trade.buyerItem(), x + 69, y + 21, mouseX, mouseY);
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack item, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080A0C);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        graphics.item(item, x + 1, y + 1);
        graphics.itemDecorations(font, item, x + 1, y + 1);
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
            graphics.setTooltipForNextFrame(font, item, mouseX, mouseY);
        }
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int left, int top, int panelWidth) {
        for (Tab value : Tab.values()) {
            int tabLeft = left + value.ordinal() * panelWidth / Tab.values().length;
            int tabRight = left + (value.ordinal() + 1) * panelWidth / Tab.values().length;
            int color = value == tab ? 0xFFD1A15D : 0xFF4B535C;
            graphics.fill(tabLeft, top, tabRight - 2, top + TAB_HEIGHT, color);
            graphics.centeredText(font, Component.translatable(value.titleKey),
                    (tabLeft + tabRight) / 2, top + 8, value == tab ? 0xFF20252B : 0xFFE0E5EA);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int panelWidth = Math.min(PANEL_WIDTH, width - 24);
            int left = (width - panelWidth) / 2;
            int tabTop = TOP + 28;
            if (event.y() >= tabTop && event.y() < tabTop + TAB_HEIGHT
                    && event.x() >= left && event.x() < left + panelWidth) {
                int selected = Math.clamp((int) ((event.x() - left) * Tab.values().length / panelWidth),
                        0, Tab.values().length - 1);
                tab = Tab.values()[selected];
                scrollOffset = 0;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            scrollOffset -= (int) Math.round(scrollY * 18);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_UP || event.key() == InputConstants.KEY_DOWN) {
            scrollOffset += event.key() == InputConstants.KEY_UP ? -18 : 18;
            return true;
        }
        return super.keyPressed(event);
    }

    private String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        CURRENT("screen.economistwars.market.tab.current", "screen.economistwars.market.empty_current"),
        HISTORY("screen.economistwars.market.tab.history", "screen.economistwars.market.empty_history");

        private final String titleKey;
        private final String emptyKey;

        Tab(String titleKey, String emptyKey) {
            this.titleKey = titleKey;
            this.emptyKey = emptyKey;
        }
    }
}
