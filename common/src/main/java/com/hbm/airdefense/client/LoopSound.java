package com.hbm.airdefense.client;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Client: a looping sound that follows its source and stops itself once the source says so. */
public final class LoopSound extends AbstractTickableSoundInstance {

    private final BooleanSupplier keep;
    private final Supplier<Vec3> at;
    private final float full;
    private final int fadeIn, fadeOut;
    private int age, fading = -1;

    /** volume above 1 widens the range: heard out to 16 * volume blocks. */
    private LoopSound(SoundEvent sound, float volume, int fadeIn, int fadeOut, BooleanSupplier keep, Supplier<Vec3> at) {
        super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.keep = keep;
        this.at = at;
        this.looping = true;
        this.delay = 0;
        this.full = volume;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
        this.volume = fadeIn > 0 ? 0.001f : volume;
        tick();
    }

    public static LoopSound play(SoundEvent sound, float volume, BooleanSupplier keep, Supplier<Vec3> at) {
        return play(sound, volume, 0, 0, keep, at);
    }

    /** Swells in over fadeIn ticks and dies away over fadeOut ticks once its source lets go. */
    public static LoopSound play(SoundEvent sound, float volume, int fadeIn, int fadeOut, BooleanSupplier keep, Supplier<Vec3> at) {
        LoopSound s = new LoopSound(sound, volume, fadeIn, fadeOut, keep, at);
        Minecraft.getInstance().getSoundManager().play(s);
        return s;
    }

    /** Still audible (not stopped by its source or kicked out by the sound engine). */
    public boolean playing() {
        return !isStopped() && Minecraft.getInstance().getSoundManager().isActive(this);
    }

    @Override
    public void tick() {
        age++;
        if (fading < 0 && !keep.getAsBoolean()) {
            if (fadeOut <= 0) {
                stop();
                return;
            }
            fading = 0;
        }
        if (fading >= 0) {
            if (++fading >= fadeOut) {
                stop();
                return;
            }
            volume = Math.max(0.001f, full * (1 - (float) fading / fadeOut));
        } else if (fadeIn > 0) {
            float k = Math.min(1, (float) age / fadeIn);
            volume = Math.max(0.001f, full * k * k * (3 - 2 * k));  // smoothstep swell
        }
        Vec3 p = at.get();
        x = p.x;
        y = p.y;
        z = p.z;
    }
}
