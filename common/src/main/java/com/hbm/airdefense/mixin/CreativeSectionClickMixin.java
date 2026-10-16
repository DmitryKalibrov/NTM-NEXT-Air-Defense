package com.hbm.airdefense.mixin;

import com.hbm.airdefense.AirDefense;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The creative tab's section fillers can't be picked up. */
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeSectionClickMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void ntmad$fillerClick(Slot slot, int index, int button, ContainerInput input, CallbackInfo ci) {
        if (slot != null && slot.getItem().is(AirDefense.SECTION.get())) ci.cancel();
    }
}
