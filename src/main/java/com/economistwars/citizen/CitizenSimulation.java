package com.economistwars.citizen;

import com.economistwars.household.Household;
import com.economistwars.network.CitizenProfilePayload;
import java.util.*;
import net.minecraft.world.item.ItemStack;

/** Exactly one caller advances all backend citizens, regardless of visual availability. */
public final class CitizenSimulation {
    private static final List<CitizenSimulationAction> ACTIONS = List.of(new CitizenSleepAction(),new CitizenDeliveryAction(),
            new CitizenMarketAction(),new CitizenBakeryAction(),new CitizenFarmAction(),new CitizenMiningAction(),new CitizenReturnHomeAction());
    private CitizenSimulation() { }
    public static void advance(Collection<CitizenState> citizens,CitizenSimulationContext c) {
        for (Household household : c.households().households().stream().sorted(Comparator.comparing(Household::id)).toList()) {
            household.processEconomyDay(c.day()); c.tickLand(household,c.day());
        }
        for (CitizenState s : citizens.stream().sorted(Comparator.comparing(CitizenState::id)).toList()) {
            if (!s.alive) continue;
            s.needs.values().forEach(n -> n.advanceToDay(c.day()));
            Household household = c.assigned(s) ? c.household(s.householdId).orElse(null) : null;
            if (household != null) {
                if (s.lastShortageDamageDay < 0) s.lastShortageDamageDay = c.day();
                if (c.day() > s.lastShortageDamageDay) {
                    s.lastShortageDamageDay = c.day();
                    float damage = 0;
                    for (CitizenNeed n : s.needs.values()) damage += n.damageForShortage(n.name().equals(CitizenNeed.EAT)
                            ? household.foodShortage() : n.urgency() >= CitizenNeed.MAX ? 1 : 0);
                    if (damage > 0) damage(s,Math.min(4,damage),c);
                }
                if (s.consumptionCooldown-- <= 0) {
                    s.consumptionCooldown = 20;
                    ItemStack best = household.storageContents().stream().filter(stack -> ItemNeedValues.forItem(stack.getItem()).satisfiesUrgentNeed(s.needs))
                            .max(Comparator.comparingDouble(stack -> ItemNeedValues.forItem(stack.getItem()).utility(s.needs))).orElse(ItemStack.EMPTY);
                    if (!best.isEmpty()) {
                        ItemStack taken = household.takeFromStorage(stack -> ItemStack.isSameItemSameComponents(stack,best),1);
                        if (!taken.isEmpty()) ItemNeedValues.forItem(taken.getItem()).satisfy(s.needs);
                    }
                }
            }
            if (!s.alive) continue;
            CitizenSimulationAction current = action(s.action);
            boolean invalid = current != null && !current.evaluate(s,c,true).eligible();
            if (invalid || --s.rescoreTicks <= 0 || s.action == null && s.rescoreTicks <= 0) rescore(s,c,current);
            s.advanceTravel();
            current = action(s.action);
            if (current != null) { s.actionElapsed++; current.advance(s,c); }
        }
        c.changed();
    }
    private static CitizenSimulationAction action(CitizenDecisionPlanner.Action kind) {
        return ACTIONS.stream().filter(a -> a.kind() == kind).findFirst().orElse(null);
    }
    private static void rescore(CitizenState s,CitizenSimulationContext c,CitizenSimulationAction current) {
        Map<CitizenSimulationAction,CitizenActionEvaluation> evaluations = new LinkedHashMap<>();
        List<CitizenDecisionPlanner.Candidate> candidates = new ArrayList<>();
        for (var action : ACTIONS) {
            var e = action.evaluate(s,c,action == current); evaluations.put(action,e);
            candidates.add(new CitizenDecisionPlanner.Candidate(action.kind(),e.eligible(),e.score(),e.details()));
        }
        var chosen = CitizenDecisionPlanner.choose(candidates,s.action).orElse(null);
        s.decisionScores = candidates.stream().map(candidate -> new CitizenProfilePayload.DecisionScore(
                candidate.action().name().toLowerCase(Locale.ROOT),candidate.eligible(),candidate.action()==chosen,
                candidate.eligible()?candidate.score():0,candidate.details())).toList();
        if (chosen != s.action) {
            if (current != null) current.stop(s,c);
            var next = action(chosen); if (next != null) next.start(s,c,evaluations.get(next));
        } else if (current != null) s.decisionScore = evaluations.get(current).score();
        if (s.action == null) { s.decisionScore = 0; s.actionElapsed = 0; s.actionDuration = 0; s.speedMultiplier = 1; s.estimatedWorkTicks = 0; }
        s.rescoreTicks = 40;
    }
    public static boolean damage(CitizenState s,float amount,CitizenSimulationContext c) {
        if (!s.alive || !Float.isFinite(amount) || amount <= 0) return false;
        s.health = Math.max(0,s.health-amount);
        if (s.health == 0) {
            s.alive = false; s.cancelTravel(); s.action = null; c.beds().release(s.id());
            var household = c.household(s.householdId).orElse(null);
            if (household != null) for (int i = 0; i < 36; i++) s.inventory.set(i,household.deposit(s.inventory.get(i)));
            c.households().removeCitizen(s.id());
            if (s.householdId != null && c.household(s.householdId).isEmpty()) c.releaseLand(s.householdId);
        }
        c.changed(); return true;
    }
}
