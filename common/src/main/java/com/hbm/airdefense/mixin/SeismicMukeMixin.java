package com.hbm.airdefense.mixin;

import com.hbm.airdefense.Seismic;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mini nukes (Fatman, small warheads). */
@Mixin(targets = "com.hbm.explosion.ExplosionNukeSmall")
abstract class SeismicMukeMixin {

    @Inject(method = "explode", at = @At("HEAD"))
    private static void ntmad$seismic(CallbackInfo ci,
                                      @Local(argsOnly = true) Level level,
                                      @Local(argsOnly = true, ordinal = 0) double x,
                                      @Local(argsOnly = true, ordinal = 1) double y,
                                      @Local(argsOnly = true, ordinal = 2) double z) {
        Seismic.blast(level, x, y, z, 150);
    }
}
