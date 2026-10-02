package com.economistwars.citizen;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Lifecycle reconciliation changes visuals only, never citizen simulation state. */
public final class CitizenPresentationManager {
    public interface Visual {
        UUID citizenId(); void remove(); void apply(CitizenState state,Vec3 position);
    }
    public interface Access {
        List<Visual> visuals(); boolean entityTicking(String dimension,Vec3 position);
        Optional<Vec3> safePosition(CitizenState state); Visual spawn(CitizenState state,Vec3 position);
    }
    private CitizenPresentationManager() { }
    public static void reconcile(Collection<CitizenState> citizens,Access access) {
        Map<UUID,Visual> existing = new HashMap<>();
        Set<UUID> live = new HashSet<>();
        for (Visual visual : access.visuals()) if (existing.putIfAbsent(visual.citizenId(),visual) != null) visual.remove();
        for (CitizenState state : citizens) {
            if (!state.alive || !access.entityTicking(state.dimension,state.position)) continue;
            var safe = access.safePosition(state); if (safe.isEmpty()) continue;
            Visual visual = existing.get(state.id());
            if (visual == null) visual = access.spawn(state,safe.get());
            if (visual != null) { visual.apply(state,safe.get()); live.add(state.id()); }
        }
        existing.forEach((id,visual) -> { if (!live.contains(id)) visual.remove(); });
    }
    public static void reconcile(MinecraftServer server,CitizenSavedData citizens) {
        reconcile(citizens.states(),new Access() {
            public List<Visual> visuals() {
                List<Visual> visuals = new ArrayList<>();
                for (ServerLevel level : server.getAllLevels()) for (var entity : level.getAllEntities())
                    if (entity instanceof CitizenEntity citizen && !citizen.isRemoved()) {
                        citizen.importLegacy(); visuals.add(new EntityVisual(citizen));
                    }
                return visuals;
            }
            public boolean entityTicking(String dimension,Vec3 position) {
                var level = server.getLevel(MinecraftCitizenSimulationContext.dimension(dimension));
                return level != null && level.isPositionEntityTicking(BlockPos.containing(position));
            }
            public Optional<Vec3> safePosition(CitizenState state) {
                ServerLevel level = server.getLevel(MinecraftCitizenSimulationContext.dimension(state.dimension));
                if (level == null) return Optional.empty();
                CitizenEntity probe = new CitizenEntity(CitizenEntityType.CITIZEN,level);
                // Collision checks only inspect cells whose chunks are already entity-ticking.
                BlockPos center = BlockPos.containing(state.position);
                if (state.action == CitizenDecisionPlanner.Action.SLEEP && !state.travelling() && state.reservedBed != null
                        && level.isPositionEntityTicking(state.reservedBed) && level.getBlockState(state.reservedBed).getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                    return Optional.of(new Vec3(state.reservedBed.getX()+.5,state.reservedBed.getY(),state.reservedBed.getZ()+.5));
                for (int radius = 0; radius <= 4; radius++) for (int dy = -2; dy <= 3; dy++)
                    for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx),Math.abs(dz)) != radius) continue;
                        BlockPos p = center.offset(dx,dy,dz);
                        if (CitizenTeleportPoints.isSafe(level,probe,p)) return Optional.of(new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5));
                    }
                return Optional.empty();
            }
            public Visual spawn(CitizenState state,Vec3 position) {
                var level = server.getLevel(MinecraftCitizenSimulationContext.dimension(state.dimension));
                if (level == null) return null;
                CitizenEntity entity = new CitizenEntity(CitizenEntityType.CITIZEN,level);
                entity.bind(state.id()); entity.setPos(position); entity.applySnapshot(state);
                return level.addFreshEntity(entity) ? new EntityVisual(entity) : null;
            }
        });
    }
    private record EntityVisual(CitizenEntity entity) implements Visual {
        public UUID citizenId() { return entity.citizenId(); }
        public void remove() { entity.discard(); }
        public void apply(CitizenState state,Vec3 position) {
            if (!entity.level().dimension().identifier().toString().equals(state.dimension)) { entity.discard(); return; }
            entity.setPos(position); entity.applySnapshot(state);
        }
    }
}
