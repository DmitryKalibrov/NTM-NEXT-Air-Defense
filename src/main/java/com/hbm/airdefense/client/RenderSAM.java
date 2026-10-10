package com.hbm.airdefense.client;

import com.hbm.airdefense.EntitySAM;
import com.hbm.render.loader.HFRWavefrontObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;

/** The flying missile: the launcher model's missile part, nose (model -Z) along the flight path. */
public class RenderSAM extends EntityRenderer<EntitySAM, RenderSAM.State> {

    private static final int BUK = RenderBuk.MESH.partId("BukMissile");
    private static final int TOR = RenderTor.MESH.partId("TorMissile");
    private static final RenderType BUK_TYPE = RenderTypes.entityCutoutCull(RenderBuk.TEX);
    private static final RenderType TOR_TYPE = RenderTypes.entityCutoutCull(RenderTor.TEX);

    public RenderSAM(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    protected boolean affectedByCulling(EntitySAM entity) {
        return false;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EntitySAM entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        state.buk = entity.kind() == EntitySAM.Kind.BUK;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        HFRWavefrontObject mesh = state.buk ? RenderBuk.MESH : RenderTor.MESH;
        int part = state.buk ? BUK : TOR;
        poseStack.pushPose();
        poseStack.translate(0, 0.2, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poseStack, state.buk ? BUK_TYPE : TOR_TYPE,
                (pose, buffer) -> mesh.renderPart(pose, buffer, light, -1, part));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        float yaw, pitch;
        boolean buk;
    }
}
