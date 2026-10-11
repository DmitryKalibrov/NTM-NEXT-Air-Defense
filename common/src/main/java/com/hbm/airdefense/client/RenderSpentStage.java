package com.hbm.airdefense.client;

import com.hbm.airdefense.EntitySAM;
import com.hbm.airdefense.EntitySpentStage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** A dropped motor section (53T6 motor, 57E6 booster), tumbling end over end about its own centre. */
public class RenderSpentStage extends EntityRenderer<EntitySpentStage, RenderSpentStage.State> {

    private static final float TUMBLE_DEG_PER_TICK = 9f;

    public RenderSpentStage(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    protected boolean affectedByCulling(EntitySpentStage entity) {
        return false;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EntitySpentStage entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot() + (entity.tickCount + partialTicks) * TUMBLE_DEG_PER_TICK;
        state.kind = entity.kind();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.translate(0, 0.4, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        // stage centre in the flight model, which is centred on the whole missile
        poseStack.translate(0, 0, -state.kind.stageBack());
        int kind = state.kind.ordinal();
        collector.submitCustomGeometry(poseStack, RenderSAM.TYPES[kind],
                (pose, buffer) -> RenderSAM.MESHES[kind].renderPart(pose, buffer, light, -1, RenderSAM.PARTS[kind][0]));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        float yaw, pitch;
        EntitySAM.Kind kind = EntitySAM.Kind.A135;
    }
}
