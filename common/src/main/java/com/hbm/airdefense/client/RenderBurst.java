package com.hbm.airdefense.client;

import com.hbm.airdefense.EntityBurst;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector3f;

/**
 * Burning pieces of a shot-down missile: per piece a camera-facing ribbon along its last ticks and a glowing
 * head, both coloured by the debris shader (blackbody ramp by heat, flicker, the trail tearing into blobs).
 * Plus the fireball of every burst, same shader. Additive, no fog and a minimum on-screen size, so a kill
 * kilometres away still shows as a warm flash and falling sparks.
 */
public class RenderBurst extends EntityRenderer<EntityBurst, RenderBurst.State> {

    /** Ribbon half-width per block of camera distance: keeps a far piece a few pixels wide. */
    private static final float PER_BLOCK = 0.0025f;
    /** Fireball: ticks it lasts, full radius in blocks, minimum radius per block of camera distance. */
    private static final float BALL_TICKS = 18, KILL_BALL_TICKS = 30, BALL_RADIUS = 8, KILL_BALL_RADIUS = 16, BALL_PER_BLOCK = 0.008f;

    public RenderBurst(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    protected boolean affectedByCulling(EntityBurst entity) {
        return false;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EntityBurst entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.pieces.clear();
        state.kill = entity.kill();
        state.age = entity.tickCount + partialTicks;
        state.seed = entity.getId() * 37 & 255;
        double ox = entity.getX(), oy = entity.getY(), oz = entity.getZ();
        int n = EntityBurst.Piece.TRAIL;
        for (EntityBurst.Piece p : entity.pieces) {
            float heat = p.heat();
            // a burnt-out piece fades over a second instead of popping off
            float fade = heat > 0 ? 1f : Math.max(0f, 1f - (p.age - p.burnout) / 20f);
            if (fade <= 0) continue;
            float[] pts = new float[(n + 1) * 3];
            // head: between last tick and now
            pts[0] = (float) (p.trail[0] + (p.x - p.trail[0]) * partialTicks - ox);
            pts[1] = (float) (p.trail[1] + (p.y - p.trail[1]) * partialTicks - oy);
            pts[2] = (float) (p.trail[2] + (p.z - p.trail[2]) * partialTicks - oz);
            for (int i = 0; i < n; i++) {
                pts[(i + 1) * 3] = (float) (p.trail[i * 3] - ox);
                pts[(i + 1) * 3 + 1] = (float) (p.trail[i * 3 + 1] - oy);
                pts[(i + 1) * 3 + 2] = (float) (p.trail[i * 3 + 2] - oz);
            }
            state.pieces.add(new PieceState(pts, heat, p.seed, p.size, fade));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float ballT = state.age / (state.kill ? KILL_BALL_TICKS : BALL_TICKS);
        if (!state.pieces.isEmpty() || ballT < 1) {
            Vector3f cam = new Vector3f((float) (camera.pos.x - state.x), (float) (camera.pos.y - state.y), (float) (camera.pos.z - state.z));
            List<PieceState> pieces = List.copyOf(state.pieces);
            boolean kill = state.kill;
            int seed = state.seed;
            collector.submitCustomGeometry(poseStack, FxRender.type(), (pose, buffer) -> {
                if (ballT < 1) fireball(pose, buffer, ballT, kill, seed, cam);
                for (PieceState p : pieces) piece(pose, buffer, p, cam);
            });
        }
        super.submit(state, poseStack, collector, camera);
    }

    private static void piece(PoseStack.Pose pose, VertexConsumer buffer, PieceState p, Vector3f cam) {
        float[] pts = p.pts;
        int n = pts.length / 3;
        Vector3f head = new Vector3f(pts[0], pts[1], pts[2]);
        float w = p.size * Math.max(0.35f, head.distance(cam) * PER_BLOCK);
        int heat = (int) (p.heat * 255), seed = (int) (p.seed * 255), alpha = (int) (p.fade * 255);

        // Trail: u runs 0..1 by distance along it, not by point.
        float total = 0;
        for (int i = 1; i < n; i++) total += dist(pts, i - 1, i);
        if (total > 1e-3f) {
            Vector3f a = new Vector3f(), b = new Vector3f();
            float run = 0;
            for (int i = 1; i < n; i++) {
                float seg = dist(pts, i - 1, i);
                if (seg < 1e-4f) continue;
                a.set(pts[(i - 1) * 3], pts[(i - 1) * 3 + 1], pts[(i - 1) * 3 + 2]);
                b.set(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]);
                float u0 = run / total, u1 = (run + seg) / total;
                run += seg;
                FxRender.strip(pose, buffer, a, b, cam, w * (1 - u0 * 0.6f), w * (1 - u1 * 0.6f), u0, u1, heat, seed, FxRender.TRAIL, alpha);
            }
        }

        FxRender.billboard(pose, buffer, head, cam, w * 2.5f, heat, seed, FxRender.GLOW, alpha);
    }

    /** The burst: grows fast then slower, rises a little; t is 0..1 over its life. */
    private static void fireball(PoseStack.Pose pose, VertexConsumer buffer, float t, boolean kill, int seed, Vector3f cam) {
        Vector3f c = new Vector3f(0, t * (kill ? 5 : 3), 0);
        float full = Math.max(kill ? KILL_BALL_RADIUS : BALL_RADIUS, c.distance(cam) * BALL_PER_BLOCK);
        float r = full * (0.3f + 0.7f * (float) Math.sqrt(t));
        FxRender.billboard(pose, buffer, c, cam, r, (int) (t * 255), seed, FxRender.FIREBALL, 255);
    }

    private static float dist(float[] pts, int i, int j) {
        float dx = pts[i * 3] - pts[j * 3], dy = pts[i * 3 + 1] - pts[j * 3 + 1], dz = pts[i * 3 + 2] - pts[j * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    record PieceState(float[] pts, float heat, float seed, float size, float fade) {}

    public static final class State extends EntityRenderState {
        final List<PieceState> pieces = new ArrayList<>();
        boolean kill;
        float age;
        int seed;
    }
}
