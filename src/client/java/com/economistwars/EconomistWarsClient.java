package com.economistwars;

import com.economistwars.citizen.CitizenEntityType;
import com.economistwars.citizen.CitizenRenderer;
import com.economistwars.citizen.CitizenProfileScreen;
import com.economistwars.network.CitizenProfilePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.api.ClientModInitializer;

public final class EconomistWarsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(CitizenEntityType.CITIZEN, CitizenRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CitizenProfilePayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreenAndShow(new CitizenProfileScreen(payload)));
        });
        EconomistWars.LOGGER.info("Economist Wars client is ready");
    }
}
