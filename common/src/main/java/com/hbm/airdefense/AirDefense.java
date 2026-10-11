package com.hbm.airdefense;

import com.hbm.inventory.recipes.ModRecipes;
import com.hbm.registration.Addon;
import com.hbm.registration.Reg;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NTM addon: register() runs inside NTM's registration window, before caps are baked. Fabric finds it
 * through the "hbm:addon" entrypoint, NeoForge through NeoForgeRegistrar.addon.
 */
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
    public static Reg.EntityHandle<EntitySpentStage> SPENT_STAGE;
    public static Reg.EntityHandle<EntityTracer> TRACER;
    public static Reg.Handle<ItemTargetCaller> TARGET_CALLER;
    public static Reg.Handle<Item> ABM_53T6;
    public static Reg.Handle<TurretPantsir> PANTSIR;
    public static Reg.Handle<MachineDefenseAssembler> ASSEMBLER;
    public static Reg.BeHandle<BlockEntityDefenseAssembler> ASSEMBLER_BE;
    public static Reg.BeHandle<BlockEntityPantsir> PANTSIR_BE;
    public static Reg.Handle<Item> SAM_PANTSIR;
    public static Reg.Handle<RadarNebo> NEBO;
    public static Reg.BeHandle<BlockEntityNebo> NEBO_BE;

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
        ASSEMBLER = ns.blockItem("defense_assembler", MachineDefenseAssembler::new, AirDefense::turretProps);
        ASSEMBLER_BE = ns.<BlockEntityDefenseAssembler>be("defense_assembler",
                        () -> new BlockEntityType<>(BlockEntityDefenseAssembler::new, Set.<Block>of(ASSEMBLER.get())))
                .ticks(BlockEntityDefenseAssembler::tickClient, BlockEntityDefenseAssembler::tickServer);
        // the launchers are built in the hall, from their own recipe type
        ModRecipes.addon(DefenseAssemblerRecipes.INSTANCE);
        PANTSIR = ns.blockItem("turret_pantsir", TurretPantsir::new, AirDefense::turretProps);
        PANTSIR_BE = ns.<BlockEntityPantsir>be("turret_pantsir",
                        () -> new BlockEntityType<>(BlockEntityPantsir::new, Set.<Block>of(PANTSIR.get())))
                .ticks(BlockEntityPantsir::tickClient, BlockEntityPantsir::tickServer);
        NEBO = ns.blockItem("radar_nebo", RadarNebo::new, AirDefense::turretProps);
        NEBO_BE = ns.<BlockEntityNebo>be("radar_nebo",
                        () -> new BlockEntityType<>(BlockEntityNebo::new, Set.<Block>of(NEBO.get())))
                .ticks(BlockEntityNebo::tickClient, BlockEntityNebo::tickServer);
        SAM = ns.entity("sam", () -> EntityType.Builder.<EntitySAM>of(EntitySAM::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.4f, 0.4f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sam"))));
        SPENT_STAGE = ns.entity("spent_stage", () -> EntityType.Builder.<EntitySpentStage>of(EntitySpentStage::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.8f, 0.8f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("spent_stage"))));
        // tracers move on the client from their spawn velocity: no position updates needed
        TRACER = ns.entity("tracer", () -> EntityType.Builder.<EntityTracer>of(EntityTracer::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.1f, 0.1f)
                .clientTrackingRange(16).updateInterval(100)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("tracer"))));
        TARGET_DRONE = ns.entity("target_drone", () -> EntityType.Builder.<EntityTargetDrone>of(EntityTargetDrone::new, MobCategory.MISC)
                .noLootTable().fireImmune().sized(1.5f, 1.5f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("target_drone"))));
        TARGET_CALLER = ns.item("target_caller", ItemTargetCaller::new, () -> new Item.Properties().stacksTo(1));
        SAM_BUK = ns.item("sam_buk", Item::new, () -> new Item.Properties().stacksTo(4));
        SAM_TOR = ns.item("sam_tor", Item::new, () -> new Item.Properties().stacksTo(8));
        SAM_PANTSIR = ns.item("sam_pantsir", Item::new, () -> new Item.Properties().stacksTo(12));
        ABM_53T6 = ns.item("abm_53t6", Item::new, () -> new Item.Properties().stacksTo(1));
    }

    /** The creative tab; each loader registers it its own way. */
    public static CreativeModeTab.Builder tab(CreativeModeTab.Builder builder) {
        return builder.title(Component.translatable("itemGroup.ntm_air_defense"))
                .icon(() -> new ItemStack(PANTSIR.get()))
                .displayItems((params, out) -> {
                    out.accept(new ItemStack(ASSEMBLER.get()));
                    out.accept(new ItemStack(BUK.get()));
                    out.accept(new ItemStack(TOR.get()));
                    out.accept(new ItemStack(SAM_BUK.get()));
                    out.accept(new ItemStack(SAM_TOR.get()));
                    out.accept(new ItemStack(PANTSIR.get()));
                    out.accept(new ItemStack(SAM_PANTSIR.get()));
                    out.accept(new ItemStack(ABM_53T6.get()));
                    out.accept(new ItemStack(NEBO.get()));
                    out.accept(new ItemStack(TARGET_CALLER.get()));
                });
    }

    private static BlockBehaviour.Properties turretProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 360.0f)
                .noOcclusion().requiresCorrectToolForDrops();
    }
}
