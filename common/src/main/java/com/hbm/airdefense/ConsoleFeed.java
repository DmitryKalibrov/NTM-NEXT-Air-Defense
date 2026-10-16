package com.hbm.airdefense;

import com.hbm.api.entity.RadarEntry;
import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import com.hbm.util.ChunkUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a console screen shows of the device it is bound to, polled by the console twice a second and sent with
 * its block update. Positions are relative to the device, in blocks.
 *
 * @param kind   "radar", "buk", "tor", "pantsir", or "lost" when the device isn't there (unloaded or gone)
 * @param pos    where the device is (packed BlockPos)
 * @param state  launcher state key: off, power, ammo, ready, lock, launch
 * @param blips  x, z, y, entity id, blip level ({@link com.hbm.api.entity.IRadarDetectableNT}, -1 = air target)
 *               per contact: radar contacts, or the targets inside a launcher's zone
 * @param lock   x, z, y of the launcher's locked target (empty when none)
 * @param manual a launcher in manual mode (see {@link BlockEntitySAMBase#manual})
 * @param filter a launcher's target filter bits ({@link BlockEntitySAMBase#filterBits})
 * @param events radar: recent launches / hits / misses in its range, {@link #EVENT} ints each: x, z, y, type
 *               ({@link SamLog}), target entity id, game time, launcher x, launcher z (relative to the radar)
 * @param charge launcher: stored energy, percent of full
 * @param clip   launcher: its last hit's TV picture ({@link BlockEntitySAMBase#clip}), recorded at clipTime
 * @param name   the name tag the player gave the device, "" when none
 */
public record ConsoleFeed(String kind, long pos, String state, int range, int ammo, int capacity, int kills, List<Integer> blips, List<Integer> lock, boolean manual,
                          int filter, List<Integer> events, List<Integer> clip, long clipTime, int charge, String name) {

    public static final int BLIP = 5, EVENT = 8;  // ints per contact in blips, per event in events

    public static final Codec<ConsoleFeed> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(ConsoleFeed::kind),
            Codec.LONG.optionalFieldOf("pos", 0L).forGetter(ConsoleFeed::pos),
            Codec.STRING.optionalFieldOf("state", "").forGetter(ConsoleFeed::state),
            Codec.INT.optionalFieldOf("range", 0).forGetter(ConsoleFeed::range),
            Codec.INT.optionalFieldOf("ammo", 0).forGetter(ConsoleFeed::ammo),
            Codec.INT.optionalFieldOf("capacity", 0).forGetter(ConsoleFeed::capacity),
            Codec.INT.optionalFieldOf("kills", 0).forGetter(ConsoleFeed::kills),
            Codec.INT.listOf().optionalFieldOf("blips", List.of()).forGetter(ConsoleFeed::blips),
            Codec.INT.listOf().optionalFieldOf("lock", List.of()).forGetter(ConsoleFeed::lock),
            Codec.BOOL.optionalFieldOf("manual", false).forGetter(ConsoleFeed::manual),
            Codec.INT.optionalFieldOf("filter", 0).forGetter(ConsoleFeed::filter),
            Codec.INT.listOf().optionalFieldOf("events", List.of()).forGetter(ConsoleFeed::events),
            Codec.INT.listOf().optionalFieldOf("clip", List.of()).forGetter(ConsoleFeed::clip),
            Codec.LONG.optionalFieldOf("clipTime", 0L).forGetter(ConsoleFeed::clipTime),
            Codec.INT.optionalFieldOf("charge", 0).forGetter(ConsoleFeed::charge),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ConsoleFeed::name)
    ).apply(i, ConsoleFeed::new));



    /** Which elements take which device: radar scopes a radar, monitors a launcher. */
    public static boolean wantsRadar(String element) {
        return element.equals("crt_ppi") || element.equals("crt_ppi_desk");
    }

    public static boolean wantsLauncher(String element) {
        return element.equals("readiness") || element.equals("tv_screen");
    }

    /** Screens and the stick that look through an optoelectronic station. */
    public static boolean wantsCamera(String element) {
        return element.equals("crt_small") || element.equals("crt_bank") || element.equals("tv_screen") || element.equals("joystick");
    }

    /** The binding key of a part of a screen element: each screen of a monitor bank has its own. */
    public static String key(String element, String part) {
        return element.equals("crt_bank") && part.startsWith("screen") ? part : "";
    }

    /** What the player calls the device: its name tag, else its type. */
    public Component label() {
        return named(kind, name);
    }

    /** Тор, or Тор ("Name") once the player named it. */
    public static Component named(String kind, String name) {
        Component type = Component.translatable("gui.ntm_air_defense.device." + kind);
        return name.isEmpty() ? type : Component.translatable("gui.ntm_air_defense.device.named", type, name);
    }

    /** An optoelectronic station goes by the name of the launcher it serves. */
    private static String name(@Nullable BlockEntity be) {
        if (be instanceof BlockEntityEos eos && eos.launcher != null) be = ChunkUtil.blockEntityIfLoaded(eos.getLevel(), eos.launcher);
        return be instanceof Nameable n && n.getCustomName() != null ? n.getCustomName().getString() : "";
    }

    public static ConsoleFeed poll(Level level, BlockPos pos) {
        if (ChunkUtil.blockEntityIfLoaded(BlockEntityMachineRadar.class, level, pos) instanceof BlockEntityMachineRadar radar) {
            List<Integer> blips = new ArrayList<>();
            for (RadarEntry e : radar.entries) {
                blips.add(e.posX - pos.getX());
                blips.add(e.posZ - pos.getZ());
                blips.add(e.posY - pos.getY());
                blips.add(e.entityID);
                blips.add(e.blipLevel);
            }
            return new ConsoleFeed("radar", pos.asLong(), "", radar.getRange(), 0, 0, 0, blips, List.of(), false, 0,
                    events(level, pos, radar.getRange()), List.of(), 0, 0, name(radar));
        }
        if (ChunkUtil.blockEntityIfLoaded(BlockEntitySAMBase.class, level, pos) instanceof BlockEntitySAMBase sam) {
            String kind = sam instanceof BlockEntityBuk ? "buk" : sam instanceof BlockEntityTor ? "tor" : sam instanceof BlockEntityPantsir ? "pantsir" : "sam";
            Vec3 at = sam.getTurretPos();
            double reach = sam.reach(sam.range());
            List<Integer> blips = new ArrayList<>();
            for (Entity e : sam.seen()) {
                Vec3 p = sam.getEntityPos(e);
                if (sam.isAirTarget(e) && p.distanceTo(at) <= reach) {
                    rel(blips, p, pos);
                    blips.add(e.getId());
                    blips.add(-1);
                }
            }
            List<Integer> lock = new ArrayList<>();
            if (sam.target != null && sam.target.isAlive()) rel(lock, sam.getEntityPos(sam.target), pos);
            String state = !sam.isOn() ? "off" : !sam.hasPower() ? "power" : sam.ammo <= 0 ? "ammo"
                    : sam.inFlightCount() > 0 ? "launch" : sam.target != null ? "lock" : "ready";
            return new ConsoleFeed(kind, pos.asLong(), state, (int) reach, sam.ammo, sam.capacity(), sam.stattrak, blips, lock, sam.manual,
                    sam.filterBits(), List.of(), sam.clip, sam.clipTime,
                    (int) (100 * sam.getPower() / Math.max(1, sam.getMaxPower())), name(sam));
        }
        if (ChunkUtil.blockEntityIfLoaded(BlockEntityEos.class, level, pos) instanceof BlockEntityEos eos) {
            return new ConsoleFeed("eos", pos.asLong(), eos.mode == BlockEntityEos.TRACK ? "track" : eos.mode == BlockEntityEos.FREE ? "free" : "slave",
                    0, 0, 0, 0, List.of(), List.of(), false, 0, List.of(), List.of(), 0, 0, name(eos));
        }
        return new ConsoleFeed("lost", pos.asLong(), "", 0, 0, 0, 0, List.of(), List.of(), false, 0, List.of(), List.of(), 0, 0, "");
    }

    /** The last half minute of missile events inside a radar's range. */
    private static List<Integer> events(Level level, BlockPos pos, double range) {
        List<Integer> out = new ArrayList<>();
        for (SamLog.Event e : SamLog.near(level, pos, range, level.getGameTime() - 600)) {
            rel(out, e.at(), pos);
            out.add(e.type());
            out.add(e.target());
            out.add((int) e.time());
            out.add(e.launcher().getX() - pos.getX());
            out.add(e.launcher().getZ() - pos.getZ());
        }
        return out;
    }

    private static void rel(List<Integer> out, Vec3 p, BlockPos o) {
        out.add((int) Math.round(p.x - o.getX()));
        out.add((int) Math.round(p.z - o.getZ()));
        out.add((int) Math.round(p.y - o.getY()));
    }
}
