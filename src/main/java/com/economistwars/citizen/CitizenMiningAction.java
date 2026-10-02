package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

final class CitizenMiningAction extends CitizenProductionAction {
    public CitizenDecisionPlanner.Action kind() { return CitizenDecisionPlanner.Action.MINE; }
    CitizenSkill skill() { return CitizenSkill.MINING; }
    int baseTicks() { return 40; }
    private MineSiteState site(CitizenState s,CitizenSimulationContext c,BlockPos target) {
        return c.mines(s.dimension).stream().filter(m -> m.key.position().equals(target)).findFirst().orElse(null);
    }
    BlockPos destination(CitizenState s,CitizenSimulationContext c,boolean current) {
        if (current) return site(s,c,s.target) == null ? null : s.target;
        return c.mines(s.dimension).stream().filter(m -> m.bounds() != null && m.remaining() > 0)
                .min(Comparator.comparingDouble(m -> s.position.distanceToSqr(m.key.position().getX()+0.5,m.key.position().getY(),m.key.position().getZ()+0.5)))
                .map(m -> m.key.position()).orElse(null);
    }
    List<CitizenDecisionPlanner.OutputOutcome> outcomes(CitizenState s,CitizenSimulationContext c,BlockPos target) {
        MineSiteState site = site(s,c,target); int stock = site == null ? 0 : site.remaining();
        return List.of(new CitizenDecisionPlanner.OutputOutcome(Items.COAL,1,.7,stock>=MiningResourceValues.cost(new ItemStack(Items.COAL))),
                new CitizenDecisionPlanner.OutputOutcome(Items.RAW_IRON,1,.2,stock>=MiningResourceValues.cost(new ItemStack(Items.RAW_IRON))),
                new CitizenDecisionPlanner.OutputOutcome(Items.RAW_GOLD,1,.1,stock>=MiningResourceValues.cost(new ItemStack(Items.RAW_GOLD))));
    }
    public void advance(CitizenState s,CitizenSimulationContext c) {
        if (!ready(s,c)) return;
        MineSiteState site = site(s,c,s.target); if (site == null) return;
        if (!s.dropsRolled) { s.pendingDrops = c.rollMiningDrops(s.random); s.dropsRolled = true; }
        var payable = MiningResourceValues.affordableDrops(s.pendingDrops,site.remaining());
        if (!s.inventory.canInsert(payable.drops())) { s.deliveryRequested = true; return; }
        if (payable.resourceCost() > 0 && site.consume(payable.resourceCost())) {
            s.inventory.insert(payable.drops()); s.inventoryWork = CitizenWorkSchedule.Work.MINE; completed(s);
        } else s.workTicks = 0;
        s.pendingDrops = List.of(); s.dropsRolled = false;
        if (s.inventory.full()) s.deliveryRequested = true;
    }
}
