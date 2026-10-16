package com.hbm.airdefense.mixin;

import com.hbm.airdefense.BlockEntitySAMBase;
import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import com.hbm.inventory.container.BlockEntityMenu;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.NbtControlPayload;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A name field above the window of our launchers and of radars: the name the consoles and the kit then
 * show, as Тор ("Name"). Sent on Enter and when the window closes; an emptied field takes the name away.
 */
@Mixin(AbstractContainerScreen.class)
abstract class DeviceNameMixin extends Screen {

    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected int leftPos, topPos;
    @Shadow @Final protected int imageWidth;

    @Unique private @Nullable EditBox ntmad$name;
    @Unique private String ntmad$sent = "";

    DeviceNameMixin(Component title) {
        super(title);
    }

    @Unique
    private @Nullable BlockEntityMachineBase ntmad$device() {
        return menu instanceof BlockEntityMenu<?> m && (m.blockEntity() instanceof BlockEntitySAMBase || m.blockEntity() instanceof BlockEntityMachineRadar)
                ? (BlockEntityMachineBase) m.blockEntity() : null;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ntmad$addName(CallbackInfo ci) {
        BlockEntityMachineBase be = ntmad$device();
        ntmad$name = null;
        if (be == null) return;
        ntmad$sent = be.getCustomName() == null ? "" : be.getCustomName().getString();
        ntmad$name = new EditBox(font, leftPos + 2, topPos - 18, Math.min(imageWidth - 4, 120), 14, Component.translatable("gui.ntm_air_defense.name"));
        ntmad$name.setMaxLength(16);
        ntmad$name.setValue(ntmad$sent);
        ntmad$name.setHint(Component.translatable("gui.ntm_air_defense.name"));
        addRenderableWidget(ntmad$name);
    }

    /** Typing goes to the field, not to the inventory key or the machine's own shortcuts. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ntmad$type(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ntmad$name == null || !ntmad$name.isFocused() || event.isEscape()) return;
        if (event.key() == 257 || event.key() == 335) {  // Enter, keypad Enter (space is part of a name)
            ntmad$save();
            ntmad$name.setFocused(false);
            setFocused(null);
        } else {
            ntmad$name.keyPressed(event);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void ntmad$close(CallbackInfo ci) {
        ntmad$save();
    }

    @Unique
    private void ntmad$save() {
        BlockEntityMachineBase be = ntmad$device();
        if (ntmad$name == null || be == null) return;
        String text = ntmad$name.getValue().strip();
        if (text.equals(ntmad$sent)) return;
        ntmad$sent = text;
        CompoundTag t = new CompoundTag();
        t.putString("callsign", text);
        PacketWire.sendToServer(new NbtControlPayload(be.getBlockPos(), t));
    }
}
