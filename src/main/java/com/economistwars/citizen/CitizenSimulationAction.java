package com.economistwars.citizen;

import net.minecraft.world.phys.Vec3;

public interface CitizenSimulationAction {
    CitizenDecisionPlanner.Action kind();
    CitizenActionEvaluation evaluate(CitizenState state,CitizenSimulationContext context,boolean current);
    default void start(CitizenState s,CitizenSimulationContext context,CitizenActionEvaluation evaluation) {
        s.action = kind(); s.target = evaluation.destination(); s.workTicks = 0; s.actionElapsed = 0;
        s.actionDuration = evaluation.duration(); s.decisionScore = evaluation.score();
        if (s.target != null) {
            Vec3 destination = new Vec3(s.target.getX()+0.5,s.target.getY(),s.target.getZ()+0.5);
            if (s.position.distanceToSqr(destination) > 2.25) s.beginTravel(destination);
        }
    }
    void advance(CitizenState state,CitizenSimulationContext context);
    default void stop(CitizenState s,CitizenSimulationContext context) {
        s.cancelTravel(); s.action = null; s.target = null; s.workTicks = 0;
        s.pendingDrops = java.util.List.of(); s.dropsRolled = false;
    }
}
