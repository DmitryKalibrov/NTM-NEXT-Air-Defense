package com.hbm.airdefense;

import com.hbm.blockentity.BlockEntityMachineBase;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * The name a player gives a launcher or radar in its window (DeviceNameMixin), shown as Тор ("Name").
 * NTM's turrets and radar list their own sync units without the machine name, so it never reached the
 * client: these put NTM's name unit (61) back for them.
 */
public final class DeviceName {

    public static final int UNIT = 61;
    public static final long BIT = 1L << UNIT;

    private DeviceName() {}

    public static void write(BlockEntityMachineBase be, ByteBuf out) {
        ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(new RegistryFriendlyByteBuf(out, be.getLevel().registryAccess()),
                Optional.ofNullable(be.getCustomName()));
    }

    public static void read(BlockEntityMachineBase be, ByteBuf in) {
        be.setCustomName(ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(new RegistryFriendlyByteBuf(in, be.getLevel().registryAccess())).orElse(null));
    }

    /** "callsign" from the name field; "" takes the name away. True when the packet was one. */
    public static boolean receive(BlockEntityMachineBase be, CompoundTag data) {
        if (!data.contains("callsign")) return false;
        String n = data.getStringOr("callsign", "").strip();
        be.setCustomName(n.isEmpty() ? null : Component.literal(n.length() > 16 ? n.substring(0, 16) : n));
        be.setChanged();
        return true;
    }
}
