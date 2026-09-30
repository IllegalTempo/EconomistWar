package com.economistwars.citizen;

import java.util.EnumSet;
import java.util.List;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/** Bakes one loaf from three carried wheat after one minute of active work. */
final class CitizenBakeryGoal extends Goal implements CitizenDecisionAction {
    private static final int BAKE_TICKS = 60 * 20;

    private final CitizenEntity citizen;
    private int workTicks;

    CitizenBakeryGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.BAKE; }
    @Override public int estimatedDurationTicks() {
        return CitizenDecisionPlanner.effectiveWorkTicks(BAKE_TICKS, citizen.skills().level(CitizenSkill.BAKERY));
    }
    @Override public double decisionScore() {
        CitizenMarketMemory memory = citizen.marketMemory();
        double output = CitizenDecisionPlanner.expectedOutputUtility(List.of(
                        new CitizenDecisionPlanner.OutputOutcome(net.minecraft.world.item.Items.BREAD, 1, 1.0, true)),
                memory == null ? null : memory.received(), memory == null ? null : memory.requested(), citizen.needs());
        double input = 3 * ItemNeedValues.forItem(net.minecraft.world.item.Items.WHEAT).utility(citizen.needs());
        return CitizenDecisionPlanner.score(output - input, BAKE_TICKS,
                citizen.skills().level(CitizenSkill.BAKERY), 0, false);
    }
    @Override public List<CitizenProfilePayload.DecisionDetail> decisionDetails() {
        CitizenMarketMemory memory = citizen.marketMemory();
        int skill = citizen.skills().level(CitizenSkill.BAKERY);
        double output = CitizenDecisionPlanner.expectedOutputUtility(List.of(
                        new CitizenDecisionPlanner.OutputOutcome(net.minecraft.world.item.Items.BREAD, 1, 1.0, true)),
                memory == null ? null : memory.received(), memory == null ? null : memory.requested(), citizen.needs());
        double input = 3 * ItemNeedValues.forItem(net.minecraft.world.item.Items.WHEAT).utility(citizen.needs());
        int workTicks = CitizenDecisionPlanner.effectiveWorkTicks(BAKE_TICKS, skill);
        return List.of(
                new CitizenProfilePayload.DecisionDetail("Output", "1 bread; utility " + String.format(java.util.Locale.ROOT, "%.4f", output)),
                new CitizenProfilePayload.DecisionDetail("Consumed input", "3 wheat; utility cost " + String.format(java.util.Locale.ROOT, "%.4f", input)),
                new CitizenProfilePayload.DecisionDetail("Net utility", String.format(java.util.Locale.ROOT, "%.4f − %.4f = %.4f", output, input, output - input)),
                new CitizenProfilePayload.DecisionDetail("Bakery skill", skill + " (speed ×" + String.format(java.util.Locale.ROOT, "%.4f", CitizenDecisionPlanner.skillSpeed(skill)) + ")"),
                new CitizenProfilePayload.DecisionDetail("Base / effective work", BAKE_TICKS + " / " + workTicks + " ticks"),
                new CitizenProfilePayload.DecisionDetail("Travel ticks", "0"),
                new CitizenProfilePayload.DecisionDetail("Schedule multiplier", "×1.00"),
                new CitizenProfilePayload.DecisionDetail("Score formula", String.format(java.util.Locale.ROOT, "max(0, %.4f − %.4f) ÷ %d = %.4f utility/tick", output, input, workTicks, decisionScore()))
        );
    }

    @Override
    public boolean canUse() {
        return citizen.level() instanceof ServerLevel
                && !citizen.level().isClientSide()
                && !citizen.isTeleporting()
                && citizen.canBakeBreadFromWheat();
    }

    @Override
    public boolean canContinueToUse() {
        return citizen.level() instanceof ServerLevel
                && !citizen.isTeleporting()
                && citizen.canBakeBreadFromWheat();
    }

    @Override
    public void tick() {
        if (++workTicks < CitizenDecisionPlanner.effectiveWorkTicks(
                BAKE_TICKS, citizen.skills().level(CitizenSkill.BAKERY))) return;
        if (citizen.bakeBreadFromWheat()) {
            citizen.addSkillExperience(CitizenSkill.BAKERY, 5);
            citizen.restartDecisionProgress(estimatedDurationTicks());
            workTicks = 0;
        }
    }
}
