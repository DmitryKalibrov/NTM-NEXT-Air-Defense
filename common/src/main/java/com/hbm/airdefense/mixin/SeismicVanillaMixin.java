package com.hbm.airdefense.mixin;

import com.hbm.airdefense.Seismic;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla-style explosion (TNT, creepers, SAM warheads, NTM's ExplosionLarge) ends in this ServerLevel.explode. */
@Mixin(ServerLevel.class)
abstract class SeismicVanillaMixin {

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At("HEAD"))
    private void ntmad$seismic(Entity source, DamageSource damage, ExplosionDamageCalculator calculator, double x, double y, double z,
                               float radius, boolean fire, Level.ExplosionInteraction interaction, ParticleOptions small,
                               ParticleOptions large, WeightedList<ExplosionParticleInfo> particles, Holder<SoundEvent> sound,
                               CallbackInfo ci) {
        Seismic.blast((ServerLevel) (Object) this, x, y, z, radius);
    }
}
