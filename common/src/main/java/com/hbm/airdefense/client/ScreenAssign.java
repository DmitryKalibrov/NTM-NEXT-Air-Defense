package com.hbm.airdefense.client;

import com.hbm.airdefense.ConsoleFunctions;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.NbtControlPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Setting up a control with the mounting kit: its label, and the function it takes from the remembered device
 * (or the console's own ones). A click on a function assigns it; with "add to group" ticked the remembered device
 * joins the devices the control already drives. The current function is shown in amber.
 */
public class ScreenAssign extends Screen {

    private static final int W = 240, ROW = 12;
    private static final int STEEL = 0xFF3A4436, STEEL_HI = 0xFF5A6650, STEEL_LO = 0xFF1C231B, PLATE = 0xFF0E120E,
            AMBER = 0xFFE8A53A, PHOSPHOR = 0xFF8CFFA0, PHOSPHOR_DIM = 0xFF3F8F52, SCREEN = 0xFF0A170D;

    private final BlockPos console;
    private final int index;
    private final String part, device, label;
    private final @Nullable String current;
    private final boolean canGroup;
    private final List<String> rows = new ArrayList<>();  // functions, then "" = clear
    private boolean group;
    private int left, top, h;
    private @Nullable EditBox name;

    private static final String BIND = "\u0001bind";
    /** Screen part to bind to the remembered device (a monitor, scope or readiness column), null when it can't be. */
    private final @Nullable String bindPart;

    public ScreenAssign(BlockPos console, int index, String part, String device, List<String> choices, @Nullable String current, String label,
                        @Nullable String bindPart) {
        super(Component.translatable("gui.ntm_air_defense.assign.title"));
        this.console = console;
        this.index = index;
        this.part = part;
        this.device = device;
        this.current = current;
        this.label = label.startsWith("#") ? "" : label;
        this.canGroup = current != null && !device.isEmpty() && !ConsoleFunctions.internal(current) && choices.contains(current);
        this.bindPart = bindPart;
        if (bindPart != null) rows.add(BIND);
        rows.addAll(choices);
        if (!choices.isEmpty() || current != null) rows.add("");
    }

    /** The remembered device by its name tag, else its type. */
    private Component deviceLabel(net.minecraft.world.item.ItemStack kit) {
        List<BlockPos> sel = com.hbm.airdefense.ItemConsoleKit.selection(kit);
        return sel.isEmpty() ? Component.translatable("gui.ntm_air_defense.device." + device)
                : com.hbm.airdefense.ItemConsoleKit.label(kit, sel.get(0), device);
    }

    @Override
    protected void init() {
        h = 70 + rows.size() * ROW + (canGroup ? 14 : 0) + (hint() ? 12 : 0) + 8;
        left = (width - W) / 2;
        top = (height - h) / 2;
        name = new EditBox(font, left + 12, top + 40, W - 24, 14, Component.translatable("gui.ntm_air_defense.assign.label"));
        name.setMaxLength(16);
        name.setValue(label);
        addRenderableWidget(name);
        setInitialFocus(name);
    }

    /** No device remembered, but this element could take one's functions. */
    private boolean hint() {
        return device.isEmpty() && !rows.isEmpty();
    }

    private int rowsTop() {
        return top + 70 + (canGroup ? 14 : 0) + (hint() ? 12 : 0);
    }

    private int rowY(int i) {
        return rowsTop() + i * ROW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(left - 1, top - 1, left + W + 1, top + h + 1, STEEL_LO);
        g.fill(left, top, left + W, top + h, STEEL);
        g.fill(left, top, left + W, top + 1, STEEL_HI);
        g.fill(left + 6, top + 6, left + W - 6, top + 22, PLATE);
        g.text(font, title, left + 12, top + 10, AMBER, false);
        net.minecraft.world.item.ItemStack kit = net.minecraft.client.Minecraft.getInstance().player.getMainHandItem();
        String grp = com.hbm.airdefense.ItemConsoleKit.selectedGroup(kit);
        Component dev = !grp.isEmpty() ? Component.translatable("gui.ntm_air_defense.assign.group_name", grp, com.hbm.airdefense.ItemConsoleKit.groups(kit).get(grp).size())
                : device.isEmpty() ? Component.translatable("gui.ntm_air_defense.assign.no_device") : deviceLabel(kit);
        g.text(font, dev, left + W - 12 - font.width(dev), top + 10, PHOSPHOR, false);
        g.text(font, Component.translatable("gui.ntm_air_defense.assign.label"), left + 12, top + 29, 0xFFD8D8CC, false);
        g.fill(left + 6, top + 58, left + W - 6, top + h - 6, SCREEN);
        int y = top + 62;
        if (hint()) {
            g.text(font, Component.translatable("gui.ntm_air_defense.assign.remember"), left + 12, y, PHOSPHOR_DIM, false);
            y += 12;
        }
        if (canGroup) {
            g.text(font, Component.literal(group ? "[x] " : "[ ] ").append(Component.translatable("gui.ntm_air_defense.assign.group")), left + 12, y, group ? AMBER : PHOSPHOR, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            String fn = rows.get(i);
            int ry = rowY(i);
            if (mx >= left + 8 && mx < left + W - 8 && my >= ry - 2 && my < ry + ROW - 2) g.fill(left + 8, ry - 2, left + W - 8, ry + ROW - 2, 0x3060FF80);
            Component c = fn.equals(BIND) ? Component.translatable("gui.ntm_air_defense.assign.bind", deviceLabel(net.minecraft.client.Minecraft.getInstance().player.getMainHandItem()))
                    : fn.isEmpty() ? Component.translatable("gui.ntm_air_defense.assign.clear") : Component.translatable("gui.ntm_air_defense.fn." + fn);
            g.text(font, c, left + 14, ry, fn.equals(current) ? AMBER : fn.isEmpty() ? PHOSPHOR_DIM : PHOSPHOR, false);
        }
        super.extractRenderState(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int gy = top + 62 + (hint() ? 12 : 0);
        if (canGroup && my >= gy - 2 && my < gy + 10 && mx >= left + 8 && mx < left + W - 8) {
            group = !group;
            return true;
        }
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (mx >= left + 8 && mx < left + W - 8 && my >= y - 2 && my < y + ROW - 2) {
                saveLabel();
                if (rows.get(i).equals(BIND)) {
                    CompoundTag t = op("bind");
                    t.putString("part", bindPart);
                    PacketWire.sendToServer(new NbtControlPayload(console, t));
                    super.onClose();
                    return true;
                }
                CompoundTag t = op("assign");
                t.putString("fn", rows.get(i));
                t.putBoolean("add", group);
                PacketWire.sendToServer(new NbtControlPayload(console, t));
                super.onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private CompoundTag op(String op) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        t.putInt("i", index);
        t.putString("part", part);
        return t;
    }

    /** A changed label goes before anything else (an emptied one gives the key back to its function's name). */
    private void saveLabel() {
        if (name == null || name.getValue().strip().equals(label)) return;
        CompoundTag t = op("label");
        t.putString("text", name.getValue());
        PacketWire.sendToServer(new NbtControlPayload(console, t));
    }

    @Override
    public void onClose() {
        saveLabel();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {}
}
