package com.hbm.airdefense.mixin;

import com.hbm.airdefense.Seismic;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTM's own explosion engine: missiles, bombs, grenades, machines going critical. */
@Mixin(targets = "com.hbm.explosion.vanillant.ExplosionVNT")
abstract class SeismicVntMixin {

    @Shadow public Level world;
    @Shadow public double posX;
    @Shadow public double posY;
    @Shadow public double posZ;
    @Shadow public float size;

    @Inject(method = "explode", at = @At("HEAD"))
    private void ntmad$seismic(CallbackInfo ci) {
        Seismic.blast(world, posX, posY, posZ, size);
    }
}
