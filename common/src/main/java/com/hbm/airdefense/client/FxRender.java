package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.mixin.RenderTypeAccessor;
import com.hbm.api.client.ShaderPacks;
import com.hbm.client.particle.ParticleLayers;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;

/**
 * Our fire shader (shaders/core/debris.*): additive, no fog, procedural. The vertex colour is shader data:
 * r heat or progress, g seed, b the mode below, a fade.
 * <p>
 * Under a shaderpack (Iris) a pipeline of ours never reaches the picture - Iris only runs its own programs,
 * and NTM's for its own pipelines. There the same quads go through NTM's additive glow pipeline with the
 * shapes baked into textures/misc/fire_fx.png (tools/gen_fire.py), the heat turned into vertex colour here.
 */
final class FxRender {

    static final int TRAIL = 0, FIREBALL = 128, PLUME = 192, GLOW = 255;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(AirDefense.id("pipeline/debris"))
            .withVertexShader(AirDefense.id("core/debris"))
            .withFragmentShader(AirDefense.id("core/debris"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();
    private static final RenderType TYPE = RenderTypeAccessor.ntmad$create("ntm_air_defense_fire", RenderSetup.builder(PIPELINE).createRenderSetup());
    private static final RenderType IRIS_TYPE = RenderTypeAccessor.ntmad$create("ntm_air_defense_fire_baked",
            RenderSetup.builder(ParticleLayers.ADDITIVE_NO_FOG_PIPELINE).withTexture("Sampler0", AirDefense.id("textures/misc/fire_fx.png"))
                    .useLightmap().createRenderSetup());
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Shaderpack path for the geometry being written now; set by {@link #type()}. */
    private static boolean baked;

    private FxRender() {}

    /** The render type for this frame; the quads written after it follow the same path. */
    static RenderType type() {
        baked = ShaderPacks.inUse();
        return baked ? IRIS_TYPE : TYPE;
    }

    /** A camera-facing square around c, half-size s, UV -1..1. */
    static void billboard(PoseStack.Pose pose, VertexConsumer buffer, Vector3f c, Vector3f cam, float s,
                          int heat, int seed, int mode, int alpha) {
        Vector3f toCam = new Vector3f(cam).sub(c).normalize();
        Vector3f right = new Vector3f(0, 1, 0).cross(toCam);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(toCam).cross(right).normalize();
        corner(pose, buffer, c, right, up, -s, -s, -1, -1, heat, seed, mode, alpha);
        corner(pose, buffer, c, right, up, s, -s, 1, -1, heat, seed, mode, alpha);
        corner(pose, buffer, c, right, up, s, s, 1, 1, heat, seed, mode, alpha);
        corner(pose, buffer, c, right, up, -s, s, -1, 1, heat, seed, mode, alpha);
    }

    /**
     * A strip from a (u 0) to b (u 1) turned about its own axis to face the camera, half-widths wa / wb, v -1..1
     * across.
     */
    static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, Vector3f cam, float wa, float wb,
                      float ua, float ub, int heat, int seed, int mode, int alpha) {
        Vector3f side = new Vector3f(b).sub(a).cross(new Vector3f(a).sub(cam));
        if (side.lengthSquared() < 1e-8f) return;
        side.normalize();
        vertex(pose, buffer, a, side, wa, ua, -1, heat, seed, mode, alpha);
        vertex(pose, buffer, a, side, -wa, ua, 1, heat, seed, mode, alpha);
        vertex(pose, buffer, b, side, -wb, ub, 1, heat, seed, mode, alpha);
        vertex(pose, buffer, b, side, wb, ub, -1, heat, seed, mode, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p, Vector3f side, float off, float u, float v,
                               int heat, int seed, int mode, int alpha) {
        emit(buffer.addVertex(pose, p.x + side.x * off, p.y + side.y * off, p.z + side.z * off), u, v, u, (v + 1) / 2, heat, seed, mode, alpha);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer buffer, Vector3f c, Vector3f right, Vector3f up, float dx, float dy,
                               float u, float v, int heat, int seed, int mode, int alpha) {
        emit(buffer.addVertex(pose, c.x + right.x * dx + up.x * dy, c.y + right.y * dx + up.y * dy, c.z + right.z * dx + up.z * dy),
                u, v, (u + 1) / 2, (v + 1) / 2, heat, seed, mode, alpha);
    }

    /** u, v: the shader's coordinates; lu, lv: 0..1 across the shape, for the baked atlas cell. */
    private static void emit(VertexConsumer vertex, float u, float v, float lu, float lv, int heat, int seed, int mode, int alpha) {
        if (!baked) {
            vertex.setUv(u, v).setColor(heat, seed, mode, alpha);
            return;
        }
        // cells of fire_fx.png in a row: glow, trail, plume, fireball (half a texel in from each edge)
        int cell = switch (mode) { case GLOW -> 0; case TRAIL -> 1; case PLUME -> 2; default -> 3; };
        float au = (cell * 64 + 0.5f + lu * 63) / 256f, av = (0.5f + lv * 63) / 64f;
        float h = heat / 255f, a = alpha / 255f;
        float r, g, b, o;
        switch (mode) {
            case GLOW -> {
                float[] c = blackbody(h);
                r = c[0]; g = c[1]; b = c[2];
                o = a * (0.3f + h) * (0.8f + 0.4f * (float) Math.random()); // flicker
            }
            case TRAIL -> {
                float[] c = blackbody(h * (1 - lu * 0.8f));
                r = c[0]; g = c[1]; b = c[2];
                o = a * (0.3f + h) * (float) Math.pow(1 - lu, 1.5);
            }
            case PLUME -> { // the cell is coloured itself; heat here is the motor's power
                r = g = b = 1;
                o = h * a;
            }
            default -> { // fireball: heat is its progress, it cools to red and fades
                float cool = h * h;
                r = 1; g = 1 - 0.55f * h; b = 1 - 0.85f * h;
                o = a * (1 - cool);
            }
        }
        vertex.setUv(au, av).setColor(r, g, b, Math.min(1f, o)).setLight(FULL_BRIGHT);
    }

    /** Same ramp as blackbody() in debris.fsh: dull red, orange, yellow, white. */
    private static float[] blackbody(float t) {
        float[] c = {0.45f, 0.02f, 0f};
        mix(c, 1f, 0.32f, 0.02f, smooth(0f, 0.35f, t));
        mix(c, 1f, 0.72f, 0.22f, smooth(0.35f, 0.7f, t));
        mix(c, 1f, 0.97f, 0.88f, smooth(0.7f, 1f, t));
        return c;
    }

    private static void mix(float[] c, float r, float g, float b, float k) {
        c[0] += (r - c[0]) * k;
        c[1] += (g - c[1]) * k;
        c[2] += (b - c[2]) * k;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }
}
