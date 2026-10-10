package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.client.render.RenderMissile;
import com.hbm.main.ResourceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class AirDefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Factories run on resource reload, after the meshes can be read.
        BlockEntityRendererRegistry.register(AirDefense.BUK_BE.get(), ctx -> new RenderBuk());
        BlockEntityRendererRegistry.register(AirDefense.TOR_BE.get(), ctx -> new RenderTor());
        BlockEntityRendererRegistry.register(AirDefense.PANTSIR_BE.get(), ctx -> new RenderPantsir());
        BlockEntityRendererRegistry.register(AirDefense.ASSEMBLER_BE.get(), ctx -> new RenderDefenseAssembler());
        BlockEntityRendererRegistry.register(AirDefense.NEBO_BE.get(), ctx -> new RenderNebo());
        EntityRendererRegistry.register(AirDefense.SAM.get(), RenderSAM::new);
        EntityRendererRegistry.register(AirDefense.SPENT_STAGE.get(), RenderSpentStage::new);
        EntityRendererRegistry.register(AirDefense.TRACER.get(), RenderTracer::new);
        EntityRendererRegistry.register(AirDefense.TARGET_DRONE.get(),
                ctx -> new RenderMissile(ctx, ResourceManager.missileV2, ResourceManager.missileV2_decoy_tex, 1.0f));
    }
}
