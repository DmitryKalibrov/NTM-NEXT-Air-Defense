package com.hbm.airdefense.client;

import com.hbm.airdefense.Camo;
import com.hbm.airdefense.ItemCamoSprayer;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.ItemControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** The spray gun's picker: one button per paint scheme, then the vegetation net; the current one is marked. */
public class ScreenCamo extends Screen {

    private final String current;

    private ScreenCamo(String current) {
        super(Component.translatable("item.ntm_air_defense.camo_sprayer"));
        this.current = current;
    }

    public static void open(String current) {
        Minecraft.getInstance().setScreenAndShow(new ScreenCamo(current));
    }

    @Override
    protected void init() {
        String[] opts = new String[Camo.values().length + 1];
        for (Camo c : Camo.values()) opts[c.ordinal()] = c.getSerializedName();
        opts[opts.length - 1] = ItemCamoSprayer.NET;
        int y = height / 2 - opts.length * 12;
        for (String o : opts) {
            Component label = Component.translatable("gui.ntm_air_defense.camo." + o);
            if (o.equals(current)) label = Component.literal("> ").append(label).append(" <");
            addRenderableWidget(Button.builder(label, b -> pick(o)).bounds(width / 2 - 70, y, 140, 20).build());
            y += 24;
        }
    }

    private void pick(String o) {
        CompoundTag t = new CompoundTag();
        t.putString("camo", o);
        PacketWire.sendToServer(new ItemControlPayload(t));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
