package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record CitizenAssetKey(String dimension, BlockPos position) {
    public static final Codec<CitizenAssetKey> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(CitizenAssetKey::dimension),
            BlockPos.CODEC.fieldOf("position").forGetter(CitizenAssetKey::position)
    ).apply(i, CitizenAssetKey::new));
}
