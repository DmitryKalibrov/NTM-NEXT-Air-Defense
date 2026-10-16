package com.hbm.airdefense.client;

import com.hbm.airdefense.EntitySAM;
import com.hbm.airdefense.EntitySAMWreck;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** A dud in the ground: the whole missile, nose dug in or lying half sunk. */
public class RenderSAMWreck extends EntityRenderer<EntitySAMWreck, RenderSAMWreck.State> {

    public RenderSAMWreck(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EntitySAMWreck entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot();
        state.kind = entity.kind();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords, kind = state.kind.ordinal();
        int[] parts = RenderSAM.PARTS[kind];
        poseStack.pushPose();
        if (state.pitch > -30) poseStack.translate(0, -0.3, 0); // flat: half sunk; nose down: centre at the surface
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poseStack, RenderSAM.TYPES[kind],
                (pose, buffer) -> {
                    for (int part : parts) RenderSAM.MESHES[kind].renderPart(pose, buffer, light, -1, part);
                });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        float yaw, pitch;
        EntitySAM.Kind kind = EntitySAM.Kind.TOR;
    }
}
