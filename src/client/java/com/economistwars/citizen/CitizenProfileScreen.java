package com.economistwars.citizen;

import com.economistwars.network.CitizenProfilePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CitizenProfileScreen extends Screen {
    private final CitizenProfilePayload profile;

    public CitizenProfileScreen(CitizenProfilePayload profile) {
        super(Component.translatable("screen.economistwars.citizen.title"));
        this.profile = profile;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);

        int panelWidth = Math.min(420, Math.max(220, width - 24));
        int left = (width - panelWidth) / 2;
        int contentWidth = panelWidth - 32;
        List<ProfileField> fields = fields();
        int panelHeight = 38;
        for (ProfileField field : fields) {
            panelHeight += 14 + Math.max(1, font.split(field.value(), contentWidth).size()) * 10 + 8;
        }
        int top = Math.max(8, (height - panelHeight) / 2);

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF021252B);
        graphics.fill(left, top, left + panelWidth, top + 2, 0xFFB68A45);
        graphics.centeredText(font, title, width / 2, top + 12, 0xFFFFE6B0);

        int textX = left + 16;
        int textY = top + 32;
        for (ProfileField field : fields) {
            graphics.text(font, field.label(), textX, textY, 0xFFB8C0C9, false);
            textY += 12;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(field.value(), contentWidth);
            if (lines.isEmpty()) {
                graphics.text(font, Component.empty(), textX, textY, 0xFFFFFFFF, false);
                textY += 10;
            } else {
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    graphics.text(font, line, textX, textY, 0xFFFFFFFF, false);
                    textY += 10;
                }
            }
            textY += 8;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private List<ProfileField> fields() {
        Component householdId = profile.householdId().isBlank()
                ? Component.translatable("screen.economistwars.citizen.unassigned")
                : Component.literal(profile.householdId());
        String normalizedSex = profile.sex().toLowerCase(java.util.Locale.ROOT);
        return List.of(
                new ProfileField(Component.translatable("screen.economistwars.citizen.name"), Component.literal(profile.name())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.sex"), Component.translatable("citizen.economistwars.sex." + normalizedSex)),
                new ProfileField(Component.translatable("screen.economistwars.citizen.id"), Component.literal(profile.citizenId())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.skin"), Component.literal(profile.skinId())),
                new ProfileField(Component.translatable("screen.economistwars.citizen.household"), householdId),
                new ProfileField(Component.translatable("screen.economistwars.citizen.household_size"), Component.literal(Integer.toString(profile.householdSize())))
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ProfileField(Component label, Component value) {}
}
