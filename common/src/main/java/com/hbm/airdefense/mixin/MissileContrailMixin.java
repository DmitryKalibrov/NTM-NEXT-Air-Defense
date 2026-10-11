package com.hbm.airdefense.mixin;

import com.hbm.entity.missile.EntityMissileBaseNT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client: NTM's exhaust is additive flame that is gone in ~3 s, so a far missile left no track. Lay a
 * long-lived, large smoke trail along its path that reads from far away (moved over from
 * NTM x Distant Horizons Compat, which drew it itself and still does on NTM 1.0.2).
 */
@Mixin(EntityMissileBaseNT.class)
abstract class MissileContrailMixin {

    /** Blocks between puffs, and how long a puff lives. */
    @Unique private static final double SPACING = 3;
    @Unique private static final int LIFETIME = 30 * 20;
    @Unique private static final boolean DH_COMPAT_TRAILS = classExists("com.hbm.dhcompat.DistantFx");

    @Unique private Vec3 ntmad$last;

    @Unique
    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, MissileContrailMixin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ntmad$contrail(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide() || DH_COMPAT_TRAILS) return;
        Vec3 pos = self.position(), last = ntmad$last;
        ntmad$last = pos;
        if (last == null) return;
        double len = pos.distanceTo(last);
        if (len < 0.1) return;
        int n = (int) Math.min(Math.ceil(len / SPACING), 20);
        for (int i = 1; i <= n; i++) {
            Vec3 p = last.lerp(pos, (double) i / n);
            Particle puff = Minecraft.getInstance().particleEngine
                    .createParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, p.x, p.y, p.z, 0, 0, 0);
            if (puff == null) continue;
            puff.scale(8F);
            puff.setLifetime(LIFETIME);
        }
    }
}
