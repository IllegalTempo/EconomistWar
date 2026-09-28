package com.economistwars.network;

import com.economistwars.EconomistWars;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CitizenProfilePayload(
        String name,
        String sex,
        String citizenId,
        String skinId,
        String householdId,
        int householdSize
) implements CustomPacketPayload {
    public static final Type<CitizenProfilePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "citizen_profile")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenProfilePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.name());
                buffer.writeUtf(payload.sex());
                buffer.writeUtf(payload.citizenId());
                buffer.writeUtf(payload.skinId());
                buffer.writeUtf(payload.householdId());
                buffer.writeVarInt(payload.householdSize());
            },
            buffer -> new CitizenProfilePayload(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
