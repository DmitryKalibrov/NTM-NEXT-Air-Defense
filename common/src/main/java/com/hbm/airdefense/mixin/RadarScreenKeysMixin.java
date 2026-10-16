package com.hbm.airdefense.mixin;

import com.hbm.client.gui.ScreenMachineRadar;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the name field (DeviceNameMixin) is typed in, digits are letters of the name, not the radar's link keys. */
@Mixin(ScreenMachineRadar.class)
abstract class RadarScreenKeysMixin extends Screen {

    RadarScreenKeysMixin(Component title) {
        super(title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ntmad$typing(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!(getFocused() instanceof EditBox box) || !box.isFocused() || event.isEscape()) return;
        if (event.key() == 257 || event.key() == 335) setFocused(null);  // Enter: done (saved when the window closes)
        else box.keyPressed(event);
        cir.setReturnValue(true);
    }
}
