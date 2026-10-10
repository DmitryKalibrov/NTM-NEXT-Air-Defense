package com.hbm.airdefense;

import com.hbm.registration.Addon;
import com.hbm.registration.Reg;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NTM addon entrypoint ("hbm:addon"): runs inside NTM's registration window, before caps are baked. */
public final class AirDefense implements Addon {

    public static final String MOD_ID = "ntm_air_defense";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Reg.Handle<TurretBuk> BUK;
    public static Reg.Handle<TurretTor> TOR;
    public static Reg.BeHandle<BlockEntityBuk> BUK_BE;
    public static Reg.BeHandle<BlockEntityTor> TOR_BE;
    public static Reg.EntityHandle<EntitySAM> SAM;
    public static Reg.Handle<Item> SAM_BUK;
    public static Reg.Handle<Item> SAM_TOR;
    public static Reg.EntityHandle<EntityTargetDrone> TARGET_DRONE;
    public static Reg.Handle<ItemTargetCaller> TARGET_CALLER;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void register() {
        Reg.Namespace ns = Reg.in(MOD_ID);
        BUK = ns.blockItem("turret_buk", TurretBuk::new, AirDefense::turretProps);
        TOR = ns.blockItem("turret_tor", TurretTor::new, AirDefense::turretProps);
        BUK_BE = ns.<BlockEntityBuk>be("turret_buk",
                        () -> new BlockEntityType<>(BlockEntityBuk::new, Set.<Block>of(BUK.get())))
                .ticks(BlockEntityBuk::tickClient, BlockEntityBuk::tickServer);
        TOR_BE = ns.<BlockEntityTor>be("turret_tor",
                        () -> new BlockEntityType<>(BlockEntityTor::new, Set.<Block>of(TOR.get())))
                .ticks(BlockEntityTor::tickClient, BlockEntityTor::tickServer);
        SAM = ns.entity("sam", () -> EntityType.Builder.<EntitySAM>of(EntitySAM::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.4f, 0.4f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sam"))));
        TARGET_DRONE = ns.entity("target_drone", () -> EntityType.Builder.<EntityTargetDrone>of(EntityTargetDrone::new, MobCategory.MISC)
                .noLootTable().fireImmune().sized(1.5f, 1.5f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("target_drone"))));
        TARGET_CALLER = ns.item("target_caller", ItemTargetCaller::new, () -> new Item.Properties().stacksTo(1));
        SAM_BUK = ns.item("sam_buk", Item::new, () -> new Item.Properties().stacksTo(4));
        SAM_TOR = ns.item("sam_tor", Item::new, () -> new Item.Properties().stacksTo(8));

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(out -> {
            out.accept(new ItemStack(BUK.get()));
            out.accept(new ItemStack(TOR.get()));
            out.accept(new ItemStack(SAM_BUK.get()));
            out.accept(new ItemStack(SAM_TOR.get()));
            out.accept(new ItemStack(TARGET_CALLER.get()));
        });
    }

    private static BlockBehaviour.Properties turretProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 360.0f)
                .noOcclusion().requiresCorrectToolForDrops();
    }
}
