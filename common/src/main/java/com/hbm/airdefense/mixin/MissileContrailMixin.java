package com.hbm.airdefense.mixin;

import com.hbm.airdefense.client.ParticleSmoke;
import com.hbm.entity.missile.EntityMissileBaseNT;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client: NTM's exhaust is additive flame that is gone in ~3 s, so a far missile left no track. Lay a
 * long-lived, thick column of our volumetric smoke (ParticleSmoke, visible out to the LOD range) along its
 * path, flame-lit at first like the SAMs' (moved over from NTM x Distant Horizons Compat, which drew it
 * itself and still does on NTM 1.0.2).
 */
@Mixin(EntityMissileBaseNT.class)
abstract class MissileContrailMixin {

    /** Blocks between puffs (closer than their size: one column), how long a puff lives. */
    @Unique private static final double SPACING = 1.2;
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
        RandomSource r = self.getRandom();
        int n = (int) Math.min(Math.ceil(len / SPACING), 24);
        for (int i = 0; i < n; i++) {
            Vec3 p = last.lerp(pos, (i + r.nextDouble()) / n);
            ParticleSmoke.spawn(self.level(), p.x, p.y, p.z, r.nextGaussian() * 0.03, r.nextGaussian() * 0.03, r.nextGaussian() * 0.03,
                    1.2f + r.nextFloat() * 0.4f, 7f + r.nextFloat() * 3f, LIFETIME - r.nextInt(200), 6, 0.82f + r.nextFloat() * 0.1f, 0.7f);
        }
    }
}
