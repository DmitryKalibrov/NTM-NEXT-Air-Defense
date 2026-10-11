package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.EntitySAM;
import com.hbm.client.model.Meshes;
import net.minecraft.resources.Identifier;
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

    static final HFRWavefrontObject A135_MESH = Meshes.load(AirDefense.id("models/obj/abm_53t6.obj"));
    static final Identifier A135_TEX = AirDefense.id("textures/item/abm_53t6.png");
    // Per EntitySAM.Kind ordinal: BUK, TOR, A135.
    static final HFRWavefrontObject[] MESHES = {RenderBuk.MESH, RenderTor.MESH, A135_MESH, RenderPantsir.MESH};
    /** Two-stage kinds list the dropped stage first, then what flies on. */
    static final int[][] PARTS = {{RenderBuk.MESH.partId("BukMissile")}, {RenderTor.MESH.partId("TorMissile")},
            A135_MESH.partIds("A135Stage", "A135Warhead"), RenderPantsir.MESH.partIds("PantsirStage", "PantsirDart")};
    static final RenderType[] TYPES = {RenderTypes.entityCutoutCull(RenderBuk.TEX), RenderTypes.entityCutoutCull(RenderTor.TEX),
            RenderTypes.entityCutoutCull(A135_TEX), RenderTypes.entityCutoutCull(RenderPantsir.TEX)};

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
        state.kind = entity.kind().ordinal();
        int[] all = PARTS[state.kind];
        state.parts = entity.staged() ? new int[]{all[all.length - 1]} : all;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        HFRWavefrontObject mesh = MESHES[state.kind];
        int[] parts = state.parts;
        poseStack.pushPose();
        poseStack.translate(0, 0.2, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        collector.submitCustomGeometry(poseStack, TYPES[state.kind],
                (pose, buffer) -> {
                    for (int part : parts) mesh.renderPart(pose, buffer, light, -1, part);
                });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        float yaw, pitch;
        int kind;
        int[] parts = {};
    }
}
