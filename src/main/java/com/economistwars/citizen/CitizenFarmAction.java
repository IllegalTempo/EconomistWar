package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

final class CitizenFarmAction extends CitizenProductionAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.FARM; }
    CitizenSkill skill() { return CitizenSkill.FARMING; }
    int baseTicks() { return 12; }
    BlockPos destination(CitizenState s,CitizenSimulationContext c,boolean current) {
        var farms = c.farmPositions(s.householdId,s.dimension);
        if (current) return farms.contains(s.target) ? s.target : null;
        return farms.stream().min(Comparator.comparingDouble(p -> s.position.distanceToSqr(p.getX()+0.5,p.getY(),p.getZ()+0.5))).orElse(null);
    }
    List<CitizenDecisionPlanner.OutputOutcome> outcomes(CitizenState s,CitizenSimulationContext c,BlockPos p) {
        return List.of(new CitizenDecisionPlanner.OutputOutcome(Items.WHEAT,2,1,true));
    }
    public void advance(CitizenState s,CitizenSimulationContext c) {
        if (!ready(s,c)) return;
        if (s.inventory.insert(List.of(new ItemStack(Items.WHEAT,2)))) {
            s.inventoryWork = CitizenWorkSchedule.Work.FARM; completed(s);
        }
        if (!s.inventory.canInsert(List.of(new ItemStack(Items.WHEAT,2)))) s.deliveryRequested = true;
    }
}
