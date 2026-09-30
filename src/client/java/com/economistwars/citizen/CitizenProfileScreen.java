package com.economistwars.citizen;

import com.economistwars.network.CitizenProfilePayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
    private final Map<String, Boolean> expandedDecisions = new HashMap<>();
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
        int contentHeight = tab == Tab.INVENTORY ? 150 : contentHeight(contentWidth, fields);
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
        if (tab == Tab.INVENTORY) {
            drawInventory(graphics, textX, viewportTop + 4 - scrollOffset, mouseX, mouseY);
        } else for (ProfileField field : fields) {
            graphics.text(font, field.label(), textX, textY,
                    field.collapsibleKey().isEmpty() ? 0xFFB8C0C9 : 0xFFFFE6B0, false);
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

    private void drawInventory(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("screen.economistwars.citizen.inventory.items"), x, y, 0xFFB8C0C9, false);
        int gridY = y + 14;
        List<ItemStack> items = profile.inventory();
        for (int slot = 0; slot < 36; slot++) {
            int row = slot < 27 ? slot / 9 : 4;
            int column = slot < 27 ? slot % 9 : slot - 27;
            int slotX = x + column * 18;
            int slotY = gridY + row * 18;
            drawSlot(graphics, items.size() > slot ? items.get(slot) : ItemStack.EMPTY, slotX, slotY, mouseX, mouseY);
        }
        int toolY = gridY + 5 * 18 + 5;
        graphics.text(font, Component.translatable("screen.economistwars.citizen.inventory.equipped"), x, toolY + 4, 0xFFB8C0C9, false);
        drawSlot(graphics, profile.equippedStack(), x + 103, toolY, mouseX, mouseY);
    }

    private void drawSlot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080A0C);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF373737);
        if (!stack.isEmpty()) {
            graphics.item(stack, x + 1, y + 1);
            graphics.itemDecorations(font, stack, x + 1, y + 1);
        }
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18 && !stack.isEmpty()) {
            graphics.setTooltipForNextFrame(font, List.of(stack.getHoverName()), java.util.Optional.empty(), mouseX, mouseY, null);
        }
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int left, int top, int panelWidth) {
        for (Tab value : Tab.values()) {
            int tabLeft = left + panelWidth * value.ordinal() / Tab.values().length;
            int tabRight = left + panelWidth * (value.ordinal() + 1) / Tab.values().length;
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
                int selected = Math.min(Tab.values().length - 1,
                        (int) ((event.x() - left) * Tab.values().length / panelWidth));
                tab = Tab.values()[selected];
                scrollOffset = 0;
                return true;
            }
            if (tab == Tab.DECISIONS) {
                int viewportTop = tabTop + TAB_HEIGHT + 6;
                int viewportBottom = height - PANEL_MARGIN - FOOTER_PADDING;
                int contentWidth = panelWidth - PANEL_PADDING * 2;
                int rowY = viewportTop + 4 - scrollOffset;
                for (ProfileField field : fields()) {
                    int rowHeight = fieldHeight(field, contentWidth);
                    if (!field.collapsibleKey().isEmpty()
                            && event.x() >= left + PANEL_PADDING && event.x() <= left + panelWidth - PANEL_PADDING
                            && event.y() >= viewportTop && event.y() < viewportBottom
                            && event.y() >= rowY && event.y() < rowY + rowHeight - 8) {
                        expandedDecisions.put(field.collapsibleKey(),
                                !expandedDecisions.getOrDefault(field.collapsibleKey(), false));
                        return true;
                    }
                    rowY += rowHeight;
                }
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
            height += fieldHeight(field, contentWidth);
        }
        return height;
    }

    private int fieldHeight(ProfileField field, int contentWidth) {
        int height = 12 + Math.max(1, font.split(field.value(), contentWidth).size()) * 10 + 8;
        return height + (field.progressPercent() >= 0 ? 13 : 0);
    }

    private List<ProfileField> fields() {
        Component householdId = profile.householdId().isBlank()
                ? Component.translatable("screen.economistwars.citizen.unassigned")
                : Component.literal(profile.householdId());
        String normalizedSex = profile.sex().toLowerCase(Locale.ROOT);
        return switch (tab) {
            case OVERVIEW -> overviewFields(normalizedSex, householdId);
            case DECISIONS -> decisionFields();
            case HOUSEHOLD -> List.of(
                    new ProfileField(Component.translatable("screen.economistwars.citizen.food"), Component.literal(Integer.toString(profile.householdFood()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.food_need"), Component.literal(Integer.toString(profile.householdSize()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.shortage"), Component.literal(Integer.toString(profile.foodShortage()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.coins"), Component.literal(Integer.toString(profile.householdCoins()))),
                    new ProfileField(Component.translatable("screen.economistwars.citizen.parcels"), Component.literal(Integer.toString(profile.ownedParcels())))
            );
            case SKILLS -> profile.skills().stream().map(this::skillField).toList();
            case INVENTORY -> List.of();
        };
    }

    private List<ProfileField> overviewFields(String normalizedSex, Component householdId) {
        ArrayList<ProfileField> fields = new ArrayList<>(List.of(
                new ProfileField(Component.translatable("screen.economistwars.citizen.name"), Component.literal(profile.name())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.sex"), Component.translatable("citizen.economistwars.sex." + normalizedSex)),
                new ProfileField(Component.translatable("screen.economistwars.citizen.current_action"),
                        Component.translatable("screen.economistwars.citizen.action." + profile.currentDecision())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.decision_score"),
                        Component.literal(String.format(Locale.ROOT, "%.4f utility/tick%s", profile.decisionScore(),
                                profile.decisionScore() >= 900.0 ? " (priority override)" : ""))),
                new ProfileField(Component.translatable("screen.economistwars.citizen.needs"), Component.literal(String.format(
                        Locale.ROOT, "Eat %d/100  •  Entertainment %d/100  •  Safety %d/100",
                        profile.eatNeed(), profile.entertainmentNeed(), profile.safetyNeed()))),
                new ProfileField(Component.translatable("screen.economistwars.citizen.id"), Component.literal(profile.citizenId())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.skin"), Component.literal(profile.skinId())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.household"), householdId),
                new ProfileField(Component.translatable("screen.economistwars.citizen.household_size"), Component.literal(Integer.toString(profile.householdSize())))
        ));
        if (profile.decisionDurationTicks() > 0) {
            int progressPercent = (int) ((long) profile.decisionProgressTicks() * 100 / profile.decisionDurationTicks());
            fields.add(3, new ProfileField(Component.translatable("screen.economistwars.citizen.action_progress"),
                    Component.literal(String.format(Locale.ROOT, "%d / %d ticks  •  %d%%",
                            profile.decisionProgressTicks(), profile.decisionDurationTicks(), progressPercent)),
                    progressPercent));
        }
        if (profile.decisionDurationTicks() > 0) {
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.action_duration"),
                    Component.literal(String.format(Locale.ROOT, "%d ticks (%.1f seconds)",
                            profile.decisionDurationTicks(), profile.decisionDurationTicks() / 20.0))));
        }
        if (profile.estimatedWorkTicks() > 0) {
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.work_estimate"),
                    Component.literal(String.format(Locale.ROOT, "×%.2f speed  •  %d work ticks",
                            profile.workSpeedMultiplier(), profile.estimatedWorkTicks()))));
        }
        if (profile.hasRememberedMarket()) {
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.remembered_trade"),
                    Component.literal(profile.marketMemoryReceived().getHoverName().getString() + " for "
                            + profile.marketMemoryRequested().getHoverName().getString())));
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.trade_gain"),
                    Component.literal(String.format(Locale.ROOT, "%+.2f utility", profile.rememberedTradeGain()))));
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.last_market"),
                    Component.literal(String.format(Locale.ROOT, "%d, %d, %d", profile.rememberedMarketX(),
                            profile.rememberedMarketY(), profile.rememberedMarketZ()))));
        } else {
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.remembered_trade"),
                    Component.translatable("screen.economistwars.citizen.no_remembered_trade")));
        }
        return List.copyOf(fields);
    }

    private List<ProfileField> decisionFields() {
        ArrayList<ProfileField> fields = new ArrayList<>();
        for (CitizenProfilePayload.DecisionScore decision : profile.decisionScores()) {
            String key = decision.action();
            boolean expanded = expandedDecisions.getOrDefault(key, false);
            String status = decision.selected()
                    ? Component.translatable("screen.economistwars.citizen.decision.selected").getString()
                    : decision.eligible()
                            ? Component.translatable("screen.economistwars.citizen.decision.available").getString()
                            : Component.translatable("screen.economistwars.citizen.decision.unavailable").getString();
            Component title = Component.literal(expanded ? "[-] " : "[+] ")
                    .append(Component.translatable("screen.economistwars.citizen.action." + key));
            fields.add(new ProfileField(title,
                    Component.literal(String.format(Locale.ROOT, "%s  •  %.4f utility/tick", status, decision.score())),
                    -1, key));
            if (expanded) {
                for (CitizenProfilePayload.DecisionDetail detail : decision.details()) {
                    fields.add(new ProfileField(Component.literal("  " + detail.label()),
                            Component.literal(detail.value())));
                }
            }
        }
        if (fields.isEmpty()) {
            fields.add(new ProfileField(Component.translatable("screen.economistwars.citizen.decisions_unavailable"),
                    Component.empty()));
        }
        return List.copyOf(fields);
    }

    private ProfileField skillField(CitizenProfilePayload.SkillProgress skill) {
        int experience = Math.max(0, skill.experience());
        int level = Math.min(100, experience / 100);
        return new ProfileField(
                Component.translatable("screen.economistwars.citizen.skill." + skill.name()),
                Component.literal("Level " + level + "  •  " + experience + " XP"),
                level >= 100 ? 100 : experience % 100);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        OVERVIEW("screen.economistwars.citizen.tab.overview"),
        DECISIONS("screen.economistwars.citizen.tab.decisions"),
        HOUSEHOLD("screen.economistwars.citizen.tab.household"),
        SKILLS("screen.economistwars.citizen.tab.skills"),
        INVENTORY("screen.economistwars.citizen.tab.inventory");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private record ProfileField(Component label, Component value, int progressPercent, String collapsibleKey) {
        private ProfileField(Component label, Component value) {
            this(label, value, -1, "");
        }
        private ProfileField(Component label, Component value, int progressPercent) {
            this(label, value, progressPercent, "");
        }
    }
}
