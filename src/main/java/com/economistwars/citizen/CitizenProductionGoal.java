package com.economistwars.citizen;

import com.economistwars.network.CitizenProfilePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.SwingAnimation;

/** Shared need-, market-, skill-, and time-based evaluation for citizen production actions. */
abstract class CitizenProductionGoal extends Goal implements CitizenDecisionAction {
    protected final CitizenEntity citizen;
    private Evaluation evaluationSnapshot;
    private int productionWorkTicks;

    protected CitizenProductionGoal(CitizenEntity citizen) {
        this.citizen = citizen;
    }

    protected abstract List<CitizenDecisionPlanner.OutputOutcome> productionOutcomes();
    protected List<InputCost> consumedInputs() { return List.of(); }
    protected abstract CitizenSkill productionSkill();
    protected abstract int baseWorkTicks();
    protected abstract int scoreTravelTicks();
    protected int durationTravelTicks() { return scoreTravelTicks(); }
    protected abstract boolean scheduledWork();
    protected abstract List<CitizenProfilePayload.DecisionDetail> outcomeDetails(Evaluation evaluation);

    /** Advances the shared timed production cycle and reports when it is ready to complete. */
    protected final boolean tickProductionWork(InteractionHand hand) {
        productionWorkTicks++;
        if (productionWorkTicks % 10 == 0) citizen.swing(hand, SwingAnimation.DEFAULT);
        return productionWorkTicks >= CitizenDecisionPlanner.effectiveWorkTicks(
                baseWorkTicks(), citizen.skills().level(productionSkill()));
    }

    protected final void resetProductionWork() { productionWorkTicks = 0; }
    protected final void startProductionWork() { productionWorkTicks = 0; }

    protected final Evaluation evaluation() {
        if (evaluationSnapshot == null) prepareDecisionSnapshot();
        return evaluationSnapshot;
    }

    @Override public final void prepareDecisionSnapshot() {
        evaluationSnapshot = evaluate(citizen.needs(), citizen.marketMemory(), productionOutcomes(), consumedInputs(),
                citizen.skills().level(productionSkill()), baseWorkTicks(), scoreTravelTicks(), scheduledWork());
    }

    static Evaluation evaluate(CitizenNeeds needs, CitizenMarketMemory memory,
            List<CitizenDecisionPlanner.OutputOutcome> outcomes, List<InputCost> inputs,
            int skillLevel, int baseWorkTicks, int travelTicks, boolean scheduled) {
        Item received = memory == null ? null : memory.received();
        Item requested = memory == null ? null : memory.requested();
        double outputUtility = CitizenDecisionPlanner.expectedOutputUtility(outcomes, received, requested, needs);
        double inputUtility = inputs.stream().mapToDouble(input -> input.quantity()
                * ItemNeedValues.forItem(input.item()).utility(needs)).sum();
        double netUtility = outputUtility - inputUtility;
        int clampedSkill = Math.clamp(skillLevel, 0, 100);
        double speed = CitizenDecisionPlanner.skillSpeed(clampedSkill);
        int workTicks = CitizenDecisionPlanner.effectiveWorkTicks(baseWorkTicks, clampedSkill);
        double scheduleMultiplier = scheduled ? 1.05 : 1.0;
        double score = CitizenDecisionPlanner.score(netUtility, baseWorkTicks, clampedSkill,
                travelTicks, scheduled);
        return new Evaluation(outputUtility, inputUtility, netUtility, clampedSkill, speed, workTicks,
                Math.max(0, travelTicks), scheduleMultiplier, score);
    }

    @Override public final double decisionScore() { return evaluation().score(); }

    @Override public final int estimatedDurationTicks() {
        return Math.max(1, durationTravelTicks()
                + CitizenDecisionPlanner.effectiveWorkTicks(baseWorkTicks(), citizen.skills().level(productionSkill())));
    }

    @Override public final List<CitizenProfilePayload.DecisionDetail> decisionDetails() {
        Evaluation result = evaluation();
        ArrayList<CitizenProfilePayload.DecisionDetail> details = new ArrayList<>(outcomeDetails(result));
        details.add(new CitizenProfilePayload.DecisionDetail("Expected output utility", format(result.outputUtility())));
        if (!consumedInputs().isEmpty()) {
            details.add(new CitizenProfilePayload.DecisionDetail("Consumed input utility", format(result.inputUtility())));
            details.add(new CitizenProfilePayload.DecisionDetail("Net utility", format(result.netUtility())));
        }
        String skillName = productionSkill().serializedName();
        skillName = Character.toUpperCase(skillName.charAt(0)) + skillName.substring(1);
        details.add(new CitizenProfilePayload.DecisionDetail(skillName + " skill",
                result.skillLevel() + " (speed ×" + format(result.skillSpeed()) + ")"));
        details.add(new CitizenProfilePayload.DecisionDetail("Base / effective work",
                baseWorkTicks() + " / " + result.workTicks() + " ticks"));
        details.add(new CitizenProfilePayload.DecisionDetail("Travel ticks", Integer.toString(result.scoreTravelTicks())));
        details.add(new CitizenProfilePayload.DecisionDetail("Scheduled work", Boolean.toString(scheduledWork())));
        details.add(new CitizenProfilePayload.DecisionDetail("Schedule multiplier", "×" + format(result.scheduleMultiplier())));
        details.add(new CitizenProfilePayload.DecisionDetail("Score formula", String.format(Locale.ROOT,
                "max(0, %.4f) ÷ (%d + %d) × %.2f = %.4f utility/tick", result.netUtility(),
                result.workTicks(), result.scoreTravelTicks(), result.scheduleMultiplier(), result.score())));
        return List.copyOf(details);
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.4f", value); }

    record InputCost(Item item, int quantity) {}

    record Evaluation(double outputUtility, double inputUtility, double netUtility, int skillLevel,
            double skillSpeed, int workTicks, int scoreTravelTicks, double scheduleMultiplier, double score) {}
}
