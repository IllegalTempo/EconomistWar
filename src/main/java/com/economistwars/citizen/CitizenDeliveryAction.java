package com.economistwars.citizen;

import net.minecraft.core.BlockPos;

final class CitizenDeliveryAction implements CitizenSimulationAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.DELIVERY; }
    public CitizenActionEvaluation evaluate(CitizenState s,CitizenSimulationContext c,boolean current) {
        if (!c.assigned(s) || s.inventory.isEmpty()) return CitizenActionEvaluation.unavailable();
        BlockPos home = c.home(s.householdId,s.dimension).orElse(null);
        boolean changed = s.inventoryWork != null && s.inventoryWork != (c.day()%5==0 ? CitizenWorkSchedule.Work.FARM : CitizenWorkSchedule.Work.MINE);
        return CitizenActionEvaluation.priority(home != null && (current || s.deliveryRequested || s.inventory.full() || changed),900,
                home == null ? 1 : CitizenDecisionPlanner.travelTicks(s.position.distanceToSqr(home.getX()+0.5,home.getY(),home.getZ()+0.5))+1,home,"Requested delivery priority");
    }
    public void advance(CitizenState s,CitizenSimulationContext c) {
        if (s.travelling()) return;
        var household = c.household(s.householdId).orElse(null); if (household == null) return;
        for (int i = 0; i < 36; i++) s.inventory.set(i,household.deposit(s.inventory.get(i)));
        if (s.inventory.isEmpty()) { s.deliveryRequested = false; s.inventoryWork = null; }
        // Retry on the next rescore if the household store is full.
        s.action = null; s.actionElapsed = 0;
    }
}
