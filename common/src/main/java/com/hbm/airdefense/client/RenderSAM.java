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
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

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

    /**
     * Exhaust per Kind ordinal, {unstaged, staged}: {nozzle behind the centre, plume length, plume half-width}
     * in blocks (nozzle from the models' missile parts). The 57E6 dart coasts without a motor: its entry is
     * the tracer burning in its tail.
     */
    private static final float[][][] PLUMES = {
            {{2.8f, 7f, 0.55f}, {2.8f, 7f, 0.55f}},
            {{1.5f, 4.5f, 0.4f}, {1.5f, 4.5f, 0.4f}},
            {{3.3f, 14f, 1.1f}, {0.55f, 5f, 0.5f}},
            {{1.6f, 6f, 0.35f}, {0.2f, 2.5f, 0.12f}}};
    /** Minimum plume half-width per block of camera distance, so a far motor still shows as a spark. */
    private static final float PLUME_PER_BLOCK = 0.002f;

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
        state.plume = entity.motorOn() ? PLUMES[state.kind][entity.staged() ? 1 : 0] : null;
        state.power = Math.clamp(entity.burnTicks(partialTicks) / 3f, 0f, 1f); // lights up over 3 ticks
        state.seed = entity.getId() * 37 & 255;
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
        if (state.plume != null && state.power > 0) plume(state, poseStack, collector, camera);
        super.submit(state, poseStack, collector, camera);
    }

    /** Motor flame behind the nozzle, in entity space (not the model's rotated frame) so it can face the camera. */
    private static void plume(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float yaw = (float) Math.toRadians(state.yaw), pitch = (float) Math.toRadians(state.pitch);
        Vector3f back = new Vector3f(Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vector3f cam = new Vector3f((float) (camera.pos.x - state.x), (float) (camera.pos.y - state.y), (float) (camera.pos.z - state.z));
        float[] p = state.plume;
        Vector3f nozzle = new Vector3f(back).mul(p[0]).add(0, 0.2f, 0);
        float w = Math.max(p[2], nozzle.distance(cam) * PLUME_PER_BLOCK), len = p[1] * w / p[2];
        Vector3f end = new Vector3f(back).mul(len).add(nozzle);
        int power = (int) (state.power * 255), seed = state.seed;
        collector.submitCustomGeometry(poseStack, FxRender.type(), (pose, buffer) -> {
            FxRender.strip(pose, buffer, nozzle, end, cam, w, w, 0, 1, power, seed, FxRender.PLUME, 255);
            FxRender.billboard(pose, buffer, nozzle, cam, w * 1.8f, power, seed, FxRender.GLOW, 255);
        });
    }

    public static final class State extends EntityRenderState {
        float yaw, pitch, power;
        float @Nullable [] plume;
        int seed;
        int kind;
        int[] parts = {};
    }
}
