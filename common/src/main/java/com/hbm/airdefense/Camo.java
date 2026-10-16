package com.hbm.airdefense;

import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Paint scheme and vegetation net of an air-defense system, kept in the core block's state (saved and synced for
 * free; the static hull picks its baked texture through blockstate variants, the renderers swap theirs).
 */
public enum Camo implements StringRepresentable {
    GREEN, DESERT, WINTER;

    public static final EnumProperty<Camo> CAMO = EnumProperty.create("camo", Camo.class);
    public static final BooleanProperty NET = BooleanProperty.create("net");

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The texture of this scheme: "buk" -> textures/block/buk.png, buk_desert.png, buk_winter.png. */
    public Identifier texture(String base) {
        return AirDefense.id("textures/block/" + base + (this == GREEN ? "" : "_" + getSerializedName()) + ".png");
    }

    /** The net's leaves, tinted to match the scheme. */
    public Identifier netTexture() {
        return AirDefense.id("textures/block/camo_net_" + getSerializedName() + ".png");
    }

    public static Camo of(BlockState state) {
        return state.hasProperty(CAMO) ? state.getValue(CAMO) : GREEN;
    }

    public static boolean net(BlockState state) {
        return state.hasProperty(NET) && state.getValue(NET);
    }
}
