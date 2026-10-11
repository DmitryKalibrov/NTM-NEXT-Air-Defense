package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.client.render.RenderMissile;
import com.hbm.main.ResourceManager;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Every renderer of the addon; each loader passes its own registration hook. */
public final class Renderers {

    /** Where a loader registers renderers (Fabric's registries, NeoForge's RegisterRenderers event). */
    public interface Sink {
        <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntity(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);

        <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider);
    }

    private Renderers() {}

    public static void register(Sink sink) {
        // Factories run on resource reload, after the meshes can be read.
        sink.blockEntity(AirDefense.BUK_BE.get(), ctx -> new RenderBuk());
        sink.blockEntity(AirDefense.TOR_BE.get(), ctx -> new RenderTor());
        sink.blockEntity(AirDefense.PANTSIR_BE.get(), ctx -> new RenderPantsir());
        sink.blockEntity(AirDefense.ASSEMBLER_BE.get(), ctx -> new RenderDefenseAssembler());
        sink.blockEntity(AirDefense.NEBO_BE.get(), ctx -> new RenderNebo());
        sink.entity(AirDefense.SAM.get(), RenderSAM::new);
        sink.entity(AirDefense.SPENT_STAGE.get(), RenderSpentStage::new);
        sink.entity(AirDefense.TRACER.get(), RenderTracer::new);
        sink.entity(AirDefense.TARGET_DRONE.get(),
                ctx -> new RenderMissile(ctx, ResourceManager.missileV2, ResourceManager.missileV2_decoy_tex, 1.0f));
    }
}
