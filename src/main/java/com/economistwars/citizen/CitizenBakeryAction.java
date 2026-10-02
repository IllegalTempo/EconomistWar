package com.economistwars.citizen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

final class CitizenBakeryAction extends CitizenProductionAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.BAKE; }
    CitizenSkill skill() { return CitizenSkill.BAKERY; }
    int baseTicks() { return 1200; }
    boolean inputsAvailable(CitizenState s) { return s.inventory.canBakeBread(); }
    double inputUtility(CitizenState s) { return 3 * ItemNeedValues.forItem(Items.WHEAT).utility(s.needs); }
    BlockPos destination(CitizenState s,CitizenSimulationContext c,boolean current) { return null; }
    List<CitizenDecisionPlanner.OutputOutcome> outcomes(CitizenState s,CitizenSimulationContext c,BlockPos p) {
        return List.of(new CitizenDecisionPlanner.OutputOutcome(Items.BREAD,1,1,true));
    }
    public void advance(CitizenState s,CitizenSimulationContext c) { if (ready(s,c) && s.inventory.bakeBread()) completed(s); }
}
