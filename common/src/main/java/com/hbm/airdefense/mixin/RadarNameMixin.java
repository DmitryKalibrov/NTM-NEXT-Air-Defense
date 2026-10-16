package com.hbm.airdefense.mixin;

import com.hbm.airdefense.DeviceName;
import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every NTM radar (and the Nebo) takes a name from its window and sends it to the client ({@link DeviceName}). */
@Mixin(BlockEntityMachineRadar.class)
abstract class RadarNameMixin {

    @Inject(method = "syncUnitMask", at = @At("RETURN"), cancellable = true)
    private void ntmad$nameUnit(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(cir.getReturnValueJ() | DeviceName.BIT);
    }

    @Inject(method = "writeSyncUnit", at = @At("HEAD"), cancellable = true)
    private void ntmad$writeName(int unit, ByteBuf out, CallbackInfo ci) {
        if (unit != DeviceName.UNIT) return;
        DeviceName.write((BlockEntityMachineRadar) (Object) this, out);
        ci.cancel();
    }

    @Inject(method = "readSyncUnit", at = @At("HEAD"), cancellable = true)
    private void ntmad$readName(int unit, ByteBuf in, CallbackInfo ci) {
        if (unit != DeviceName.UNIT) return;
        DeviceName.read((BlockEntityMachineRadar) (Object) this, in);
        ci.cancel();
    }

    @Inject(method = "receiveControl(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), cancellable = true)
    private void ntmad$name(CompoundTag data, CallbackInfo ci) {
        if (DeviceName.receive((BlockEntityMachineRadar) (Object) this, data)) ci.cancel();
    }
}
