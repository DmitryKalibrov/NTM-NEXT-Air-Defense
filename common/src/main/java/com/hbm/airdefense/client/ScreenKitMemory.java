package com.hbm.airdefense.client;

import com.hbm.airdefense.ConsoleFeed;
import com.hbm.airdefense.ItemConsoleKit;
import com.hbm.items.ModDataComponents;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.ItemControlPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The mounting kit's memory. Groups on top (a click selects the whole group, the cross dissolves it), the
 * remembered devices below (a click selects one, the box marks it, the cross forgets it). Marked devices plus a
 * name make a group ("to group"); pick an existing group's name to change its members. The selection is what the
 * next setup on a console takes: a control assigned with a group selected drives every launcher in it.
 */
public class ScreenKitMemory extends Screen {

    private static final int W = 300, ROW = 13;
    private static final int STEEL = 0xFF3A4436, STEEL_HI = 0xFF5A6650, STEEL_LO = 0xFF1C231B, PLATE = 0xFF0E120E,
            AMBER = 0xFFE8A53A, PHOSPHOR = 0xFF8CFFA0, PHOSPHOR_DIM = 0xFF3F8F52, SCREEN = 0xFF0A170D, RED = 0xFFFF5A3A,
            KEY = 0xFF2A2E2A, INK = 0xFFD8D8CC;

    private final Set<Long> marked = new HashSet<>();
    private int left, top, h;
    private @Nullable EditBox name;

    public ScreenKitMemory() {
        super(Component.translatable("gui.ntm_air_defense.kit_memory.title"));
    }

