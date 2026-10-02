package com.economistwars.household;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdHomeTest {
    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:the_nether"));
    @Test
    void homeIsOnlyAvailableInItsRegisteredDimension() {
        Household household = new Household(UUID.randomUUID());
        assertTrue(household.home(OVERWORLD).isEmpty());
        household.setHome(OVERWORLD, new BlockPos(10, 64, -20));
        assertEquals(new BlockPos(10, 64, -20), household.home(OVERWORLD).orElseThrow());
        assertTrue(household.home(NETHER).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void householdHomeAndMembershipSurviveSaving() throws Exception {
        HouseholdSavedData data = new HouseholdSavedData();
        UUID householdId = data.createHousehold();
        UUID citizenId = UUID.randomUUID();
        data.addMember(householdId, citizenId);
        data.getHousehold(householdId).setHome(OVERWORLD, new BlockPos(-10, 72, 20));
        var field = HouseholdSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        Codec<HouseholdSavedData> codec = (Codec<HouseholdSavedData>) field.get(null);
        HouseholdSavedData restored = codec.parse(JsonOps.INSTANCE,
                codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertTrue(restored.hasMember(householdId, citizenId));
        assertEquals(new BlockPos(-10, 72, 20), restored.getHousehold(householdId).home(OVERWORLD).orElseThrow());
        assertTrue(restored.getHousehold(householdId).home(NETHER).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedHomeMetadataPreservesMembership() throws Exception {
        UUID householdId = UUID.randomUUID();
        UUID citizenId = UUID.randomUUID();
        JsonObject records = new JsonObject();
        JsonArray fields = new JsonArray();
        fields.add(citizenId.toString());
        JsonObject snapshot = new JsonObject();
        snapshot.add("members", fields);
        records.add(householdId.toString(), snapshot);
        var field = HouseholdSavedData.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        Codec<HouseholdSavedData> codec = (Codec<HouseholdSavedData>) field.get(null);
        snapshot.addProperty("home", "home|minecraft:overworld|bad position");
        HouseholdSavedData malformed = codec.parse(JsonOps.INSTANCE, records).getOrThrow();
        assertTrue(malformed.hasMember(householdId, citizenId));
        assertTrue(malformed.getHousehold(householdId).home(OVERWORLD).isEmpty());
    }
}
