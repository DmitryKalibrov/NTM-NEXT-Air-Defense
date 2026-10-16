package com.hbm.airdefense.client;

import com.hbm.airdefense.BlockEntityConsole;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.NbtControlPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The owner's access list for the whole console: players already let in, and the players nearby; a click lets
 * one in or takes them out. Admitted players may work the controls (a key switch still has to be on).
 */
public class ScreenAccess extends Screen {

    private static final int W = 220, ROW = 12;
    private static final int STEEL = 0xFF3A4436, STEEL_HI = 0xFF5A6650, STEEL_LO = 0xFF1C231B, PLATE = 0xFF0E120E,
            AMBER = 0xFFE8A53A, PHOSPHOR = 0xFF8CFFA0, PHOSPHOR_DIM = 0xFF3F8F52, SCREEN = 0xFF0A170D;

    private final BlockPos console;
    private int left, top, h;

    public ScreenAccess(BlockPos console) {
        super(Component.translatable("gui.ntm_air_defense.access.title"));
        this.console = console;
    }

    /** Everyone to show: admitted first (true), then players within 32 blocks (false). */
    private Map<UUID, Object[]> rows() {
        Map<UUID, Object[]> out = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(console) instanceof BlockEntityConsole be)) return out;
        be.access.forEach((id, name) -> out.put(id, new Object[]{name, true}));
        for (Player p : mc.level.players()) {
            if (p == mc.player || out.containsKey(p.getUUID()) || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(console)) > 32 * 32) continue;
            out.put(p.getUUID(), new Object[]{p.getName().getString(), false});
        }
        return out;
    }

    @Override
    protected void init() {
        h = 46 + Math.max(1, rows().size()) * ROW + 10;
        left = (width - W) / 2;
        top = (height - h) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(left - 1, top - 1, left + W + 1, top + h + 1, STEEL_LO);
        g.fill(left, top, left + W, top + h, STEEL);
        g.fill(left, top, left + W, top + 1, STEEL_HI);
        g.fill(left + 6, top + 6, left + W - 6, top + 22, PLATE);
        g.text(font, title, left + 12, top + 10, AMBER, false);
        g.fill(left + 6, top + 28, left + W - 6, top + h - 6, SCREEN);
        Map<UUID, Object[]> rows = rows();
        if (rows.isEmpty()) g.text(font, Component.translatable("gui.ntm_air_defense.access.nobody"), left + 12, top + 44, PHOSPHOR_DIM, false);
        int y = top + 44;
        for (Object[] r : rows.values()) {
            boolean in = (Boolean) r[1];
            if (mx >= left + 8 && mx < left + W - 8 && my >= y - 2 && my < y + ROW - 2) g.fill(left + 8, y - 2, left + W - 8, y + ROW - 2, 0x3060FF80);
            g.text(font, Component.literal(in ? "[x] " : "[ ] ").append((String) r[0]), left + 14, y, in ? AMBER : PHOSPHOR, false);
            y += ROW;
        }
        g.text(font, Component.translatable("gui.ntm_air_defense.access.hint"), left + 12, top + 32, PHOSPHOR_DIM, false);
        super.extractRenderState(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int y = top + 44;
        for (Map.Entry<UUID, Object[]> r : rows().entrySet()) {
            if (event.x() >= left + 8 && event.x() < left + W - 8 && event.y() >= y - 2 && event.y() < y + ROW - 2) {
                CompoundTag t = new CompoundTag();
                t.putString("op", "access");
                t.putString("uuid", r.getKey().toString());
                t.putString("name", (String) r.getValue()[0]);
                t.putBoolean("allow", !(Boolean) r.getValue()[1]);
                PacketWire.sendToServer(new NbtControlPayload(console, t));
                return true;
            }
            y += ROW;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {}
}
