package com.economistwars.citizen;

import java.util.Map;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Scores feasible citizen actions by expected net need utility per game tick. */
final class CitizenDecisionPlanner {
    static final double URGENT_SCORE = 900.0;
    static final int RESCORE_TICKS = 40;
    private static final double SKILL_SPEED_PER_LEVEL = 1.02;
    private static final double SCHEDULE_PREFERENCE = 1.05;

    private CitizenDecisionPlanner() {}

    static double skillSpeed(int skillLevel) {
        return Math.pow(SKILL_SPEED_PER_LEVEL, Math.clamp(skillLevel, 0, 100));
    }

    static int effectiveWorkTicks(int baseTicks, int skillLevel) {
        if (baseTicks <= 0) return 1;
        return Math.max(1, (int) Math.ceil(baseTicks / skillSpeed(skillLevel)));
    }

    static double score(double netUtility, int baseWorkTicks, int skillLevel, int travelTicks, boolean scheduled) {
        if (!Double.isFinite(netUtility) || netUtility <= 0.0) return 0.0;
        int totalTicks = effectiveWorkTicks(baseWorkTicks, skillLevel) + Math.max(0, travelTicks);
        double result = netUtility / Math.max(1, totalTicks);
        return scheduled ? result * SCHEDULE_PREFERENCE : result;
    }

    static double miningOutputUtility(Map<String, CitizenNeed> needs, Item received, Item requested,
            int availableStock, int coalCost, int ironCost, int goldCost) {
        return expectedOutputUtility(List.of(
                new OutputOutcome(Items.COAL, 1, 0.7, availableStock >= coalCost),
                new OutputOutcome(Items.RAW_IRON, 1, 0.2, availableStock >= ironCost),
                new OutputOutcome(Items.RAW_GOLD, 1, 0.1, availableStock >= goldCost)
        ), received, requested, needs);
    }

    static double expectedOutputUtility(List<OutputOutcome> outcomes, Item received,
            Item requested, Map<String, CitizenNeed> needs) {
        double expected = 0.0;
        for (OutputOutcome outcome : outcomes) {
            if (outcome.affordable() && outcome.probability() > 0.0) {
                expected += outcome.probability() * adjustedOutputUtility(outcome.item(),
                        outcome.quantity(), received, requested, needs);
            }
        }
        return expected;
    }

<<<<<<< HEAD
    static double adjustedOutputUtility(Item product, int quantity,
            Item rememberedReceived, Item rememberedRequested,
            CitizenNeeds needs) {
=======
    static double adjustedOutputUtility(net.minecraft.world.item.Item product, int quantity,
            net.minecraft.world.item.Item rememberedReceived, net.minecraft.world.item.Item rememberedRequested,
            Map<String, CitizenNeed> needs) {
>>>>>>> 6ed3b08ddfe4f62e2182c119ec4516b63a8bf14c
        if (quantity <= 0) return 0.0;
        double direct = ItemNeedValues.forItem(product).utility(needs);
        if (product == rememberedRequested && rememberedReceived != null) {
            double gain = ItemNeedValues.forItem(rememberedReceived).utility(needs) - direct;
            return (gain > 0.0 ? gain : direct * quantity) ;
        }
        return quantity * direct;
    }

    static Optional<Action> choose(List<Candidate> candidates, Action current) {
        Candidate best = candidates.stream().filter(Candidate::eligible)
                .max((first, second) -> Double.compare(first.score(), second.score())).orElse(null);
        if (best == null) return Optional.empty();
        if (current != null) {
            Candidate currentCandidate = candidates.stream()
                    .filter(candidate -> candidate.eligible() && candidate.action() == current)
                    .findFirst().orElse(null);
            if (currentCandidate != null && best.action() != current
                    && best.score() < currentCandidate.score() * 1.10) {
                return Optional.of(current);
            }
        }
        return Optional.of(best.action());
    }

    static int teleportTicks(double distance) {
        return Math.clamp((int) Math.ceil(Math.max(0.0, distance) * 2.0), 10, 200);
    }

    static int travelTicks(double distanceSquared) {
        return teleportTicks(Math.sqrt(Math.max(0.0, distanceSquared)));
    }

    enum Action { SLEEP, DELIVERY, MARKET, BAKE, FARM, MINE, RETURN_HOME }

    record OutputOutcome(Item item, int quantity, double probability, boolean affordable) {}

    record Candidate(Action action, boolean eligible, double score,
            List<com.economistwars.network.CitizenProfilePayload.DecisionDetail> details) {
        Candidate(Action action, boolean eligible, double score) {
            this(action, eligible, score, List.of());
        }
    }
}
