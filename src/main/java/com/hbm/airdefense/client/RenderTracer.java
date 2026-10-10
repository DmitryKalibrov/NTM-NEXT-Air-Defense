package com.hbm.airdefense.client;

import com.hbm.airdefense.EntityTracer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/** Gun tracer: the Pantsir model's glowing streak along the flight path, always full bright. */
public class RenderTracer extends EntityRenderer<EntityTracer, RenderTracer.State> {

    private static final int TRACER = RenderPantsir.MESH.partId("Tracer");
    private static final RenderType TYPE = RenderTypes.entityCutoutCull(RenderPantsir.TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    public RenderTracer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    protected boolean affectedByCulling(EntityTracer entity) {
        return false;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EntityTracer entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Vec3 v = entity.getDeltaMovement();
        state.yaw = (float) Math.toDegrees(Math.atan2(-v.x, -v.z));
        state.pitch = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poseStack, TYPE,
                (pose, buffer) -> RenderPantsir.MESH.renderPart(pose, buffer, FULL_BRIGHT, -1, TRACER));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        float yaw, pitch;
    }
}
