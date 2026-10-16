package com.hbm.airdefense.mixin;

import com.hbm.airdefense.Seismic;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nuclear blasts: every NTM mushroom cloud is spawned through statFac (scale ~100 per kiloton-ish step). */
@Mixin(targets = "com.hbm.entity.effect.EntityNukeTorex")
abstract class SeismicNukeMixin {

    @Inject(method = "statFac", at = @At("RETURN"))
    private static void ntmad$seismic(Level level, double x, double y, double z, float scale, CallbackInfoReturnable<?> cir) {
        Seismic.blast(level, x, y, z, 2000 * Math.clamp(scale * 0.01f, 0.25f, 5f));
    }
}
