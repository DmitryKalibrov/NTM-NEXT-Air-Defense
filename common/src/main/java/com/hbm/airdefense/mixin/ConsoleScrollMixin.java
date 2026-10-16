package com.hbm.airdefense.mixin;

import com.hbm.airdefense.client.ConsoleClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shift + wheel with the mounting kit switches its current device instead of the hotbar slot. */
@Mixin(MouseHandler.class)
abstract class ConsoleScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void ntmad$kitScroll(long window, double dx, double dy, CallbackInfo ci) {
        if (ConsoleClient.scroll(dy)) ci.cancel();
    }
}
