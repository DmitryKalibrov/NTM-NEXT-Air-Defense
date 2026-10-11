package com.hbm.airdefense.neoforge;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.client.Renderers;
import com.hbm.registration.NeoForgeRegistrar;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AirDefense.MOD_ID)
public final class NeoForgeEntry {

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AirDefense.MOD_ID);

    public NeoForgeEntry(IEventBus modBus, Dist dist) {
        NeoForgeRegistrar.addon(AirDefense.MOD_ID, modBus, new AirDefense());
        TABS.register("main", () -> AirDefense.tab(CreativeModeTab.builder()).build());
        TABS.register(modBus);
        if (dist.isClient()) modBus.addListener(NeoForgeEntry::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        Renderers.register(new Renderers.Sink() {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntity(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }

            @Override
            public <T extends Entity> void entity(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                event.registerEntityRenderer(type, provider);
            }
        });
    }
}
