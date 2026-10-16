package com.hbm.airdefense;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What the missiles did lately (server side): launches, hits and misses, for the radar screens' marks and logs.
 * ponytail: one global ring of the last 128 events, fine for one server; per level if that ever matters.
 */
public final class SamLog {

    public static final int LAUNCH = 0, HIT = 1, MISS = 2;

    public record Event(ResourceKey<Level> dim, long time, BlockPos launcher, Vec3 at, int type, int target) {}

    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>();

    private SamLog() {}

    static void add(Level level, BlockPos launcher, Vec3 at, int type, int target) {
        EVENTS.addLast(new Event(level.dimension(), level.getGameTime(), launcher, at, type, target));
        while (EVENTS.size() > 128) EVENTS.removeFirst();
    }

    /** Events in this level since `since`, within `range` blocks (horizontally) of `centre`. */
    static List<Event> near(Level level, BlockPos centre, double range, long since) {
        List<Event> out = new ArrayList<>();
        for (Event e : EVENTS) {
            if (e.time >= since && e.dim == level.dimension() && Math.hypot(e.at.x - centre.getX(), e.at.z - centre.getZ()) <= range) out.add(e);
        }
        return out;
    }
}
