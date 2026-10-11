package com.hbm.airdefense.mixin;

import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's explosion particles, for an explode() call with our own sound. */
@Mixin(Level.class)
public interface LevelAccessor {

    @Accessor("DEFAULT_EXPLOSION_BLOCK_PARTICLES")
    static WeightedList<ExplosionParticleInfo> ntmad$blockParticles() {
        throw new AssertionError();
    }
}
