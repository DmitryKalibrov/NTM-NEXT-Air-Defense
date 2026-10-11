package com.hbm.airdefense.fabric;

import com.hbm.airdefense.AirDefense;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Blocks, items and the rest come in through NTM's "hbm:addon" entrypoint; this only adds the creative tab. */
public final class FabricEntry implements ModInitializer {

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, AirDefense.id("main"), AirDefense.tab(FabricCreativeModeTab.builder()).build());
    }
}
