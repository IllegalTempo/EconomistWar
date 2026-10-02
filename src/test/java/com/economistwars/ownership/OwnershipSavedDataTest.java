package com.economistwars.ownership;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OwnershipSavedDataTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:the_nether"));

    @Test
    void cropClaimsAreExclusiveDimensionAwareAndSurviveSaving() throws Exception {
        OwnershipSavedData data = new OwnershipSavedData();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        BlockPos crop = new BlockPos(10, 64, 10);
        data.claimCrop(OVERWORLD, owner, crop);
        data.claimCrop(OVERWORLD, other, crop);
        var field = OwnershipSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var codec = (com.mojang.serialization.Codec<OwnershipSavedData>) field.get(null);
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        data = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow();
        assertTrue(data.ownsFarmPosition(OVERWORLD, owner, crop.above()));
        assertFalse(data.ownsFarmPosition(OVERWORLD, other, crop));
        assertFalse(data.ownsFarmPosition(NETHER, owner, crop));
        assertEquals(crop, data.nearestFarmPosition(OVERWORLD, owner, new net.minecraft.world.phys.Vec3(0,64,0)).orElseThrow());
        data.setDirty(false);
        data.releaseHousehold(owner);
        assertTrue(data.isDirty(), "Removing crop-only ownership must be saved");
        assertFalse(data.ownsFarmPosition(OVERWORLD, owner, crop));
    }

    @Test
    void ordinaryOwnedLandDoesNotBecomeFarmWork() throws Exception {
        UUID owner = UUID.randomUUID();
        BlockPos center = new BlockPos(10,64,10);
        var field = OwnershipSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var codec = (com.mojang.serialization.Codec<OwnershipSavedData>) field.get(null);
        var deed = new Ownership(owner, new com.economistwars.ownership.assetsReferences.LandAssetsReference(
                "minecraft:overworld", new net.minecraft.world.level.levelgen.structure.BoundingBox(7,64,7,13,64,13)));
        var json = new com.google.gson.JsonObject();
        var deeds = new com.google.gson.JsonArray();
        deeds.add(deed.serializeOwnership());
        json.add(owner.toString(),deeds);
        var data = codec.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
        assertTrue(data.ownsLandPosition(OVERWORLD,owner,center));
        assertFalse(data.ownsFarmPosition(OVERWORLD,owner,center));
        assertEquals(0,data.farmParcelCount(OVERWORLD,owner));
        assertTrue(data.nearestFarmPosition(OVERWORLD,owner,new net.minecraft.world.phys.Vec3(10,64,10)).isEmpty());
    }

    @Test
    void farmPurchaseTransfersDeedAndCoinsAndProtectsLastSellerParcel() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        net.minecraft.world.item.Items.WHEAT.builtInRegistryHolder().bindComponents(components);
        var households = new com.economistwars.household.HouseholdSavedData();
        var buyer = households.getHousehold(households.createHousehold());
        var seller = households.getHousehold(households.createHousehold());
        households.addMember(buyer.id(), UUID.randomUUID());
        seller.deposit(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT,16));
        OwnershipSavedData data = new OwnershipSavedData();
        BlockPos first = new BlockPos(10,64,0), second = new BlockPos(30,64,0);
        data.registerParcel(OVERWORLD,first);
        data.registerParcel(OVERWORLD,second);
        data.grantStarter(OVERWORLD,seller.id(),first);
        assertFalse(data.buyFarmParcel(OVERWORLD,buyer,seller,first));
        seller.receiveCoins(60);
        assertTrue(data.buyFarmParcel(OVERWORLD,seller,null,second));
        assertTrue(data.buyFarmParcel(OVERWORLD,buyer,seller,first));
        assertEquals(40,buyer.coins());
        assertEquals(160,seller.coins());
        assertEquals(buyer.id(),data.parcelAt(OVERWORLD,first).orElseThrow().owner());
        assertEquals(1,data.farmParcelCount(OVERWORLD,seller.id()));
        assertFalse(data.buyFarmParcel(OVERWORLD,buyer,seller,second));
    }

    @Test
    void starterGrantsAreExclusiveDimensionAwareAndReleasedWithTheHousehold() {
        OwnershipSavedData data = new OwnershipSavedData();
        BlockPos center = new BlockPos(10, 64, 0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.registerParcel(OVERWORLD, center);
        data.registerParcel(NETHER, center);
        data.grantStarter(OVERWORLD, first, new BlockPos(0, 64, 0));
        data.grantStarter(OVERWORLD, second, new BlockPos(0, 64, 0));
        assertTrue(data.ownsLandPosition(OVERWORLD, first, center.above()));
        assertFalse(data.ownsLandPosition(OVERWORLD, second, center));
        assertFalse(data.ownsLandPosition(NETHER, first, center));
        assertEquals(first, data.parcelAt(OVERWORLD, center).orElseThrow().owner());
        assertNull(data.parcelAt(NETHER, center).orElseThrow().owner());
        data.releaseHousehold(first);
        data.grantStarter(OVERWORLD, second, new BlockPos(0, 64, 0));
        assertEquals(second, data.parcelAt(OVERWORLD, center).orElseThrow().owner());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registeredAndOwnedParcelsSurviveSaving() throws Exception {
        OwnershipSavedData data = new OwnershipSavedData();
        UUID owner = UUID.randomUUID();
        BlockPos center = new BlockPos(10, 64, 0);
        data.registerParcel(OVERWORLD, center);
        data.grantStarter(OVERWORLD, owner, center);
        var field = OwnershipSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        var codec = (com.mojang.serialization.Codec<OwnershipSavedData>) field.get(null);
        var ops = com.mojang.serialization.JsonOps.INSTANCE;
        OwnershipSavedData restored = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow();
        assertEquals(owner, restored.parcelAt(OVERWORLD, center).orElseThrow().owner());
        assertTrue(restored.ownsLandPosition(OVERWORLD, owner, center.above()));
        assertFalse(restored.ownsLandPosition(NETHER, owner, center));
    }
}
