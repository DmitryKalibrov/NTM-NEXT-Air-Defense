package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.Camo;
import com.hbm.airdefense.BlockEntityEos;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Optoelectronic station: tripod still, the head turned to the station's yaw and tilted to its pitch. */
public class RenderEos implements BlockEntityRenderer<BlockEntityEos, RenderEos.State> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/eos.obj"));
    private static final int BASE = MESH.partId("Base"), YAW = MESH.partId("Yaw"), PITCH = MESH.partId("Pitch");
    private static final HFRWavefrontObject NET = Meshes.load(AirDefense.id("models/obj/net_eos.obj"));
    private static final int NET_PART = NET.partId("Net");

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;  // the head stands above the block
    }

    @Override
    public void extractRenderState(BlockEntityEos be, State s, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, cameraPosition, breaking);
        s.yaw = Mth.rotLerp(partialTicks, be.prevYaw, be.yaw);
        s.pitch = Mth.lerp(partialTicks, be.prevPitch, be.pitch);
        s.camo = Camo.of(be.getBlockState());
        s.net = Camo.net(be.getBlockState());
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        RenderType type = RenderTypes.entityCutoutCull(s.camo.texture("eos"));
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        if (s.net) collector.submitCustomGeometry(ps, RenderTypes.entityCutout(s.camo.netTexture()),
                (pose, buffer) -> NET.renderPart(pose, buffer, light, -1, NET_PART));
        collector.submitCustomGeometry(ps, type, (pose, buffer) -> MESH.renderPart(pose, buffer, light, -1, BASE));
        ps.pushPose();  // a fresh pose per stage: a submitted one is not changed afterwards
        ps.mulPose(Axis.YP.rotationDegrees(180 - s.yaw));  // the model looks to -Z, yaw 0 is south
        collector.submitCustomGeometry(ps, type, (pose, buffer) -> MESH.renderPart(pose, buffer, light, -1, YAW));
        ps.pushPose();
        ps.translate(0, BlockEntityEos.PITCH_Y, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-s.pitch));  // pitch positive = nose down
        ps.translate(0, -BlockEntityEos.PITCH_Y, 0);
        collector.submitCustomGeometry(ps, type, (pose, buffer) -> MESH.renderPart(pose, buffer, light, -1, PITCH));
        ps.popPose();
        ps.popPose();
        ps.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        float yaw, pitch;
        Camo camo = Camo.GREEN;
        boolean net;
    }
}
