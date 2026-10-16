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
    public static Reg.EntityHandle<EntitySAMWreck> SAM_WRECK;
    public static Reg.EntityHandle<EntityDebugEmitter> DEBUG_EMITTER;
    public static Reg.EntityHandle<EntityTracer> TRACER;
    public static Reg.EntityHandle<EntityBurst> BURST;
    public static Reg.Handle<ItemTargetCaller> TARGET_CALLER;
    public static Reg.Handle<Item> ABM_53T6;
    /** Creative tab layout filler: section titles and row padding, drawn as nothing. */
    public static Reg.Handle<Item> SECTION;
    public static Reg.Handle<TurretPantsir> PANTSIR;
    public static Reg.Handle<MachineDefenseAssembler> ASSEMBLER;
    public static Reg.BeHandle<BlockEntityDefenseAssembler> ASSEMBLER_BE;
    public static Reg.BeHandle<BlockEntityPantsir> PANTSIR_BE;
    public static Reg.Handle<Item> SAM_PANTSIR;
    public static Reg.Handle<RadarNebo> NEBO;
    public static Reg.BeHandle<BlockEntityNebo> NEBO_BE;
    public static Reg.Handle<BlockConsole> CONSOLE_DESK;
    public static Reg.Handle<BlockConsole> CONSOLE_PANEL;
    public static Reg.Handle<BlockConsole> CONSOLE_TILT;
    public static Reg.BeHandle<BlockEntityConsole> CONSOLE_BE;
    public static Reg.Handle<ItemConsoleKit> CONSOLE_KIT;
    public static Reg.Handle<BlockEos> EOS;
    public static Reg.BeHandle<BlockEntityEos> EOS_BE;
    public static Reg.Handle<ItemCamoSprayer> CAMO_SPRAYER;
    public static final java.util.List<Reg.Handle<BlockDecor>> DECOR = new java.util.ArrayList<>();

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
        CONSOLE_DESK = ns.blockItem("console_desk", p -> new BlockConsole(ConsoleGeometry.Kind.DESK, p), AirDefense::consoleProps);
        CONSOLE_PANEL = ns.blockItem("console_panel", p -> new BlockConsole(ConsoleGeometry.Kind.PANEL, p), AirDefense::consoleProps);
        CONSOLE_TILT = ns.blockItem("console_tilt", p -> new BlockConsole(ConsoleGeometry.Kind.TILT, p), AirDefense::consoleProps);
        CONSOLE_BE = ns.be("console", () -> new BlockEntityType<>(BlockEntityConsole::new,
                Set.<Block>of(CONSOLE_DESK.get(), CONSOLE_PANEL.get(), CONSOLE_TILT.get())));
        CONSOLE_KIT = ns.item("console_kit", ItemConsoleKit::new, () -> new Item.Properties().stacksTo(1));
        EOS = ns.blockItem("eos", BlockEos::new, AirDefense::turretProps);
        CAMO_SPRAYER = ns.item("camo_sprayer", ItemCamoSprayer::new, () -> new Item.Properties().stacksTo(1));
        decor(ns, "chair_operator", Block.box(4, 0, 4, 12, 15, 12), Block.box(4, 15, 12, 12, 24, 14));
        decor(ns, "chair_commander", Block.box(3, 0, 3, 13, 15, 13), Block.box(3, 15, 12, 13, 30, 15));
        decor(ns, "safe", Block.box(1.5, 0, 2.5, 14.5, 20, 13.5));
        decor(ns, "waste_bin", Block.box(4.5, 0, 4.5, 11.5, 8.5, 11.5));
        decor(ns, "extinguisher", Block.box(5, 0, 5, 11, 15, 11));
        decor(ns, "ficus", Block.box(4, 0, 4, 12, 7, 12), Block.box(3, 7, 3, 13, 28, 13));
        EOS_BE = ns.be("eos", () -> new BlockEntityType<>(BlockEntityEos::new, Set.<Block>of(EOS.get())));
        SAM = ns.entity("sam", () -> EntityType.Builder.<EntitySAM>of(EntitySAM::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.4f, 0.4f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sam"))));
        SPENT_STAGE = ns.entity("spent_stage", () -> EntityType.Builder.<EntitySpentStage>of(EntitySpentStage::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.8f, 0.8f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("spent_stage"))));
        SAM_WRECK = ns.entity("sam_wreck", () -> EntityType.Builder.<EntitySAMWreck>of(EntitySAMWreck::new, MobCategory.MISC)
                .noLootTable().fireImmune().sized(1.0f, 1.0f)
                .clientTrackingRange(10).updateInterval(100)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sam_wreck"))));
        DEBUG_EMITTER = ns.entity("debug_emitter", () -> EntityType.Builder.<EntityDebugEmitter>of(EntityDebugEmitter::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.1f, 0.1f)
                .clientTrackingRange(4).updateInterval(100)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("debug_emitter"))));
        // tracers move on the client from their spawn velocity: no position updates needed
        TRACER = ns.entity("tracer", () -> EntityType.Builder.<EntityTracer>of(EntityTracer::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.1f, 0.1f)
                .clientTrackingRange(16).updateInterval(100)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("tracer"))));
        BURST = ns.entity("burst", () -> EntityType.Builder.<EntityBurst>of(EntityBurst::new, MobCategory.MISC)
                .noLootTable().noSave().fireImmune().sized(0.1f, 0.1f)
                .clientTrackingRange(16).updateInterval(100)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("burst"))));
        TARGET_DRONE = ns.entity("target_drone", () -> EntityType.Builder.<EntityTargetDrone>of(EntityTargetDrone::new, MobCategory.MISC)
                .noLootTable().fireImmune().sized(1.5f, 1.5f)
                .clientTrackingRange(63).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id("target_drone"))));
        TARGET_CALLER = ns.item("target_caller", ItemTargetCaller::new, () -> new Item.Properties().stacksTo(1));
        SAM_BUK = ns.item("sam_buk", Item::new, () -> new Item.Properties().stacksTo(4));
        SAM_TOR = ns.item("sam_tor", Item::new, () -> new Item.Properties().stacksTo(8));
        SAM_PANTSIR = ns.item("sam_pantsir", Item::new, () -> new Item.Properties().stacksTo(12));
        ABM_53T6 = ns.item("abm_53t6", Item::new, () -> new Item.Properties().stacksTo(1));
        SECTION = ns.item("section", Item::new, () -> new Item.Properties());
    }

    /** The creative tab; each loader registers it its own way. Sections start on a new row under a title bar. */
    public static CreativeModeTab.Builder tab(CreativeModeTab.Builder builder) {
        return builder.title(Component.translatable("itemGroup.ntm_air_defense"))
                .icon(() -> new ItemStack(PANTSIR.get()))
                .displayItems((params, out) -> {
                    Sections sec = new Sections(out);
                    sec.section("systems", BUK.get(), TOR.get(), PANTSIR.get(), ASSEMBLER.get());
                    sec.section("ammo", SAM_BUK.get(), SAM_TOR.get(), SAM_PANTSIR.get());
                    sec.section("sensors", NEBO.get(), EOS.get());
                    sec.section("command", CONSOLE_DESK.get(), CONSOLE_PANEL.get(), CONSOLE_TILT.get(), CONSOLE_KIT.get());
                    sec.section("decor", DECOR.stream().map(h -> (net.minecraft.world.level.ItemLike) h.get()).toArray(net.minecraft.world.level.ItemLike[]::new));
                    sec.section("tools", CAMO_SPRAYER.get(), TARGET_CALLER.get());
                    sec.section("concepts", ABM_53T6.get());  // not playable yet
                });
    }

    /**
     * Lays the tab out in rows of 9: a title row of invisible {@link #SECTION} stacks (the creative screen draws the
     * bar, see CreativeSectionsMixin), then the items, the last row filled up with invisible stacks. Every filler is
     * a distinct stack (the tab refuses duplicates) and stays out of the search tab.
     */
    private static final class Sections {
        private final CreativeModeTab.Output out;
        private int n;

        Sections(CreativeModeTab.Output out) {
            this.out = out;
        }

        void section(String key, net.minecraft.world.level.ItemLike... items) {
            for (int i = 0; i < 9; i++) filler(i == 0 ? key : "");
            for (var item : items) out.accept(item);
            for (int i = items.length; i % 9 != 0; i++) filler("");
        }

        private void filler(String key) {
            ItemStack stack = new ItemStack(SECTION.get());
            int id = n++;
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> {
                t.putInt("i", id);
                if (!key.isEmpty()) t.putString("section", key);
            });
            stack.set(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,
                    new net.minecraft.world.item.component.TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            out.accept(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
    }

    private static void decor(Reg.Namespace ns, String name, net.minecraft.world.phys.shapes.VoxelShape... boxes) {
        var shape = net.minecraft.world.phys.shapes.Shapes.or(net.minecraft.world.phys.shapes.Shapes.empty(), boxes);
        DECOR.add(ns.blockItem(name, p -> new BlockDecor(shape, p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f).noOcclusion()));
    }

    private static BlockBehaviour.Properties consoleProps() {
        // dynamic shape: a console turns into a corner by its neighbours
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 360.0f).noOcclusion().dynamicShape()
                .lightLevel(s -> s.getValue(BlockConsole.LIT) ? 13 : 0);
    }

    private static BlockBehaviour.Properties turretProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 360.0f)
                .noOcclusion().requiresCorrectToolForDrops();
    }
}