    private static ItemStack kit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
    }

    @Override
    protected void init() {
        ItemStack kit = kit();
        int rows = Math.max(1, ItemConsoleKit.groups(kit).size()) + Math.max(1, ItemConsoleKit.memory(kit).size());
        h = 40 + rows * ROW + 24 + 46;
        left = (width - W) / 2;
        top = (height - h) / 2;
        name = new EditBox(font, left + 12, top + h - 40, W - 110, 14, Component.translatable("gui.ntm_air_defense.kit_memory.name"));
        name.setMaxLength(16);
        addRenderableWidget(name);
    }

    private int rows = -1;

    /** The list grew or shrank (the server answered): lay out again, keeping the typed name. */
    @Override
    public void tick() {
        ItemStack kit = kit();
        int n = Math.max(1, ItemConsoleKit.groups(kit).size()) + Math.max(1, ItemConsoleKit.memory(kit).size());
        if (rows >= 0 && n != rows) {
            String typed = name == null ? "" : name.getValue();
            rebuildWidgets();
            if (name != null) name.setValue(typed);
        }
        rows = n;
    }

    private int groupsTop() {
        return top + 42;
    }

    private int devicesTop() {
        return groupsTop() + Math.max(1, ItemConsoleKit.groups(kit()).size()) * ROW + 14;
    }

    private int buttonX() {
        return left + W - 92;
    }

    private int buttonY() {
        return top + h - 41;
    }

    private boolean row(double mx, double my, int y) {
        return mx >= left + 8 && mx < left + W - 8 && my >= y - 3 && my < y + ROW - 3;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack kit = kit();
        List<ItemConsoleKit.Device> mem = ItemConsoleKit.memory(kit);
        Map<String, List<ItemConsoleKit.Device>> groups = ItemConsoleKit.groups(kit);
        String sg = ItemConsoleKit.selectedGroup(kit);
        Long cur = kit.get(ModDataComponents.RADAR_LINKER_TARGET.get());
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(left - 1, top - 1, left + W + 1, top + h + 1, STEEL_LO);
        g.fill(left, top, left + W, top + h, STEEL);
        g.fill(left, top, left + W, top + 1, STEEL_HI);
        g.fill(left + 6, top + 6, left + W - 6, top + 22, PLATE);
        g.text(font, title, left + 12, top + 10, AMBER, false);
        g.fill(left + 6, top + 28, left + W - 6, top + h - 48, SCREEN);

        g.text(font, Component.translatable("gui.ntm_air_defense.kit_memory.groups"), left + 12, groupsTop() - 11, INK, false);
        if (groups.isEmpty()) g.text(font, Component.translatable("gui.ntm_air_defense.kit_memory.no_groups"), left + 22, groupsTop(), PHOSPHOR_DIM, false);
        int y = groupsTop();
        for (Map.Entry<String, List<ItemConsoleKit.Device>> e : groups.entrySet()) {
            boolean active = e.getKey().equals(sg);
            if (row(mx, my, y)) g.fill(left + 8, y - 3, left + W - 8, y + ROW - 3, 0x3060FF80);
            g.text(font, (active ? "> " : "  ") + e.getKey(), left + 10, y, active ? AMBER : PHOSPHOR, false);
            StringBuilder kinds = new StringBuilder();
            for (ItemConsoleKit.Device d : e.getValue()) kinds.append(d.label().getString()).append(' ');
            g.text(font, kinds.toString().strip(), left + 110, y, PHOSPHOR_DIM, false);
            g.text(font, "x", left + W - 18, y, mx >= left + W - 22 && row(mx, my, y) ? RED : 0xFF8A6A60, false);
            y += ROW;
        }

        g.text(font, Component.translatable("gui.ntm_air_defense.kit_memory.devices"), left + 12, devicesTop() - 11, INK, false);
        if (mem.isEmpty()) g.text(font, Component.translatable("gui.ntm_air_defense.kit_memory.empty"), left + 22, devicesTop(), PHOSPHOR_DIM, false);
        y = devicesTop();
        for (ItemConsoleKit.Device d : mem) {
            BlockPos pos = BlockPos.of(d.pos());
            boolean active = sg.isEmpty() && cur != null && cur == d.pos();
            if (row(mx, my, y)) g.fill(left + 8, y - 3, left + W - 8, y + ROW - 3, 0x3060FF80);
            int c = active ? AMBER : PHOSPHOR;
            g.text(font, marked.contains(d.pos()) ? "[x]" : "[ ]", left + 10, y, marked.contains(d.pos()) ? AMBER : PHOSPHOR_DIM, false);
            g.text(font, (active ? ">" : " "), left + 28, y, c, false);
            g.text(font, font.substrByWidth(d.label(), 54).getString(), left + 36, y, c, false);
            g.text(font, pos.toShortString(), left + 92, y, PHOSPHOR_DIM, false);
            if (!d.group().isEmpty()) g.text(font, d.group(), left + 178, y, PHOSPHOR_DIM, false);
            ConsoleFeed f = mc.level == null ? null : ConsoleFeed.poll(mc.level, pos);
            boolean here = f != null && !f.kind().equals("lost");
            Component st = !here ? Component.translatable("gui.ntm_air_defense.kit_memory.far")
                    : d.kind().equals("radar") ? Component.literal("") : Component.translatable("screen.ntm_air_defense.state." + f.state());
            g.text(font, st, left + 236, y, here ? PHOSPHOR_DIM : 0xFF6A6A60, false);
            g.text(font, "x", left + W - 18, y, mx >= left + W - 22 && row(mx, my, y) ? RED : 0xFF8A6A60, false);
            y += ROW;
        }

        // making a group from the marked devices
        int bx = buttonX(), by = buttonY();
        boolean ready = !marked.isEmpty() && name != null && !name.getValue().isBlank();
        g.fill(bx - 1, by - 1, bx + 81, by + 17, PLATE);
        g.fill(bx, by, bx + 80, by + 16, ready && mx >= bx && mx < bx + 80 && my >= by && my < by + 16 ? 0xFF3A403A : KEY);
        Component b = Component.translatable("gui.ntm_air_defense.kit_memory.make_group");
        g.text(font, b, bx + (80 - font.width(b)) / 2, by + 4, ready ? INK : 0xFF6A6A60, false);
        g.text(font, Component.translatable("gui.ntm_air_defense.kit_memory.hint"), left + 12, top + h - 20, INK, false);
        super.extractRenderState(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        ItemStack kit = kit();
        boolean cross = mx >= left + W - 22;
        int y = groupsTop();
        for (String gname : ItemConsoleKit.groups(kit).keySet()) {
            if (row(mx, my, y)) {
                if (cross) {
                    send("ungroup", 0, gname, null);
                } else {
                    select(0, gname);
                    onClose();
                }
                return true;
            }
            y += ROW;
        }
        y = devicesTop();
        for (ItemConsoleKit.Device d : ItemConsoleKit.memory(kit)) {
            if (row(mx, my, y)) {
                if (cross) {
                    send("forget", d.pos(), "", null);
                    marked.remove(d.pos());
                } else if (mx < left + 28) {
                    if (!marked.remove(d.pos())) marked.add(d.pos());
                    if (name != null && name.getValue().isBlank() && !d.group().isEmpty()) name.setValue(d.group());
                } else {
                    select(d.pos(), "");
                    onClose();
                }
                return true;
            }
            y += ROW;
        }
        if (name != null && !marked.isEmpty() && !name.getValue().isBlank()
                && mx >= buttonX() && mx < buttonX() + 80 && my >= buttonY() && my < buttonY() + 16) {
            List<Long> m = new ArrayList<>(marked);
            send("group", 0, name.getValue(), m.stream().mapToLong(Long::longValue).toArray());
            marked.clear();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Select a device (pos) or a group (non-empty name); shown at once, the server confirms. */
    static void select(long pos, String group) {
        ItemStack kit = kit();
        if (group.isEmpty()) kit.set(ModDataComponents.RADAR_LINKER_TARGET.get(), pos);
        send("select", pos, group, null);
    }

    static void send(String op, long pos, String group, long @Nullable [] members) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        t.putLong("pos", pos);
        t.putString("group", group);
        if (members != null) t.putLongArray("members", members);
        PacketWire.sendToServer(new ItemControlPayload(t));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {}
}
