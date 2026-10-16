package com.hbm.airdefense;

import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import com.hbm.blocks.multiblock.MultiblockSurface;
import com.hbm.items.IItemControlReceiver;
import com.hbm.items.ModDataComponents;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The panel mounting kit. On a console its clicks are taken by the client (mount, set up, remove); here: shift-click
 * a radar or a launcher to remember it. The kit keeps the remembered devices (custom data "devices"), each maybe in
 * a named group, and a selection: one device (NTM's radar linker component) or a whole group ("group"), which the
 * next setup on a console uses: a control assigned with a group selected drives every launcher in it. The memory
 * is edited from the client (shift + wheel, the memory screen) through NTM's item control packet.
 */
public class ItemConsoleKit extends Item implements IItemControlReceiver {

    public static final int MEMORY = 16;

    /**
     * A remembered device: where, what it was ("radar", "buk", ...), its group ("" = none) and the name it had when
     * the server last saw it ({@link #inventoryTick}), so a device out of the client's view still shows by name.
     */
    public record Device(long pos, String kind, String group, String name) {

        public Component label() {
            return ConsoleFeed.named(kind, name);
        }
    }

    /** What the device at pos is: asked of the device when it's loaded, else what the kit remembers ("lost" when neither). */
    public static String kind(ItemStack stack, Level level, BlockPos pos) {
        String k = ConsoleFeed.poll(level, pos).kind();
        if (!k.equals("lost")) return k;
        for (Device d : memory(stack)) if (d.pos() == pos.asLong()) return d.kind();
        return k;
    }

    /** The label of a remembered device, else just the type. */
    public static Component label(ItemStack stack, BlockPos pos, String kind) {
        for (Device d : memory(stack)) if (d.pos() == pos.asLong()) return d.label();
        return ConsoleFeed.named(kind, "");
    }

    /** Every two seconds the server brings the remembered names up to date for devices loaded on it. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @org.jspecify.annotations.Nullable EquipmentSlot slot) {
        if (level.getGameTime() % 40 != 0) return;
        List<Device> mem = memory(stack), now = new ArrayList<>(mem);
        for (int i = 0; i < now.size(); i++) {
            Device d = now.get(i);
            ConsoleFeed f = ConsoleFeed.poll(level, BlockPos.of(d.pos()));
            if (!f.kind().equals("lost")) now.set(i, new Device(d.pos(), d.kind(), d.group(), f.name()));
        }
        if (!now.equals(mem)) store(stack, now, tag(stack).getStringOr("group", ""));
    }

    public ItemConsoleKit(Properties props) {
        super(props);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static List<Device> memory(ItemStack stack) {
        List<Device> out = new ArrayList<>();
        ListTag list = tag(stack).getListOrEmpty("devices");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            out.add(new Device(t.getLongOr("pos", 0), t.getStringOr("kind", "?"), t.getStringOr("group", ""), t.getStringOr("name", "")));
        }
        return out;
    }

    /** Groups in order of first appearance, with their members. */
    public static Map<String, List<Device>> groups(ItemStack stack) {
        Map<String, List<Device>> out = new LinkedHashMap<>();
        for (Device d : memory(stack)) if (!d.group().isEmpty()) out.computeIfAbsent(d.group(), k -> new ArrayList<>()).add(d);
        return out;
    }

    /** The selected group, "" when a single device is selected. */
    public static String selectedGroup(ItemStack stack) {
        String g = tag(stack).getStringOr("group", "");
        return groups(stack).containsKey(g) ? g : "";
    }

    /** What a setup on a console takes: the selected group's devices, or the selected device alone. */
    public static List<BlockPos> selection(ItemStack stack) {
        String g = selectedGroup(stack);
        if (!g.isEmpty()) return groups(stack).get(g).stream().map(d -> BlockPos.of(d.pos())).toList();
        Long at = stack.get(ModDataComponents.RADAR_LINKER_TARGET.get());
        return at == null ? List.of() : List.of(BlockPos.of(at));
    }

    private static void store(ItemStack stack, List<Device> devices, String group) {
        ListTag list = new ListTag();
        for (Device d : devices) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", d.pos());
            t.putString("kind", d.kind());
            if (!d.group().isEmpty()) t.putString("group", d.group());
            if (!d.name().isEmpty()) t.putString("name", d.name());
            list.add(t);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put("devices", list);
            tag.putString("group", group);
        });
    }

    /** Core of the radar, launcher or optoelectronic station at pos (any block of it), null when it's none of them. */
    public static @org.jspecify.annotations.Nullable BlockPos deviceCore(Level level, BlockPos pos) {
        BlockPos core = MultiblockSurface.coreOfAny(level, pos);
        if (core == null) core = pos;
        BlockEntity be = level.getBlockEntity(core);
        return be instanceof BlockEntityMachineRadar || be instanceof BlockEntitySAMBase || be instanceof BlockEntityEos ? core : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        // a plain click on an optoelectronic station slaves it to the launcher selected in the kit
        if (!player.isShiftKeyDown() && level.getBlockEntity(ctx.getClickedPos()) instanceof BlockEntityEos eos) {
            if (!level.isClientSide()) {
                BlockPos sam = selection(ctx.getItemInHand()).stream()
                        .filter(p -> level.getBlockEntity(p) instanceof BlockEntitySAMBase).findFirst().orElse(null);
                if (sam == null) {
                    player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.eos.need_launcher"));
                } else {
                    eos.launcher = sam;
                    eos.mode = BlockEntityEos.SLAVE;
                    eos.setChanged();
                    player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.eos.linked",
                            ConsoleFeed.poll(level, sam).label(), sam.toShortString()));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos core = deviceCore(level, ctx.getClickedPos());
        if (core == null) return InteractionResult.PASS;
        BlockEntity be = level.getBlockEntity(core);
        if (!level.isClientSide()) {
            ItemStack stack = ctx.getItemInHand();
            String kind = be instanceof BlockEntityMachineRadar ? "radar" : ConsoleFeed.poll(level, core).kind();
            List<Device> mem = new ArrayList<>(memory(stack));
            long at = core.asLong();
            String group = mem.stream().filter(d -> d.pos() == at).map(Device::group).findFirst().orElse("");
            mem.removeIf(d -> d.pos() == at);
            Device remembered = new Device(at, kind, group, ConsoleFeed.poll(level, core).name());
            mem.add(0, remembered);  // newest first, keeps its group
            while (mem.size() > MEMORY) mem.remove(mem.size() - 1);
            store(stack, mem, "");
            stack.set(ModDataComponents.RADAR_LINKER_TARGET.get(), at);
            player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.console_kit.remembered",
                    remembered.label(), core.toShortString()));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * From the client: "select" a device (pos) or a group (group), "forget" a device, "group": the devices in
     * "members" (long array) become exactly the group "group", "ungroup": dissolve "group".
     */
    @Override
    public void receiveControl(ServerPlayer player, ItemStack stack, CompoundTag data) {
        long pos = data.getLongOr("pos", 0);
        String name = data.getStringOr("group", "").strip();
        if (name.length() > 16) name = name.substring(0, 16);
        List<Device> mem = new ArrayList<>(memory(stack));
        String sel = selectedGroup(stack);
        switch (data.getStringOr("op", "")) {
            case "select" -> {
                if (!name.isEmpty()) {
                    if (!groups(stack).containsKey(name)) return;
                    sel = name;
                    stack.set(ModDataComponents.RADAR_LINKER_TARGET.get(), groups(stack).get(name).get(0).pos());
                } else {
                    if (mem.stream().noneMatch(d -> d.pos() == pos)) return;
                    sel = "";
                    stack.set(ModDataComponents.RADAR_LINKER_TARGET.get(), pos);
                }
            }
            case "forget" -> {
                mem.removeIf(d -> d.pos() == pos);
                Long cur = stack.get(ModDataComponents.RADAR_LINKER_TARGET.get());
                if (cur != null && cur == pos) {
                    if (mem.isEmpty()) stack.remove(ModDataComponents.RADAR_LINKER_TARGET.get());
                    else stack.set(ModDataComponents.RADAR_LINKER_TARGET.get(), mem.get(0).pos());
                }
            }
            case "group" -> {
                if (name.isEmpty()) return;
                List<Long> members = new ArrayList<>();
                for (long l : data.getLongArray("members").orElse(new long[0])) members.add(l);
                for (int i = 0; i < mem.size(); i++) {
                    Device d = mem.get(i);
                    boolean in = members.contains(d.pos());
                    if (in) mem.set(i, new Device(d.pos(), d.kind(), name, d.name()));
                    else if (d.group().equals(name)) mem.set(i, new Device(d.pos(), d.kind(), "", d.name()));
                }
            }
            case "ungroup" -> {
                for (int i = 0; i < mem.size(); i++) {
                    Device d = mem.get(i);
                    if (d.group().equals(name)) mem.set(i, new Device(d.pos(), d.kind(), "", d.name()));
                }
                if (sel.equals(name)) sel = "";
            }
            default -> {
                return;
            }
        }
        store(stack, mem, sel);
    }
}
