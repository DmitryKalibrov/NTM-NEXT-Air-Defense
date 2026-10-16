package com.hbm.airdefense.client;

import com.hbm.airdefense.EntitySAM;
import com.hbm.airdefense.ItemTargetCaller;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.ItemControlPayload;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The debug tool's settings: what use does (mode), how far, and the mode's option, each a row of  < value >;
 * then the world switch that makes every SAM launch fail. Every change goes to the server at once.
 */
public class ScreenDebugTool extends Screen {

    private static final String P = "gui.ntm_air_defense.debug.";
    private final CompoundTag s;

    private ScreenDebugTool(CompoundTag settings) {
        super(Component.translatable("item.ntm_air_defense.target_caller"));
        this.s = settings;
    }

    public static void open(CompoundTag settings) {
        Minecraft.getInstance().setScreenAndShow(new ScreenDebugTool(settings));
    }

    @Override
    protected void init() {
        int mode = get("mode", ItemTargetCaller.MODES, 0);
        int y = height / 2 - 60;
        row(y, "mode", ItemTargetCaller.MODES, 0, i -> Component.translatable(P + "mode." + i));
        row(y += 24, "dist", ItemTargetCaller.DISTANCES.length, 4,
                i -> Component.translatable(P + (mode == ItemTargetCaller.STRIKE ? "launch_dist" : "dist"), ItemTargetCaller.DISTANCES[i]));
        y += 24;
        switch (mode) {
            case ItemTargetCaller.BLAST -> row(y, "blast", ItemTargetCaller.BLASTS.length, 1,
                    i -> Component.translatable(P + "blast", (int) ItemTargetCaller.BLASTS[i]));
            case ItemTargetCaller.GUNFIRE -> row(y, "weapon", ItemTargetCaller.WEAPONS.size(), 0,
                    i -> Component.translatable(P + "weapon." + ItemTargetCaller.WEAPONS.get(i).key()));
            case ItemTargetCaller.AIR_DEFENCE -> row(y, "sam", EntitySAM.Kind.values().length, 1,
                    i -> Component.translatable(P + "sam." + EntitySAM.Kind.values()[i].name().toLowerCase(java.util.Locale.ROOT)));
            default -> row(y, "missile", ItemTargetCaller.MISSILES.size(), 0,
                    i -> BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("hbm", ItemTargetCaller.MISSILES.get(i))).getDescription());
        }
        boolean duds = s.getBooleanOr("duds", false);
        addRenderableWidget(Button.builder(Component.translatable(P + "duds_" + (duds ? "on" : "off")), b -> {
            CompoundTag t = new CompoundTag();
            t.putBoolean("toggle_duds", true);
            PacketWire.sendToServer(new ItemControlPayload(t));
            s.putBoolean("duds", !duds);  // the server confirms in chat
            rebuildWidgets();
        }).bounds(width / 2 - 110, y + 36, 220, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, y + 64, 100, 20).build());
    }

    private int get(String key, int size, int def) {
        return Math.clamp(s.getIntOr(key, def), 0, size - 1);
    }

    /** One setting:  <  value  >  (a click on the value steps forward too). */
    private void row(int y, String key, int size, int def, IntFunction<Component> label) {
        int cur = get(key, size, def);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> set(key, Math.floorMod(cur - 1, size)))
                .bounds(width / 2 - 110, y, 20, 20).build());
        addRenderableWidget(Button.builder(label.apply(cur), b -> set(key, (cur + 1) % size))
                .bounds(width / 2 - 86, y, 172, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> set(key, (cur + 1) % size))
                .bounds(width / 2 + 90, y, 20, 20).build());
    }

    private void set(String key, int value) {
        s.putInt(key, value);
        CompoundTag t = s.copy();
        t.remove("duds");
        PacketWire.sendToServer(new ItemControlPayload(t));
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
