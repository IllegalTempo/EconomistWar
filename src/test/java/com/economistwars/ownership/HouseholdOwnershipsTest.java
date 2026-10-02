package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.LandAssetsReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdOwnershipsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void noLandMeansNoWorkPosition() {
        HouseholdOwnerships ownerships = new HouseholdOwnerships();
        assertTrue(ownerships.nearestLandPosition(OWNER, Vec3.ZERO).isEmpty());
        assertFalse(ownerships.ownsLandPosition(OWNER, BlockPos.ZERO));
    }

    @Test
    void workPositionsStayWithinHorizontalBoundsAndAllowStandingAboveTheGround() {
        HouseholdOwnerships ownerships = new HouseholdOwnerships();
        ownerships.addOwnership(new Ownership(OWNER,
                new LandAssetsReference(new BoundingBox(-3, 64, -3, 3, 64, 3))));
        assertTrue(ownerships.ownsLandPosition(OWNER, new BlockPos(-3, 64, 3)));
        assertTrue(ownerships.ownsLandPosition(OWNER, new BlockPos(3, 66, -3)));
        assertFalse(ownerships.ownsLandPosition(OWNER, new BlockPos(4, 65, 0)));
        assertFalse(ownerships.ownsLandPosition(OWNER, new BlockPos(0, 63, 0)));
        assertFalse(ownerships.ownsLandPosition(OWNER, new BlockPos(0, 67, 0)));
    }

    @Test
    void nearestPositionUsesParcelEdgesRatherThanParcelCenters() {
        HouseholdOwnerships ownerships = new HouseholdOwnerships();
        ownerships.addOwnership(new Ownership(OWNER,
                new LandAssetsReference(new BoundingBox(0, 64, 0, 100, 64, 100))));
        ownerships.addOwnership(new Ownership(OWNER,
                new LandAssetsReference(new BoundingBox(103, 64, 0, 105, 64, 2))));
        assertEquals(new BlockPos(100, 64, 1),
                ownerships.nearestLandPosition(OWNER, new Vec3(101.5, 64, 1.5)).orElseThrow());
    }

    @Test
    void transferredLandIsImmediatelyUnavailableToThePreviousOwner() {
        HouseholdOwnerships ownerships = new HouseholdOwnerships();
        Ownership land = new Ownership(OWNER,
                new LandAssetsReference(new BoundingBox(0, 64, 0, 6, 64, 6)));
        ownerships.addOwnership(land);
        land.setOwner(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertFalse(ownerships.ownsLandPosition(OWNER, new BlockPos(3, 65, 3)));
        assertTrue(ownerships.nearestLandPosition(OWNER, new Vec3(3, 65, 3)).isEmpty());
    }
}
