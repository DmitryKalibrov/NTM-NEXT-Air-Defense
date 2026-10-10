package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityDefenseAssembler;
import com.hbm.client.model.Meshes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Defence assembly hall: the hall itself is the block model. This draws the launcher on the stand,
 * the overhead crane that brings its assemblies in one by one through the rear gate (+Z) and lowers
 * them into place, and two welding arms driven by NTM's assembler arm logic.
 */
public class RenderDefenseAssembler implements BlockEntityRenderer<BlockEntityDefenseAssembler, RenderDefenseAssembler.State> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/defense_assembler.obj"));
    private static final RenderType TYPE = RenderTypes.entityCutoutCull(AirDefense.id("textures/block/defense_assembler.png"));
    private static final int BRIDGE = MESH.partId("Bridge"), TROLLEY = MESH.partId("Trolley"), HOOK = MESH.partId("Hook"),
            CABLE = MESH.partId("Cable"), ARM_LOWER = MESH.partId("ArmLower"), ARM_UPPER = MESH.partId("ArmUpper"),
            ARM_HEAD = MESH.partId("ArmHead"), ARM_SPIKE = MESH.partId("ArmSpike");
    /** Hall floor height the products stand on. */
    private static final float FLOOR = 0.1f;
    /** Crane limits: bridge travel along Z, highest hook tip, cable drum height, hook block height. */
    private static final float PARK_Z = 3.3f, HOOK_TOP = 5.25f, DRUM = 6.65f, BLOCK = 0.62f;
    /** Arm pivots (x, y): shoulder, elbow, wrist; arm A stands at z = -0.9, arm B is it turned 180 degrees. */
    private static final float ARM_X = 3.5f, SHOULDER = 2.4f, ELBOW = 4.3f, WRIST_X = 2.0f, ARM_Z = -0.9f;

    /** A launcher on the stand: its mesh and assemblies in build order (the first one is there from the start). */
    private record Product(HFRWavefrontObject mesh, RenderType type, int[][] stages, float[][] bounds) {
        static Product of(HFRWavefrontObject mesh, Identifier tex, String[]... stages) {
            int[][] ids = new int[stages.length][];
            float[][] bounds = new float[stages.length][];
            for (int i = 0; i < stages.length; i++) {
                ids[i] = mesh.partIds(stages[i]);
                bounds[i] = mesh.boundsOfParts(ids[i]);
            }
            return new Product(mesh, RenderTypes.entityCutoutCull(tex), ids, bounds);
        }
    }

    private static String[] s(String... parts) {
        return parts;
    }

    private static final Product BUK = Product.of(RenderBuk.MESH, RenderBuk.TEX,
            s("Base"), s("Carriage"), s("Radar"), s("Launcher", "RamCylinder", "RamRod"));
    private static final Product TOR = Product.of(RenderTor.MESH, RenderTor.TEX, s("TBase"), s("Turret"),
            s("Hatch1", "Hatch2", "Hatch3", "Hatch4", "Hatch5", "Hatch6", "Hatch7", "Hatch8"), s("TrackRadar"), s("SearchRadar"));
    private static final Product PANTSIR = Product.of(RenderPantsir.MESH, RenderPantsir.TEX,
            s("Base"), s("Turret"), s("TrackRadar"), s("WeaponL"), s("WeaponR"), s("SearchRadar"));
    // the Nebo antenna is taller than the hall: it goes up on site
    private static final Product NEBO = Product.of(RenderNebo.MESH, AirDefense.id("textures/block/radar_nebo.png"), s("Base"));

    private static @Nullable Product productFor(@Nullable GenericRecipe recipe) {
        if (recipe == null) return null;
        Item out = recipe.getIcon().getItem();
        if (out == AirDefense.BUK.get().asItem()) return BUK;
        if (out == AirDefense.TOR.get().asItem()) return TOR;
        if (out == AirDefense.PANTSIR.get().asItem()) return PANTSIR;
        if (out == AirDefense.NEBO.get().asItem()) return NEBO;
        return null;
    }

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
    public void extractRenderState(BlockEntityDefenseAssembler be, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breaking);
        state.product = productFor(be.recipeModule.getRecipe());
        state.progress = (float) be.recipeModule.progress;
        state.working = be.isProgressing;
        be.arms[0].getPositions(partialTicks, state.arm1);
        be.arms[1].getPositions(partialTicks, state.arm2);
    }

    private static void part(PoseStack ps, SubmitNodeCollector c, int light, int part) {
        c.submitCustomGeometry(ps, TYPE, (pose, buf) -> MESH.renderPart(pose, buf, light, -1, part));
    }

    /** Turns the pose about the Z axis through (x, y); positive leans an arm inwards. */
    private static void pivot(PoseStack ps, float x, float y, double deg) {
        ps.translate(x, y, 0);
        ps.mulPose(Axis.ZP.rotationDegrees((float) deg));
        ps.translate(-x, -y, 0);
    }

    private static void arm(PoseStack ps, SubmitNodeCollector c, int light, double[] a) {
        ps.pushPose();
        ps.translate(0, 0, ARM_Z);
        pivot(ps, ARM_X, SHOULDER, a[0]);
        part(ps, c, light, ARM_LOWER);
        pivot(ps, ARM_X, ELBOW, a[1]);
        part(ps, c, light, ARM_UPPER);
        pivot(ps, WRIST_X, ELBOW, a[2]);
        part(ps, c, light, ARM_HEAD);
        ps.translate(0, a[3] * 0.5, 0);
        part(ps, c, light, ARM_SPIKE);
        ps.popPose();
    }

    private static float ease(float f, float from, float to) {
        float t = Mth.clamp((f - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState camera) {
        int light = s.lightCoords;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);

        arm(ps, c, light, s.arm1);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180));
        arm(ps, c, light, s.arm2);
        ps.popPose();

        // crane parked at the rear gate unless an assembly is on its way
        float hx = 0, hy = HOOK_TOP, hz = PARK_Z;
        Product p = s.product;
        int seated = 0;
        int carried = -1;
        float dx = 0, dy = 0, dz = 0;
        if (p != null) {
            int n = p.stages.length - 1;
            seated = 1;
            if (n > 0 && (s.working || s.progress > 0)) {
                float u = Mth.clamp(s.progress, 0, 0.9999f) * n;
                int k = 1 + (int) u;
                float f = u - (int) u;
                float[] b = p.bounds[k];
                float cx = (b[0] + b[3]) / 2, top = b[4] + FLOOR, cz = (b[2] + b[5]) / 2;
                float lift = Mth.clamp(HOOK_TOP - 0.05f - top, 0, 2);
                seated = k;
                if (f < 0.65f) {
                    // carried in on the hook from the rear gate, then lowered into place
                    carried = k;
                    float travel = 1 - ease(f, 0.05f, 0.45f);
                    dx = -cx * travel;
                    dz = (PARK_Z - cz) * travel;
                    dy = lift * (1 - ease(f, 0.45f, 0.62f));
                    hx = cx + dx;
                    hy = top + dy;
                    hz = cz + dz;
                } else {
                    // released: the hook climbs and the crane goes back for the next one
                    seated = k + 1;
                    float up = ease(f, 0.65f, 0.75f), back = ease(f, 0.72f, 1f);
                    hx = Mth.lerp(back, cx, 0);
                    hy = Mth.lerp(up, top, HOOK_TOP);
                    hz = Mth.lerp(back, cz, PARK_Z);
                }
            }
        }

        ps.pushPose();
        ps.translate(0, 0, hz);
        part(ps, c, light, BRIDGE);
        ps.translate(hx, 0, 0);
        part(ps, c, light, TROLLEY);
        ps.pushPose();
        ps.translate(0, hy, 0);
        part(ps, c, light, HOOK);
        ps.translate(0, BLOCK, 0);
        ps.scale(1, DRUM - hy - BLOCK, 1);
        part(ps, c, light, CABLE);
        ps.popPose();
        ps.popPose();

        if (p != null) {
            ps.translate(0, FLOOR, 0);
            for (int i = 0; i < seated; i++) stage(ps, c, light, p, i);
            if (carried >= 0) {
                ps.translate(dx, dy, dz);
                stage(ps, c, light, p, carried);
            }
        }
        ps.popPose();
    }

    private static void stage(PoseStack ps, SubmitNodeCollector c, int light, Product p, int i) {
        for (int part : p.stages[i]) {
            c.submitCustomGeometry(ps, p.type, (pose, buf) -> p.mesh.renderPart(pose, buf, light, -1, part));
        }
    }

    public static final class State extends BlockEntityRenderState {
        @Nullable Product product;
        float progress;
        boolean working;
        final double[] arm1 = new double[4], arm2 = new double[4];
    }
}
