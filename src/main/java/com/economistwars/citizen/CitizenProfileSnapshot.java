package com.economistwars.citizen;

import java.util.*;
import com.economistwars.network.CitizenProfilePayload;
import net.minecraft.world.item.*;

public final class CitizenProfileSnapshot {
    private CitizenProfileSnapshot() { }
    static ItemStack tool(CitizenState state) {
        if (state.action == CitizenDecisionPlanner.Action.FARM) return new ItemStack(Items.IRON_HOE);
        if (state.action == CitizenDecisionPlanner.Action.MINE) return new ItemStack(Items.IRON_PICKAXE);
        return ItemStack.EMPTY;
    }
    public static CitizenProfilePayload create(CitizenState s,CitizenSimulationContext c) {
        var h = c.assigned(s) ? c.household(s.householdId).orElse(null) : null;
        var memory = s.marketMemory;
        return new CitizenProfilePayload(s.identity.name(),s.identity.sex().name(),s.id().toString(),s.identity.skin().toString(),
                h == null ? "" : h.id().toString(),h == null ? 0 : h.memberCount(),h == null ? 0 : h.foodCount(),
                h == null ? 0 : h.foodShortage(),h == null ? 0 : h.coins(),h == null ? 0 : c.farmParcelCount(h.id(),s.dimension),
                s.actionName(),s.decisionScore,s.decisionScores,s.actionElapsed,s.actionDuration,
                CitizenNeed.urgency(s.needs,CitizenNeed.EAT),CitizenNeed.urgency(s.needs,CitizenNeed.ENTERTAINMENT),CitizenNeed.urgency(s.needs,CitizenNeed.SAFETY),
                s.needs.values().stream().map(n -> new CitizenProfilePayload.NeedProgress(n.name(),n.urgency())).toList(),
                s.speedMultiplier,s.estimatedWorkTicks,memory == null ? ItemStack.EMPTY : new ItemStack(memory.received()),
                memory == null ? ItemStack.EMPTY : new ItemStack(memory.requested()),memory != null,
                memory == null ? 0 : memory.marketPosition().getX(),memory == null ? 0 : memory.marketPosition().getY(),
                memory == null ? 0 : memory.marketPosition().getZ(),memory == null ? 0 : memory.gain(s.needs),
                Arrays.stream(CitizenSkill.values()).map(skill -> new CitizenProfilePayload.SkillProgress(skill.serializedName(),s.skills.experience(skill))).toList(),
                s.inventory.contents(),tool(s));
    }
}
