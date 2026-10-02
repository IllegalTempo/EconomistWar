package com.economistwars.network;

import com.economistwars.EconomistWars;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record CitizenProfilePayload(
        String name,
        String sex,
        String citizenId,
        String skinId,
        String householdId,
        int householdSize,
        int householdFood,
        int foodShortage,
        int householdCoins,
        int ownedParcels,
        String currentDecision,
        double decisionScore,
        List<DecisionScore> decisionScores,
        int decisionProgressTicks,
        int decisionDurationTicks,
        int eatNeed,
        int entertainmentNeed,
        int safetyNeed,
        List<NeedProgress> needs,
        double workSpeedMultiplier,
        int estimatedWorkTicks,
        ItemStack marketMemoryReceived,
        ItemStack marketMemoryRequested,
        boolean hasRememberedMarket,
        int rememberedMarketX,
        int rememberedMarketY,
        int rememberedMarketZ,
        double rememberedTradeGain,
        List<SkillProgress> skills,
        List<ItemStack> inventory,
        ItemStack equippedStack
) implements CustomPacketPayload {
    public static final Type<CitizenProfilePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "citizen_profile")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenProfilePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.name());
                buffer.writeUtf(payload.sex());
                buffer.writeUtf(payload.citizenId());
                buffer.writeUtf(payload.skinId());
                buffer.writeUtf(payload.householdId());
                buffer.writeVarInt(payload.householdSize());
                buffer.writeVarInt(payload.householdFood());
                buffer.writeVarInt(payload.foodShortage());
                buffer.writeVarInt(payload.householdCoins());
                buffer.writeVarInt(payload.ownedParcels());
                buffer.writeUtf(payload.currentDecision());
                buffer.writeDouble(payload.decisionScore());
                buffer.writeVarInt(payload.decisionScores().size());
                for (DecisionScore score : payload.decisionScores()) {
                    buffer.writeUtf(score.action());
                    buffer.writeBoolean(score.eligible());
                    buffer.writeBoolean(score.selected());
                    buffer.writeDouble(score.score());
                    buffer.writeVarInt(score.details().size());
                    for (DecisionDetail detail : score.details()) {
                        buffer.writeUtf(detail.label());
                        buffer.writeUtf(detail.value());
                    }
                }
                buffer.writeVarInt(payload.decisionProgressTicks());
                buffer.writeVarInt(payload.decisionDurationTicks());
                buffer.writeVarInt(payload.eatNeed());
                buffer.writeVarInt(payload.entertainmentNeed());
                buffer.writeVarInt(payload.safetyNeed());
                buffer.writeVarInt(payload.needs().size());
                for (NeedProgress need : payload.needs()) {
                    buffer.writeUtf(need.name());
                    buffer.writeVarInt(need.urgency());
                }
                buffer.writeDouble(payload.workSpeedMultiplier());
                buffer.writeVarInt(payload.estimatedWorkTicks());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.marketMemoryReceived());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.marketMemoryRequested());
                buffer.writeBoolean(payload.hasRememberedMarket());
                buffer.writeVarInt(payload.rememberedMarketX());
                buffer.writeVarInt(payload.rememberedMarketY());
                buffer.writeVarInt(payload.rememberedMarketZ());
                buffer.writeDouble(payload.rememberedTradeGain());
                buffer.writeVarInt(payload.skills().size());
                for (SkillProgress skill : payload.skills()) {
                    buffer.writeUtf(skill.name());
                    buffer.writeVarInt(skill.experience());
                }
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buffer, payload.inventory());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.equippedStack());
            },
            buffer -> new CitizenProfilePayload(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(),
                    buffer.readDouble(),
                    readDecisionScores(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    readNeeds(buffer),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    readSkills(buffer),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buffer),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static List<SkillProgress> readSkills(RegistryFriendlyByteBuf buffer) {
        int count = Math.clamp(buffer.readVarInt(), 0, 64);
        List<SkillProgress> skills = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            skills.add(new SkillProgress(buffer.readUtf(), buffer.readVarInt()));
        }
        return skills;
    }

    private static List<NeedProgress> readNeeds(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > buffer.readableBytes() / 2) {
            throw new IllegalArgumentException("Invalid citizen need count: " + count);
        }
        List<NeedProgress> needs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            needs.add(new NeedProgress(buffer.readUtf(), buffer.readVarInt()));
        }
        return needs;
    }

    private static List<DecisionScore> readDecisionScores(RegistryFriendlyByteBuf buffer) {
        int count = Math.clamp(buffer.readVarInt(), 0, 32);
        List<DecisionScore> scores = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String action = buffer.readUtf();
            boolean eligible = buffer.readBoolean();
            boolean selected = buffer.readBoolean();
            double score = buffer.readDouble();
            int detailCount = Math.clamp(buffer.readVarInt(), 0, 64);
            List<DecisionDetail> details = new ArrayList<>(detailCount);
            for (int detail = 0; detail < detailCount; detail++) {
                details.add(new DecisionDetail(buffer.readUtf(), buffer.readUtf()));
            }
            scores.add(new DecisionScore(action, eligible, selected, score, details));
        }
        return scores;
    }

    public record SkillProgress(String name, int experience) {}
    public record NeedProgress(String name, int urgency) {}
    public record DecisionScore(String action, boolean eligible, boolean selected, double score,
            List<DecisionDetail> details) {}
    public record DecisionDetail(String label, String value) {}
}
