package com.hbm.airdefense.fabric;

import com.hbm.airdefense.client.Renderers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FabricClientEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Renderers.register(new Renderers.Sink() {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntity(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                BlockEntityRendererRegistry.register(type, provider);
            }

            @Override
            public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                EntityRendererRegistry.register(type, provider);
            }
        });
    }
}
