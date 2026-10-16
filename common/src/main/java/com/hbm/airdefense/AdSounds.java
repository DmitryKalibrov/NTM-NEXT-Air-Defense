package com.hbm.airdefense;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/** Our sound events. Direct holders: no registry entry needed, sounds.json is enough. */
public final class AdSounds {

    private AdSounds() {}

    public static final Holder<SoundEvent> TOR_HATCH = of("tor.hatch");
    public static final Holder<SoundEvent> TOR_EJECT = of("tor.eject");
    public static final Holder<SoundEvent> TOR_IGNITION = of("tor.ignition");
    public static final Holder<SoundEvent> TOR_FLIGHT = of("tor.flight");
    public static final Holder<SoundEvent> TOR_POWER = of("tor.power");
    public static final Holder<SoundEvent> TOR_RADAR = of("tor.radar");
    public static final Holder<SoundEvent> CONSOLE_HUM = of("console.hum");
    public static final Holder<SoundEvent> EXPLOSION_SMALL = of("sam.explosion_small");

    private static Holder<SoundEvent> of(String name) {
        return Holder.direct(SoundEvent.createVariableRangeEvent(AirDefense.id(name)));
    }
}
