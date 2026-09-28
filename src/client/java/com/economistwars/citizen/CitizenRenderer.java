package com.economistwars.citizen;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public final class CitizenRenderer extends MobRenderer<CitizenEntity, CitizenRenderState, HumanoidModel<CitizenRenderState>> {
    private static final Identifier FALLBACK_SKIN = Identifier.fromNamespaceAndPath("economistwars", "textures/entity/citizen/female_1.png");

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.45f);
    }

    @Override
    public CitizenRenderState createRenderState() {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(CitizenEntity entity, CitizenRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skin = entity.skin();
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState state) {
        return state.skin == null ? FALLBACK_SKIN : state.skin;
    }
}
