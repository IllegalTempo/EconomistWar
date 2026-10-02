package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;

final class CitizenMarketAction implements CitizenSimulationAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.MARKET; }
    public CitizenActionEvaluation evaluate(CitizenState s,CitizenSimulationContext c,boolean current) {
        if (!c.assigned(s) || c.night() || s.lastMarketVisitDay >= c.day()) return CitizenActionEvaluation.unavailable();
        BlockPos home = c.home(s.householdId,s.dimension).orElse(null); if (home == null) return CitizenActionEvaluation.unavailable();
        var market = c.markets(s.dimension).stream().filter(m -> m.key.position().distSqr(home) <= 128*128)
                .filter(m -> !current || m.key.position().equals(s.target))
                .min(Comparator.comparingDouble(m -> m.key.position().distSqr(home))).orElse(null);
        if (market == null) return CitizenActionEvaluation.unavailable();
        double benefit = market.potentialTradeBenefit(s.householdId,s.needs,id -> c.household(id).orElse(null));
        int urgency = s.needs.values().stream().mapToInt(CitizenNeed::urgency).max().orElse(0);
        BlockPos target = market.key.position();
        int outbound = CitizenDecisionPlanner.travelTicks(s.position.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5));
        int returning = CitizenDecisionPlanner.travelTicks(target.distSqr(home));
        double score = urgency >= 80 ? 900 : CitizenDecisionPlanner.score(benefit,1,0,outbound+returning,false);
        return CitizenActionEvaluation.priority(true,score,outbound+1,target,"Net barter utility / round-trip duration; priority at need 80");
    }
    public void advance(CitizenState s,CitizenSimulationContext c) {
        if (s.travelling()) return;
        c.markets(s.dimension).stream().filter(m -> m.key.position().equals(s.target)).findFirst()
                .ifPresent(m -> m.visit(s.householdId,s.needs,s,id -> c.household(id).orElse(null),c.gameTime()));
        s.lastMarketVisitDay = c.day(); s.action = null;
    }
}
