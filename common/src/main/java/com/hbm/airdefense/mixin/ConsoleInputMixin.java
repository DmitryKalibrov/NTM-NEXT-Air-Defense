package com.hbm.airdefense.mixin;

import com.hbm.airdefense.client.ConsoleClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Console hover every tick; a right click on a console element or with the mounting kit goes to the console. */
@Mixin(Minecraft.class)
abstract class ConsoleInputMixin {

    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void ntmad$consoleUse(CallbackInfo ci) {
        if (ConsoleClient.use()) {
            rightClickDelay = 4;  // as vanilla's use would: holding the button doesn't flip a switch every tick
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ntmad$consoleTick(CallbackInfo ci) {
        ConsoleClient.tick();
        ConsoleClient.card = ConsoleClient.hint();
    }
}
