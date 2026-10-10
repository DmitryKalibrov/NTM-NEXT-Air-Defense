package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class AirDefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Factories run on resource reload, after the meshes can be read.
        BlockEntityRendererRegistry.register(AirDefense.BUK_BE.get(), ctx -> new RenderBuk());
        BlockEntityRendererRegistry.register(AirDefense.TOR_BE.get(), ctx -> new RenderTor());
        EntityRendererRegistry.register(AirDefense.SAM.get(), RenderSAM::new);
    }
}
