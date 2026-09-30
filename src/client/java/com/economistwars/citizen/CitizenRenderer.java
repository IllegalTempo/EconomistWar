package com.economistwars.citizen;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

public final class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderState, HumanoidModel<CitizenRenderState>> {
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
        state.teleporting = entity.isTeleporting();
        state.teleportProgress = entity.teleportProgress();
        state.actionProgressVisible = !state.teleporting && !"idle".equals(entity.currentDecision());
        state.actionProgress = entity.decisionProgress();
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState state) {
        return state.skin == null ? FALLBACK_SKIN : state.skin;
    }

    @Override
    protected void submitNameDisplay(CitizenRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera) {
        super.submitNameDisplay(state, poseStack, collector, camera);
        if ((!state.teleporting && !state.actionProgressVisible)
                || state.nameTagAttachment == null || state.nameTag == null) return;
        int progress = state.teleporting ? state.teleportProgress : state.actionProgress;

        poseStack.pushPose();
        poseStack.translate(state.nameTagAttachment.x, state.nameTagAttachment.y + 0.5, state.nameTagAttachment.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(0.025F, -0.025F, 0.025F);
        collector.order(0).submitTextBackground(poseStack, -20, 10, 20, 15,
                0xFF17191F, Font.DisplayMode.NORMAL, state.lightCoords);
        collector.order(0).submitTextBackground(poseStack, -19, 11,
                -19 + 38.0F * Math.clamp(progress, 0, 10) / 10.0F, 14,
                0xFF56E0BD, Font.DisplayMode.NORMAL, state.lightCoords);
        poseStack.popPose();
    }
}
