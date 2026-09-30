package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.ItemAssetsReference;
import com.economistwars.ownership.assetsReferences.LandAssetsReference;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OwnershipSavedData extends SavedData {
    private static final Codec<OwnershipSavedData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
            .xmap(OwnershipSavedData::fromSerialized, OwnershipSavedData::toSerialized);
    private static final SavedDataType<OwnershipSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", "economistwars_ownership"),
            OwnershipSavedData::new, CODEC, DataFixTypes.LEVEL
    );

    private final Map<UUID, HouseholdOwnerships> ownerships = new LinkedHashMap<>();

    public static OwnershipSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<String, List<String>> toSerialized() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, HouseholdOwnerships> entry : ownerships.entrySet()) {
            List<String> serializedOwnerships = new ArrayList<>();
            for (Ownership ownership : entry.getValue().getAllOwnerships()) {
                String serialized = ownership.serializeOwnership();
                if (serialized != null) {
                    serializedOwnerships.add(serialized);
                }
            }
            result.put(entry.getKey().toString(), serializedOwnerships);
        }
        return result;
    }

    private static OwnershipSavedData fromSerialized(Map<String, List<String>> records) {
        OwnershipSavedData data = new OwnershipSavedData();
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            UUID householdId;
            try {
                householdId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                continue;
            }

            HouseholdOwnerships householdOwnerships = new HouseholdOwnerships();
            for (String serialized : entry.getValue()) {
                Ownership ownership = Ownership.deserializeOwnership(serialized);
                if (ownership != null) {
                    householdOwnerships.addOwnership(ownership);
                }
            }
            if (householdOwnerships.GetTotalOwnershipsCount() > 0) {
                data.ownerships.put(householdId, householdOwnerships);
            }
        }
        return data;
    }




}
