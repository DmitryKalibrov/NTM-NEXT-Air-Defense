package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityDefenseAssembler;
import com.hbm.client.model.Meshes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.render.loader.HFRWavefrontObject;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * Defence assembly hall: the hall itself is the block model; this draws the travelling crane and,
 * on the stand, the launcher being built - its parts appear one by one as the recipe progresses.
 */
public class RenderDefenseAssembler implements BlockEntityRenderer<BlockEntityDefenseAssembler, RenderDefenseAssembler.State> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/defense_assembler.obj"));
    private static final RenderType TYPE = RenderTypes.entityCutoutCull(AirDefense.id("textures/block/defense_assembler.png"));
    private static final int BRIDGE = MESH.partId("Bridge"), TROLLEY = MESH.partId("Trolley"), HOOK = MESH.partId("Hook");
    /** Hall floor height the products stand on. */
    private static final float FLOOR = 0.1f;

    /** A launcher on the stand: its mesh and parts in build order. */
    private record Product(HFRWavefrontObject mesh, RenderType type, int[] parts) {
        static Product of(HFRWavefrontObject mesh, Identifier tex, String... parts) {
            return new Product(mesh, RenderTypes.entityCutoutCull(tex), mesh.partIds(parts));
        }
    }

    private static final Product BUK = Product.of(RenderBuk.MESH, RenderBuk.TEX,
            "Base", "Carriage", "Radar", "Launcher", "RamCylinder", "RamRod");
    private static final Product TOR = Product.of(RenderTor.MESH, RenderTor.TEX, "TBase", "Turret", "Hatch1", "Hatch2",
            "Hatch3", "Hatch4", "Hatch5", "Hatch6", "Hatch7", "Hatch8", "TrackRadar", "SearchRadar");
    private static final Product PANTSIR = Product.of(RenderPantsir.MESH, RenderPantsir.TEX,
            "Base", "Turret", "TrackRadar", "WeaponL", "WeaponR", "SearchRadar");
    // the Nebo antenna is taller than the hall: it goes up on site
    private static final Product NEBO = Product.of(RenderNebo.MESH, AirDefense.id("textures/block/radar_nebo.png"), "Base");

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
        state.time = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partialTicks;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);

        // crane: parked at the rear end when idle, wandering over the stand while working
        float t = s.time;
        float along = s.working ? 2.4f * Mth.sin(t * 0.021f) : 3.6f;
        float across = s.working ? 1.8f * Mth.sin(t * 0.033f + 1f) : 0f;
        float drop = s.working ? 1.4f * (0.5f + 0.5f * Mth.sin(t * 0.057f)) : 0f;
        ps.pushPose();
        ps.translate(0, 0, along);
        collector.submitCustomGeometry(ps, TYPE, (pose, buf) -> MESH.renderPart(pose, buf, light, -1, BRIDGE));
        ps.translate(across, 0, 0);
        collector.submitCustomGeometry(ps, TYPE, (pose, buf) -> MESH.renderPart(pose, buf, light, -1, TROLLEY));
        ps.translate(0, -drop, 0);
        collector.submitCustomGeometry(ps, TYPE, (pose, buf) -> MESH.renderPart(pose, buf, light, -1, HOOK));
        ps.popPose();

        Product p = s.product;
        if (p != null) {
            int shown = s.working || s.progress > 0 ? 1 + (int) (s.progress * (p.parts.length - 1)) : 1;
            ps.translate(0, FLOOR, 0);
            for (int i = 0; i < shown && i < p.parts.length; i++) {
                int part = p.parts[i];
                collector.submitCustomGeometry(ps, p.type, (pose, buf) -> p.mesh.renderPart(pose, buf, light, -1, part));
            }
        }
        ps.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        @Nullable Product product;
        float progress, time;
        boolean working;
    }
}
