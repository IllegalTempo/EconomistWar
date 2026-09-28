package com.economistwars.household;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HouseholdHomeSavedData extends SavedData {
    private static final String DATA_ID = "economistwars_household_homes";
    private static final Codec<HouseholdHomeSavedData> CODEC = Codec.STRING.listOf()
            .xmap(HouseholdHomeSavedData::fromSerialized, data -> List.copyOf(data.populatedHomes));
    private static final SavedDataType<HouseholdHomeSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("economistwars", DATA_ID),
            HouseholdHomeSavedData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final Set<String> populatedHomes = new HashSet<>();

    public static HouseholdHomeSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isPopulated(ResourceKey<Level> dimension, BlockPos anchorPosition) {
        return populatedHomes.contains(key(dimension, anchorPosition));
    }

    public void markPopulated(ResourceKey<Level> dimension, BlockPos anchorPosition) {
        if (populatedHomes.add(key(dimension, anchorPosition))) {
            setDirty();
        }
    }

    private static String key(ResourceKey<Level> dimension, BlockPos anchorPosition) {
        return dimension.identifier() + "|" + anchorPosition.asLong();
    }

    private static HouseholdHomeSavedData fromSerialized(List<String> values) {
        HouseholdHomeSavedData data = new HouseholdHomeSavedData();
        for (String value : values) {
            if (value.lastIndexOf('|') > 0) {
                data.populatedHomes.add(value);
            }
        }
        return data;
    }
}
