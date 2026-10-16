package com.hbm.airdefense.mixin;

import com.hbm.airdefense.client.ScreenEos;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looking through an optoelectronic station: its zoom sets the field of view. */
@Mixin(Camera.class)
abstract class CameraFovMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void ntmad$eosFov(float partial, CallbackInfoReturnable<Float> cir) {
        if (ScreenEos.active != null) cir.setReturnValue(ScreenEos.active.fov());
    }
}
