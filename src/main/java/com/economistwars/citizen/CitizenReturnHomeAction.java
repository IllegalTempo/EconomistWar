package com.economistwars.citizen;

final class CitizenReturnHomeAction implements CitizenSimulationAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.RETURN_HOME; }
    public CitizenActionEvaluation evaluate(CitizenState s,CitizenSimulationContext c,boolean current) {
        if (!c.assigned(s)) return CitizenActionEvaluation.unavailable();
        var home = c.home(s.householdId,s.dimension).orElse(null); if (home == null) return CitizenActionEvaluation.unavailable();
        double distance = s.position.distanceToSqr(home.getX()+.5,home.getY(),home.getZ()+.5);
        if (distance <= (current ? 2.25 : 64) || !current && !c.night() && !s.inventory.isEmpty() && !s.deliveryRequested) return CitizenActionEvaluation.unavailable();
        int travel = CitizenDecisionPlanner.travelTicks(distance);
        double score = c.night() ? 900 : CitizenDecisionPlanner.score(1+CitizenNeed.urgency(s.needs,CitizenNeed.SAFETY)/100.0,1,0,travel,false);
        return CitizenActionEvaluation.priority(true,score,travel,home,"Safety utility / travel duration; night priority");
    }
    public void advance(CitizenState s,CitizenSimulationContext c) { if (!s.travelling()) s.action = null; }
}
