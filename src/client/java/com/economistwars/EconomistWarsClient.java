package com.economistwars;

import com.economistwars.citizen.CitizenEntityType;
import com.economistwars.citizen.CitizenRenderer;
import com.economistwars.citizen.CitizenProfileScreen;
import com.economistwars.citizen.MiningEncampmentHud;
import com.economistwars.citizen.SettlementMarketScreen;
import com.economistwars.network.CitizenProfilePayload;
import com.economistwars.network.SettlementMarketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;

public final class EconomistWarsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(CitizenEntityType.CITIZEN, CitizenRenderer::new);
        HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mining_encampment_status"),
                new MiningEncampmentHud());
        ClientPlayNetworking.registerGlobalReceiver(CitizenProfilePayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreenAndShow(new CitizenProfileScreen(payload)));
        });
        ClientPlayNetworking.registerGlobalReceiver(SettlementMarketPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreenAndShow(new SettlementMarketScreen(payload)));
        });
        EconomistWars.LOGGER.info("Economist Wars client is ready");
    }
}
