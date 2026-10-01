package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void registeredAssetTypeRoundTripsWithoutOwnershipChanges() {
        AssetsReferenceRegistry.register("test-custom", CustomReference::new);
        Ownership original = new Ownership(OWNER, new CustomReference("some|payload"));
        assertEquals(OWNER + "|test-custom|some|payload", original.serializeOwnership());
        Ownership restored = Ownership.deserializeOwnership(original.serializeOwnership());
        assertNotNull(restored);
        assertEquals(OWNER, restored.getOwner());
        assertEquals("some|payload", ((CustomReference) restored.getAssetReference()).payload);
        assertThrows(IllegalArgumentException.class,
                () -> AssetsReferenceRegistry.register("test-custom", CustomReference::new));
    }

    @Test
    void existingItemAndLandRecordsKeepTheirFormat() {
        String itemRecord = OWNER + "|item|" + OWNER;
        Ownership item = Ownership.deserializeOwnership(itemRecord);
        assertNotNull(item);
        assertEquals(OWNER, ((ItemAssetsReference) item.getAssetReference()).getItemUUID());
        assertEquals(itemRecord, item.serializeOwnership());

        String landRecord = OWNER + "|land|-1,2,3,4,5,6";
        Ownership land = Ownership.deserializeOwnership(landRecord);
        assertNotNull(land);
        BoundingBox bounds = ((LandAssetsReference) land.getAssetReference()).getLandBoundingBox();
        assertEquals(-1, bounds.minX());
        assertEquals(6, bounds.maxZ());
        assertEquals(landRecord, land.serializeOwnership());
    }

    @Test
    void invalidRecordsAndIncompleteReferencesAreSkipped() {
        assertNull(Ownership.deserializeOwnership("bad"));
        assertNull(Ownership.deserializeOwnership("bad|item|" + OWNER));
        assertNull(Ownership.deserializeOwnership(OWNER + "|unknown|payload"));
        assertNull(Ownership.deserializeOwnership(OWNER + "|item|bad"));
        assertNull(Ownership.deserializeOwnership(OWNER + "|land|1,2"));
        assertNull(Ownership.deserializeOwnership(OWNER + "|land|a,2,3,4,5,6"));
        assertNull(new Ownership(new ItemAssetsReference(OWNER)).serializeOwnership());
        assertNull(new Ownership(OWNER, null).serializeOwnership());
        assertNull(new Ownership(OWNER, new ItemAssetsReference(null)).serializeOwnership());
        assertNull(new Ownership(OWNER, new LandAssetsReference(null)).serializeOwnership());
    }

    private static final class CustomReference extends AssetsReference {
        private final String payload;

        private CustomReference(String payload) { this.payload = payload; }

        @Override
        public String getType() { return "test-custom"; }

        @Override
        public String serializePayload() { return payload; }
    }
}
