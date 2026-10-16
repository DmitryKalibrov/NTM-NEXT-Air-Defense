package com.hbm.airdefense;

import com.hbm.airdefense.client.LoopSound;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/** Client: looping sounds that follow missiles and launchers. Called from their client ticks only. */
final class SamSounds {

    private SamSounds() {}

    /** One loop per (source, slot); restarted if the engine dropped it, at most once a second. */
    private static final Map<Object, Slot[]> LOOPS = new WeakHashMap<>();

    private static final class Slot {
        LoopSound sound;
        long retryAt;
    }

    /** Motor roar riding on the missile while it burns: Minecraft does the distance falloff. */
    static void flight(EntitySAM sam) {
        keep(sam, 0, 1, AdSounds.TOR_FLIGHT, 12f, () -> !sam.isRemoved() && sam.motorOn(), sam::position, sam.tickCount);
    }

    /** Tor launcher switched on: gas-turbine power unit and the spinning search antenna. */
    static void tor(BlockEntityTor tor) {
        BooleanSupplier on = () -> !tor.isRemoved() && tor.isOn();
        Vec3 c = Vec3.atCenterOf(tor.getBlockPos());
        long now = tor.getLevel() == null ? 0 : tor.getLevel().getGameTime();
        keep(tor, 0, 2, AdSounds.TOR_POWER, 2f, 60, 50, on, () -> c, now);  // the turbine spools up and down
        keep(tor, 1, 2, AdSounds.TOR_RADAR, 1.5f, 40, 30, on, () -> c.add(0, 3.5, 0), now);
    }

    /** A console unit's faint electrical hum, from the middle of the unit. */
    static void console(BlockEntityConsole c) {
        long now = c.getLevel() == null ? 0 : c.getLevel().getGameTime();
        keep(c, 0, 1, AdSounds.CONSOLE_HUM, 1f, () -> !c.isRemoved() && c.soundAt != null, () -> c.soundAt == null ? Vec3.atCenterOf(c.getBlockPos()) : c.soundAt, now);
    }

    private static void keep(Object src, int i, int slots, Holder<SoundEvent> ev, float volume,
                             BooleanSupplier on, Supplier<Vec3> at, long now) {
        keep(src, i, slots, ev, volume, 0, 0, on, at, now);
    }

    private static void keep(Object src, int i, int slots, Holder<SoundEvent> ev, float volume, int fadeIn, int fadeOut,
                             BooleanSupplier on, Supplier<Vec3> at, long now) {
        if (!on.getAsBoolean()) return; // a running loop stops itself
        Slot s = LOOPS.computeIfAbsent(src, k -> new Slot[slots])[i];
        if (s == null) s = LOOPS.get(src)[i] = new Slot();
        if (s.sound != null && s.sound.playing() || now < s.retryAt) return;
        s.retryAt = now + 20;
        s.sound = LoopSound.play(ev.value(), volume, fadeIn, fadeOut, on, at);
    }
}
