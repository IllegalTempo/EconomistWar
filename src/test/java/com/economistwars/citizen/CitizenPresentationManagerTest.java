package com.economistwars.citizen;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenPresentationManagerTest {
    @BeforeAll static void bootstrap() { CitizenStateTest.bootstrap(); }
    static final class Visual implements CitizenPresentationManager.Visual {
        final UUID id; boolean removed; Vec3 position;
        Visual(UUID id) { this.id = id; }
        public UUID citizenId() { return id; }
        public void remove() { removed = true; }
        public void apply(CitizenState state,Vec3 safePosition) { position = safePosition; }
    }
    static final class Access implements CitizenPresentationManager.Access {
        final List<Visual> visuals = new ArrayList<>(); boolean ticking = true, obstructed;
        public List<CitizenPresentationManager.Visual> visuals() { return new ArrayList<>(visuals.stream().filter(v -> !v.removed).toList()); }
        public boolean entityTicking(String dimension,Vec3 position) { return ticking; }
        public Optional<Vec3> safePosition(CitizenState state) { return obstructed ? Optional.empty() : Optional.of(state.position); }
        public CitizenPresentationManager.Visual spawn(CitizenState state,Vec3 position) { Visual v = new Visual(state.id()); visuals.add(v); return v; }
    }
    @Test void unloadRemovesOnlyVisualAndLaterRespawnsAtBackendPosition() {
        CitizenState state = CitizenStateTest.citizen(); Access access = new Access();
        CitizenPresentationManager.reconcile(List.of(state),access); assertEquals(1,access.visuals().size());
        UUID household = state.householdId;
        access.ticking = false; CitizenPresentationManager.reconcile(List.of(state),access); assertEquals(0,access.visuals().size());
        assertTrue(state.alive); assertEquals(household,state.householdId);
        state.position = new Vec3(30,64,10); access.ticking = true;
        CitizenPresentationManager.reconcile(List.of(state),access);
        assertEquals(state.position,((Visual)access.visuals().getFirst()).position);
    }
    @Test void oneVisualPerCitizenAndDeadRecordsDoNotRespawn() {
        CitizenState state = CitizenStateTest.citizen(); Access access = new Access();
        access.visuals.add(new Visual(state.id())); access.visuals.add(new Visual(state.id()));
        CitizenPresentationManager.reconcile(List.of(state),access); assertEquals(1,access.visuals().size());
        state.alive = false; CitizenPresentationManager.reconcile(List.of(state),access); assertEquals(0,access.visuals().size());
    }
    @Test void nonTickingOrObstructedDestinationDefersVisual() {
        CitizenState state = CitizenStateTest.citizen(); Access access = new Access(); access.obstructed = true;
        CitizenPresentationManager.reconcile(List.of(state),access); assertEquals(0,access.visuals().size());
        assertEquals(Vec3.ZERO,state.position);
        access.obstructed = false; access.ticking = false;
        CitizenPresentationManager.reconcile(List.of(state),access); assertEquals(0,access.visuals().size());
    }
    @Test void profileReflectsBackendProgress() {
        SimulationTestContext c = new SimulationTestContext(); CitizenState state = c.citizen();
        state.action = CitizenDecisionPlanner.Action.FARM; state.actionElapsed = 7; state.actionDuration = 12;
        state.inventory.insert(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT,2)));
        var profile = CitizenProfileSnapshot.create(state,c);
        assertEquals("farm",profile.currentDecision()); assertEquals(7,profile.decisionProgressTicks());
        assertEquals(12,profile.decisionDurationTicks()); assertEquals(2,profile.inventory().getFirst().getCount());
    }
}
