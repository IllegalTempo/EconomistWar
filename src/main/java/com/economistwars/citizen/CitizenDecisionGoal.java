package com.economistwars.citizen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.world.entity.ai.goal.Goal;

/** Owns action selection and delegates execution to the existing citizen behavior goals. */
final class CitizenDecisionGoal extends Goal {
    private final CitizenEntity citizen;
    private final List<Goal> actions;
    private Goal selected;
    private boolean active;
    private int rescoreTicks;

    CitizenDecisionGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        this.actions = List.of(
                new CitizenSleepGoal(citizen),
                new CitizenStoreHarvestGoal(citizen),
                new CitizenMarketGoal(citizen),
                new CitizenBakeryGoal(citizen),
                new CitizenFarmGoal(citizen),
                new CitizenMiningGoal(citizen),
                new CitizenReturnHomeGoal(citizen));
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (active) return false;
        selected = select(null);
        return selected != null;
    }

    @Override
    public boolean canContinueToUse() {
        return active && selected != null && selected.canContinueToUse();
    }

    @Override
    public void start() {
        if (selected == null) return;
        active = true;
        rescoreTicks = CitizenDecisionPlanner.RESCORE_TICKS;
        selected.start();
        updateDisplayedDecision();
    }

    @Override
    public void tick() {
        if (!active || selected == null) return;
        if (!selected.canContinueToUse()) {
            selected.stop();
            selected = null;
            active = false;
            citizen.setCurrentDecision("idle");
            return;
        }
        if (--rescoreTicks <= 0) {
            Goal next = select(selected);
            rescoreTicks = CitizenDecisionPlanner.RESCORE_TICKS;
            if (next != null && next != selected) {
                selected.stop();
                selected = next;
                selected.start();
            }
            updateDisplayedDecision();
        }
        selected.tick();
        citizen.advanceDecisionProgress();
    }

    @Override
    public void stop() {
        if (active && selected != null) selected.stop();
        selected = null;
        active = false;
        rescoreTicks = 0;
        citizen.setCurrentDecision("idle");
    }

    private void updateDisplayedDecision() {
        CitizenDecisionAction action = selected instanceof CitizenDecisionAction selectedAction
                ? selectedAction : null;
        if (action == null) {
            citizen.setCurrentDecision("idle");
            return;
        }
        String key = switch (action.decisionAction()) {
            case SLEEP -> "sleep";
            case DELIVERY -> "delivery";
            case MARKET -> "market";
            case BAKE -> "bake";
            case FARM -> "farm";
            case MINE -> "mine";
            case RETURN_HOME -> "return_home";
        };
        int baseWorkTicks = switch (action.decisionAction()) {
            case BAKE -> 60 * 20;
            case FARM -> 12;
            case MINE -> 40;
            default -> 0;
        };
        CitizenSkill skill = switch (action.decisionAction()) {
            case BAKE -> CitizenSkill.BAKERY;
            case FARM -> CitizenSkill.FARMING;
            case MINE -> CitizenSkill.MINING;
            default -> null;
        };
        int skillLevel = skill == null ? 0 : citizen.skills().level(skill);
        double speed = baseWorkTicks == 0 ? 1.0 : CitizenDecisionPlanner.skillSpeed(skillLevel);
        int workTicks = baseWorkTicks == 0 ? 0 : CitizenDecisionPlanner.effectiveWorkTicks(baseWorkTicks, skillLevel);
        citizen.setCurrentDecision(key, action.decisionScore(), speed, workTicks,
                Math.max(1, action.estimatedDurationTicks()));
    }

    private Goal select(Goal current) {
        CitizenDecisionPlanner.Action currentAction = current instanceof CitizenDecisionAction action
                ? action.decisionAction() : null;
        ArrayList<CitizenDecisionPlanner.Candidate> candidates = new ArrayList<>();
        for (Goal goal : actions) {
            CitizenDecisionAction action = (CitizenDecisionAction) goal;
            boolean eligible = goal == current ? goal.canContinueToUse() : goal.canUse();
            action.prepareDecisionSnapshot();
            double score = action.decisionScore();
            ArrayList<CitizenProfilePayload.DecisionDetail> details = new ArrayList<>(action.decisionDetails());
            details.add(0, new CitizenProfilePayload.DecisionDetail("Eligible", Boolean.toString(eligible)));
            details.add(1, new CitizenProfilePayload.DecisionDetail("Needs", String.format(java.util.Locale.ROOT,
                    "eat %d/100 • entertainment %d/100 • safety %d/100",
                    citizen.needs().eat(), citizen.needs().entertainment(), citizen.needs().safety())));
            if (action.decisionAction() == CitizenDecisionPlanner.Action.BAKE
                    || action.decisionAction() == CitizenDecisionPlanner.Action.FARM
                    || action.decisionAction() == CitizenDecisionPlanner.Action.MINE) {
                CitizenMarketMemory memory = citizen.marketMemory();
                details.add(new CitizenProfilePayload.DecisionDetail("Remembered trade target",
                        memory == null ? "none" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(memory.received())
                                + " for " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(memory.requested())));
                if (memory != null) details.add(new CitizenProfilePayload.DecisionDetail("Trade gain at current needs",
                        String.format(java.util.Locale.ROOT, "%.4f utility", memory.gain(citizen.needs()))));
            }
            details.add(new CitizenProfilePayload.DecisionDetail("Estimated total duration",
                    Math.max(1, action.estimatedDurationTicks()) + " ticks"));
            details.add(new CitizenProfilePayload.DecisionDetail("Final planner score",
                    String.format(java.util.Locale.ROOT, "%.4f utility/tick", eligible && Double.isFinite(score) ? score : 0.0)));
            candidates.add(new CitizenDecisionPlanner.Candidate(
                    action.decisionAction(), eligible, score, List.copyOf(details)));
        }
        CitizenDecisionPlanner.Action chosen = CitizenDecisionPlanner.choose(
                candidates, currentAction).orElse(null);
        citizen.setDecisionScores(candidates, chosen);
        if (chosen == null) return null;
        for (Goal goal : actions) {
            if (((CitizenDecisionAction) goal).decisionAction() == chosen) return goal;
        }
        return null;
    }
}
