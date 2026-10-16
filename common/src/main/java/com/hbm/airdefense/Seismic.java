package com.hbm.airdefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Blasts felt by the seismographs on consoles. The server hands each console with a seismograph a short list of
 * quakes (P and S onsets, ground motion there, source magnitude); the client synthesises the trace from them,
 * so a recording costs one block update per blast, however far away it went off.
 */
public final class Seismic {

    /** One blast as recorded at one console: onsets in game ticks, amplitude there, source magnitude. */
    public record Quake(long p, long s, float amp, float mag) {
        static final Codec<Quake> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("p").forGetter(Quake::p),
                Codec.LONG.fieldOf("s").forGetter(Quake::s),
                Codec.FLOAT.fieldOf("amp").forGetter(Quake::amp),
                Codec.FLOAT.fieldOf("mag").forGetter(Quake::mag)
        ).apply(i, Quake::new));
    }

    /** Server consoles that may carry a seismograph (checked at each blast). */
    static final Set<BlockEntityConsole> STATIONS = ConcurrentHashMap.newKeySet();

    // P and S waves through rock, blocks per tick (3000 and 1700 m/s)
    private static final double VP = 150, VS = 85;

    private Seismic() {}

    /**
     * A blast of `energy` (vanilla TNT = 4, a nuke thousands) at x y z. Ground motion falls off with distance;
     * a quake too faint to lift the pen off the noise is not sent.
     */
    public static void blast(Level level, double x, double y, double z, double energy) {
        if (!(level instanceof ServerLevel) || energy <= 0) return;
        long now = level.getGameTime();
        float mag = (float) Math.log10(energy) + 2;
        for (BlockEntityConsole be : STATIONS) {
            if (be.getLevel() != level || be.isRemoved() || !be.hasSeismograph()) continue;
            double d = Math.sqrt(be.getBlockPos().distToCenterSqr(x, y, z));
            float amp = (float) (energy / Math.pow(1 + d / 48, 1.7));
            if (amp < 0.02f) continue;
            be.quake(new Quake(now + (long) (d / VP), now + (long) (d / VS), amp, mag));
        }
    }

    /** Ground motion at time t (ticks): small P onset, bigger S train with a long coda, a little noise. */
    public static float motion(List<Quake> quakes, float t, int seed) {
        float v = 0.04f * (float) (Math.sin(t * 2.3 + seed) * Math.sin(t * 0.71 + seed * 0.3));
        for (Quake q : quakes) {
            float dp = t - q.p(), ds = t - q.s();
            if (dp > 0) v += 0.3f * q.amp() * (float) (Math.exp(-dp / 25) * Math.sin(dp * 1.9 + seed));
            if (ds > 0) v += q.amp() * (float) (Math.exp(-ds / 45) * Math.sin(ds * 1.1 + seed * 0.7) * (1 + 0.3 * Math.sin(ds * 0.37)));
        }
        return v;
    }

    /** How hard the ground shakes at t (for the meter and the alarm lamp). */
    public static float envelope(List<Quake> quakes, float t) {
        float e = 0;
        for (Quake q : quakes) {
            float dp = t - q.p(), ds = t - q.s();
            if (dp > 0) e = Math.max(e, 0.3f * q.amp() * (float) Math.exp(-dp / 25));
            if (ds > 0) e = Math.max(e, q.amp() * (float) Math.exp(-ds / 45));
        }
        return e;
    }

    /** Pen deflection: soft-limited, a big quake swings the pen to the edge of the paper rather than off it. */
    public static float pen(float motion) {
        return motion / (1 + Math.abs(motion));
    }
}
