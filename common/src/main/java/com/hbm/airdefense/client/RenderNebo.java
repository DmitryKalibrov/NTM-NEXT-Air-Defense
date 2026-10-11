package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityNebo;
import com.hbm.client.model.Meshes;
import com.hbm.render.loader.HFRWavefrontObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Nebo radar: the static site comes from the block model, this turns the Yagi array with NTM's radar spin. */
public class RenderNebo implements BlockEntityRenderer<BlockEntityNebo, RenderNebo.State> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/radar_nebo.obj"));
    private static final int ANTENNA = MESH.partId("Antenna");
    private static final RenderType TYPE = RenderTypes.entityCutoutCull(AirDefense.id("textures/block/radar_nebo.png"));

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public void extractRenderState(BlockEntityNebo be, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breaking);
        state.spin = be.prevRotation + (be.rotation - be.prevRotation) * partialTicks;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-s.spin));
        collector.submitCustomGeometry(ps, TYPE, (pose, buffer) -> MESH.renderPart(pose, buffer, light, -1, ANTENNA));
        ps.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        float spin;
    }
}
