package com.economistwars.citizen;

final class CitizenSleepAction implements CitizenSimulationAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.SLEEP; }
    public CitizenActionEvaluation evaluate(CitizenState s,CitizenSimulationContext c,boolean current) {
        if (!c.assigned(s) || !c.night()) return CitizenActionEvaluation.unavailable();
        var bed = c.beds().available(s.householdId,s.id(),s.dimension).orElse(null);
        if (bed == null) return CitizenActionEvaluation.unavailable();
        return CitizenActionEvaluation.priority(true,1800,(int)(23000-c.timeOfDay()),bed.position(),"Night sleep priority");
    }
    public void start(CitizenState s,CitizenSimulationContext c,CitizenActionEvaluation e) {
        var key = new CitizenAssetKey(s.dimension,e.destination());
        if (!c.beds().reserve(key,s.householdId,s.id())) return;
        CitizenSimulationAction.super.start(s,c,e); s.reservedBed = key.position();
    }
    public void advance(CitizenState s,CitizenSimulationContext c) { }
    public void stop(CitizenState s,CitizenSimulationContext c) {
        c.beds().release(s.id()); s.reservedBed = null; CitizenSimulationAction.super.stop(s,c);
    }
}
