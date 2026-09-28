package com.economistwars.citizen;

import com.economistwars.network.CitizenProfilePayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class CitizenProfileScreen extends Screen {
    private static final int PANEL_MARGIN = 8;
    private static final int PANEL_WIDTH_MIN = 220;
    private static final int PANEL_WIDTH_MAX = 420;
    private static final int PANEL_PADDING = 16;
    private static final int TITLE_HEIGHT = 24;
    private static final int TAB_HEIGHT = 22;
    private static final int FOOTER_PADDING = 10;
    private static final int SCROLL_STEP = 18;

    private final CitizenProfilePayload profile;
    private Tab tab = Tab.OVERVIEW;
    private int scrollOffset;

    public CitizenProfileScreen(CitizenProfilePayload profile) {
        super(Component.translatable("screen.economistwars.citizen.title"));
        this.profile = profile;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);

        int panelWidth = Math.min(PANEL_WIDTH_MAX, Math.max(PANEL_WIDTH_MIN, width - 24));
        int left = (width - panelWidth) / 2;
        int top = PANEL_MARGIN;
        int bottom = height - PANEL_MARGIN;
        int tabTop = top + TITLE_HEIGHT;
        int viewportTop = tabTop + TAB_HEIGHT + 6;
        int viewportBottom = bottom - FOOTER_PADDING;
        int contentWidth = panelWidth - PANEL_PADDING * 2;
        List<ProfileField> fields = fields();
        int contentHeight = contentHeight(contentWidth, fields);
        int viewportHeight = Math.max(0, viewportBottom - viewportTop);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);

        graphics.fill(left, top, left + panelWidth, bottom, 0xF021252B);
        graphics.fill(left, top, left + panelWidth, top + 2, 0xFFB68A45);
        graphics.centeredText(font, title, width / 2, top + 8, 0xFFFFE6B0);

        drawTabs(graphics, left, tabTop, panelWidth);
        graphics.enableScissor(left, viewportTop, left + panelWidth, viewportBottom);
        int textX = left + PANEL_PADDING;
        int textY = viewportTop + 4 - scrollOffset;
        for (ProfileField field : fields) {
            graphics.text(font, field.label(), textX, textY, 0xFFB8C0C9, false);
            textY += 12;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(field.value(), contentWidth);
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                graphics.text(font, line, textX, textY, 0xFFFFFFFF, false);
                textY += 10;
            }
            if (lines.isEmpty()) {
                textY += 10;
            }
            if (field.progressPercent() >= 0) {
                int barTop = textY + 1;
                int barWidth = contentWidth;
                graphics.fill(textX, barTop, textX + barWidth, barTop + 5, 0xFF4B535C);
                graphics.fill(textX, barTop, textX + barWidth * field.progressPercent() / 100, barTop + 5, 0xFFD1A15D);
                textY += 13;
            }
            textY += 8;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = left + panelWidth - 6;
            int thumbHeight = Math.max(12, viewportHeight * viewportHeight / contentHeight);
            int thumbTravel = viewportHeight - thumbHeight;
            int thumbTop = viewportTop + (thumbTravel * scrollOffset / maxScroll);
            graphics.fill(trackX, viewportTop, trackX + 2, viewportBottom, 0xFF4B535C);
            graphics.fill(trackX, thumbTop, trackX + 2, thumbTop + thumbHeight, 0xFFD1A15D);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int left, int top, int panelWidth) {
        int tabWidth = panelWidth / Tab.values().length;
        for (Tab value : Tab.values()) {
            int tabLeft = left + value.ordinal() * tabWidth;
            int tabRight = value == Tab.SKILLS ? left + panelWidth : tabLeft + tabWidth;
            int color = value == tab ? 0xFFD1A15D : 0xFF4B535C;
            graphics.fill(tabLeft, top, tabRight - 2, top + TAB_HEIGHT, color);
            graphics.centeredText(font, Component.translatable(value.translationKey()), (tabLeft + tabRight) / 2, top + 7,
                    value == tab ? 0xFF20252B : 0xFFE0E5EA);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int panelWidth = Math.min(PANEL_WIDTH_MAX, Math.max(PANEL_WIDTH_MIN, width - 24));
            int left = (width - panelWidth) / 2;
            int tabTop = PANEL_MARGIN + TITLE_HEIGHT;
            if (event.y() >= tabTop && event.y() < tabTop + TAB_HEIGHT && event.x() >= left && event.x() < left + panelWidth) {
                int tabWidth = panelWidth / Tab.values().length;
                int selected = Math.min(Tab.values().length - 1, (int) ((event.x() - left) / tabWidth));
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
            scrollOffset -= (int) Math.round(scrollY * SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_UP) {
            scrollOffset -= SCROLL_STEP;
            return true;
        }
        if (event.key() == InputConstants.KEY_DOWN) {
            scrollOffset += SCROLL_STEP;
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEUP) {
            scrollOffset -= Math.max(SCROLL_STEP, height / 2);
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN) {
            scrollOffset += Math.max(SCROLL_STEP, height / 2);
            return true;
        }
        return super.keyPressed(event);
    }

    private int contentHeight(int contentWidth, List<ProfileField> fields) {
        int height = 4;
        for (ProfileField field : fields) {
            height += 12 + Math.max(1, font.split(field.value(), contentWidth).size()) * 10 + 8;
            if (field.progressPercent() >= 0) {
                height += 13;
            }
        }
        return height;
    }

    private List<ProfileField> fields() {
        Component householdId = profile.householdId().isBlank()
                ? Component.translatable("screen.economistwars.citizen.unassigned")
                : Component.literal(profile.householdId());
        String normalizedSex = profile.sex().toLowerCase(Locale.ROOT);
        return switch (tab) {
            case OVERVIEW -> List.of(
                    new ProfileField(Component.translatable("screen.economistwars.citizen.name"), Component.literal(profile.name())),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.sex"), Component.translatable("citizen.economistwars.sex." + normalizedSex)),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.id"), Component.literal(profile.citizenId())),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.skin"), Component.literal(profile.skinId())),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.household"), householdId),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.household_size"), Component.literal(Integer.toString(profile.householdSize())))
            );
            case HOUSEHOLD -> List.of(
                    new ProfileField(Component.translatable("screen.economistwars.citizen.food"), Component.literal(Integer.toString(profile.householdFood()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.food_need"), Component.literal(Integer.toString(profile.householdSize()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.shortage"), Component.literal(Integer.toString(profile.foodShortage()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.coins"), Component.literal(Integer.toString(profile.householdCoins()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.parcels"), Component.literal(Integer.toString(profile.ownedParcels())))
            );
            case SKILLS -> List.of(
                    skillField("screen.economistwars.citizen.farming", profile.farmingLevel(), profile.farmingExperience()),
                    skillField("screen.economistwars.citizen.mining", profile.miningLevel(), profile.miningExperience()),
                    skillField("screen.economistwars.citizen.building", profile.buildingLevel(), profile.buildingExperience())
            );
        };
    }

    private ProfileField skillField(String labelKey, int level, int experience) {
        return new ProfileField(Component.translatable(labelKey), Component.literal("Level " + level + "  •  " + experience + " XP"),
                level >= 100 ? 100 : experience % 100);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        OVERVIEW("screen.economistwars.citizen.tab.overview"),
        HOUSEHOLD("screen.economistwars.citizen.tab.household"),
        SKILLS("screen.economistwars.citizen.tab.skills");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private record ProfileField(Component label, Component value, int progressPercent) {
        private ProfileField(Component label, Component value) {
            this(label, value, -1);
        }
    }
}
