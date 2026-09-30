package com.economistwars.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class CitizenNetworking {
    private CitizenNetworking() {}

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(CitizenProfilePayload.TYPE, CitizenProfilePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SettlementMarketPayload.TYPE, SettlementMarketPayload.CODEC);
    }

    public static void sendProfile(ServerPlayer player, CitizenProfilePayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendMarket(ServerPlayer player, SettlementMarketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
