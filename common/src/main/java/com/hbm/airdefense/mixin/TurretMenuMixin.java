package com.hbm.airdefense.mixin;

import com.hbm.airdefense.BlockEntitySAMBase;
import com.hbm.airdefense.RestrictedSlot;
import com.hbm.inventory.container.MenuTurretBase;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NTM's turret menu: for SAM launchers, make the turret slots honour canPlaceItem (one ammo slot). */
@Mixin(MenuTurretBase.class)
abstract class TurretMenuMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/Slot"))
    private Slot ntmad$slot(Container container, int index, int x, int y, Operation<Slot> original) {
        return container instanceof BlockEntitySAMBase
                ? new RestrictedSlot(container, index, x, y)
                : original.call(container, index, x, y);
    }
}
