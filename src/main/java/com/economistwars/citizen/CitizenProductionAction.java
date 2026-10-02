package com.economistwars.citizen;

import java.util.*;
import com.economistwars.network.CitizenProfilePayload.DecisionDetail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

/** Shared utility/time evaluation and production progress over backend state. */
abstract class CitizenProductionAction implements CitizenSimulationAction {
    abstract CitizenSkill skill();
    abstract int baseTicks();
    abstract BlockPos destination(CitizenState s,CitizenSimulationContext c,boolean current);
    abstract List<CitizenDecisionPlanner.OutputOutcome> outcomes(CitizenState s,CitizenSimulationContext c,BlockPos target);
    boolean inputsAvailable(CitizenState s) { return true; }
    double inputUtility(CitizenState s) { return 0; }
    boolean scheduled(CitizenSimulationContext c) { return kind() == CitizenDecisionPlanner.Action.FARM ? c.day()%5 == 0
            : kind() == CitizenDecisionPlanner.Action.MINE && c.day()%5 != 0; }
    @Override public CitizenActionEvaluation evaluate(CitizenState s,CitizenSimulationContext c,boolean current) {
        if (!c.assigned(s) || c.night() && kind() != CitizenDecisionPlanner.Action.BAKE || !inputsAvailable(s)) return CitizenActionEvaluation.unavailable();
        BlockPos target = destination(s,c,current);
        if (target == null && kind() != CitizenDecisionPlanner.Action.BAKE) return CitizenActionEvaluation.unavailable();
        var outputs = outcomes(s,c,target);
        List<ItemStack> capacity = outputs.stream().filter(o -> o.affordable()).map(o -> new ItemStack(o.item(),o.quantity())).toList();
        if (kind() != CitizenDecisionPlanner.Action.BAKE && (s.deliveryRequested || capacity.isEmpty()
                || capacity.stream().noneMatch(stack -> s.inventory.canInsert(List.of(stack))))) return CitizenActionEvaluation.unavailable();
        CitizenMarketMemory memory = s.marketMemory;
        double output = CitizenDecisionPlanner.expectedOutputUtility(outputs,memory == null ? null : memory.received(),memory == null ? null : memory.requested(),s.needs);
        double input = inputUtility(s), netUtility = output - input;
        int level = s.skills.level(skill()), work = CitizenDecisionPlanner.effectiveWorkTicks(baseTicks(),level);
        int travel = target == null ? 0 : CitizenDecisionPlanner.travelTicks(s.position.distanceToSqr(target.getX()+0.5,target.getY(),target.getZ()+0.5));
        double score = CitizenDecisionPlanner.score(netUtility,baseTicks(),level,travel,scheduled(c));
        List<DecisionDetail> details = new ArrayList<>();
        details.add(new DecisionDetail("Expected output utility",String.format(Locale.ROOT,"%.4f",output)));
        details.add(new DecisionDetail("Consumed input utility",String.format(Locale.ROOT,"%.4f",input)));
        details.add(new DecisionDetail("Skill speed",String.format(Locale.ROOT,"%s %d; ×%.4f",skill().serializedName(),level,CitizenDecisionPlanner.skillSpeed(level))));
        details.add(new DecisionDetail("Base / effective work",baseTicks()+" / "+work+" ticks"));
        details.add(new DecisionDetail("Score formula",String.format(Locale.ROOT,"max(0, %.4f) ÷ (%d + %d) × %.2f = %.4f utility/tick",netUtility,work,travel,scheduled(c)?1.05:1,score)));
        outputs.forEach(o -> details.add(new DecisionDetail("Output",String.format(Locale.ROOT,"%d %s; %.0f%%; affordable %s",o.quantity(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(o.item()),o.probability()*100,o.affordable()))));
        return new CitizenActionEvaluation(true,score,work+(target == null || s.position.distanceToSqr(target.getX()+0.5,target.getY(),target.getZ()+0.5)<=2.25 ? 0 : travel),target,List.copyOf(details));
    }
    @Override public void start(CitizenState s,CitizenSimulationContext c,CitizenActionEvaluation e) {
        CitizenSimulationAction.super.start(s,c,e);
        s.speedMultiplier = CitizenDecisionPlanner.skillSpeed(s.skills.level(skill()));
        s.estimatedWorkTicks = CitizenDecisionPlanner.effectiveWorkTicks(baseTicks(),s.skills.level(skill()));
    }
    boolean ready(CitizenState s,CitizenSimulationContext c) {
        if (s.travelling()) return false;
        if (!evaluate(s,c,true).eligible()) { if (!s.inventory.isEmpty()) s.deliveryRequested = true; return false; }
        return ++s.workTicks >= CitizenDecisionPlanner.effectiveWorkTicks(baseTicks(),s.skills.level(skill()));
    }
    void completed(CitizenState s) {
        s.skills.addExperience(skill(),5); s.workTicks = 0; s.actionElapsed = 0;
        s.actionDuration = CitizenDecisionPlanner.effectiveWorkTicks(baseTicks(),s.skills.level(skill()));
        s.speedMultiplier = CitizenDecisionPlanner.skillSpeed(s.skills.level(skill())); s.estimatedWorkTicks = s.actionDuration;
    }
}

