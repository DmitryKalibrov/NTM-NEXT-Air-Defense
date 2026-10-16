package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityConsole;
import com.hbm.airdefense.ConsoleFeed;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.NbtControlPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * Sitting at a radar scope: the radar's own indicator unit fills the screen. The case is a rendered panel
 * (tools/blender/radar_gui.py); the round tube shows a PPI whose contacts are painted only as the sweep passes
 * them and then fade in the phosphor, the small tube lists targets, the locked one's formular and a log.
 * Range selector and brightness knob turn (click / right click / wheel), a click on the tube puts the marker,
 * LOCK takes the contact nearest it (or double-click it), RESET drops it, OFF or Esc switches the unit off.
 */
public class ScreenRadar extends Screen {

    private static final Identifier FACE = AirDefense.id("textures/gui/radar_console.png");
    private static final int TEX_W = 1280, TEX_H = 720;
    // panel layout in panel units (16 x 9, y up), see radar_gui.py
    private static final float CX = 4.6f, CY = 4.7f, TUBE = 3.45f, CH_X0 = 9.8f, CH_Y0 = 4.0f, CH_X1 = 15.0f, CH_Y1 = 8.0f,
            KNOB_Y = 1.55f, RANGE_X = 10.4f, BRIGHT_X = 12.0f, LABEL_Y = 0.65f;
    private static final float[] BUTTONS = {13.3f, 14.2f, 15.1f};
    private static final String[] BUTTON_KEYS = {"capture", "reset", "off"};
    private static final int[] ZOOMS = {1, 2, 4, 8};
    private static final int PHOSPHOR = 0x7CFF96, LABEL = 0xFFD8D8CC, SWEEP = 80;  // ticks per turn
    private static final float DECAY = 55;  // ticks for a paint to fade to 1/e

    private final BlockPos console;
    private final int index;
    private int zoom = 0, bright = 3, age, closing = -1;
    private final int[] pressed = {-99, -99, -99};
    private @Nullable ConsoleFeed last;
    private final Map<Integer, ArrayDeque<double[]>> tracks = new HashMap<>();  // id -> (x, z, y, tick)
    private final Map<Integer, ArrayDeque<float[]>> paints = new HashMap<>();   // id -> (x, z, time) the sweep left
    private final Map<Integer, Integer> numbers = new HashMap<>(), levels = new HashMap<>();
    private final List<Component> log = new ArrayList<>();
    private int nextNumber = 1;
    private @Nullable Integer selected;
    private float markX, markZ;  // the marker, radar-relative blocks
    /** Target given to each launcher of the console (launcher pos -> contact id), as the operator set it. */
    private final Map<Long, Integer> assigned = new HashMap<>();
    /** Hits and misses to mark on the scope: x, z, game time, type. */
    private final List<float[]> marks = new ArrayList<>();
    private long lastEvent = Long.MIN_VALUE;
    /** Launcher rows on the character tube (screen y0, y1, launcher pos), for clicks. */
    private final List<double[]> launcherRows = new ArrayList<>();
    private double lastSweep = Double.NaN;
    private static final float[][] CLUTTER = new float[36][];  // ground returns around the radar: bearing, distance (0..1 of the tube)

    static {
        Random r = new Random(7);
        for (int k = 0; k < CLUTTER.length; k++) CLUTTER[k] = new float[]{(float) (r.nextFloat() * 2 * Math.PI), 0.015f + r.nextFloat() * r.nextFloat() * 0.09f};
    }

    // panel to screen, set in init
    private float s, ox, oy;

    public ScreenRadar(BlockPos console, int index) {
        super(Component.translatable("screen.ntm_air_defense.radar"));
        this.console = console;
        this.index = index;
    }

    @Override
    protected void init() {
        s = Math.min(width / 16f, height / 9f);
        ox = (width - 16 * s) / 2;
        oy = (height - 9 * s) / 2;
    }

    private float px(float u) {
        return ox + u * s;
    }

    private float py(float v) {
        return oy + (9 - v) * s;
    }

    private @Nullable BlockEntityConsole be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(console) instanceof BlockEntityConsole b ? b : null;
    }

    @Override
    public void tick() {
        age++;
        if (closing >= 0 && age - closing >= 8) {
            super.onClose();
            return;
        }
        if (age % 20 == 1) {  // keep the console polling fast while we look
            CompoundTag t = new CompoundTag();
            t.putString("op", "watch");
            PacketWire.sendToServer(new NbtControlPayload(console, t));
        }
        BlockEntityConsole b = be();
        ConsoleFeed f = b == null ? null : b.feeds.get(index + "/");
        if (f != null && f != last) {
            last = f;
            record(f);
            events(f);
        }
        last = f;
    }

    @Override
    public void onClose() {
        if (closing < 0) closing = age;
    }

    /** A new picture from the radar: extend the tracks, number new contacts, log arrivals and losses. */
    private void record(ConsoleFeed f) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        List<Integer> b = f.blips();
        Set<Integer> seen = new HashSet<>();
        for (int k = 0; k + ConsoleFeed.BLIP - 1 < b.size(); k += ConsoleFeed.BLIP) {
            int id = b.get(k + 3);
            seen.add(id);
            levels.put(id, b.get(k + 4));
            ArrayDeque<double[]> tr = tracks.computeIfAbsent(id, x -> new ArrayDeque<>());
            tr.addLast(new double[]{b.get(k), b.get(k + 1), b.get(k + 2), now});
            while (tr.size() > 12) tr.removeFirst();
            if (!numbers.containsKey(id)) {
                numbers.put(id, nextNumber++);
                log(Component.translatable("screen.ntm_air_defense.radar.new", name(id), type(id)));
            }
        }
        for (Integer id : List.copyOf(tracks.keySet())) {
            if (!seen.contains(id)) {
                tracks.remove(id);
                log(Component.translatable("screen.ntm_air_defense.radar.lost_contact", name(id)));
                if (id.equals(selected)) selected = null;
                unassign(id);
            }
        }
    }

    private void log(Component c) {
        log.add(0, Component.literal(String.format("%02d:%02d ", age / 1200, age / 20 % 60)).append(c));
        while (log.size() > 6) log.remove(log.size() - 1);
    }

    /** New launches, hits and misses from the radar: into the log, hits and misses also onto the scope. */
    private void events(ConsoleFeed f) {
        List<Integer> ev = f.events();
        long newest = lastEvent;
        for (int k = 0; k + ConsoleFeed.EVENT - 1 < ev.size(); k += ConsoleFeed.EVENT) {
            long time = ev.get(k + 5);
            if (time <= lastEvent) continue;
            newest = Math.max(newest, time);
            int type = ev.get(k + 3), target = ev.get(k + 4);
            Component who = name(target);
            switch (type) {
                case 0 -> log(Component.translatable("screen.ntm_air_defense.radar.ev_launch", launcherName(ev.get(k + 6), ev.get(k + 7)), who));
                case 1 -> log(Component.translatable("screen.ntm_air_defense.radar.ev_hit", who));
                default -> log(Component.translatable("screen.ntm_air_defense.radar.ev_miss", who));
            }
            if (type != 0) marks.add(new float[]{ev.get(k), ev.get(k + 1), time, type});
        }
        lastEvent = newest;
    }

    /** The kind of the console's launcher standing at radar-relative x, z. */
    private Component launcherName(int dx, int dz) {
        if (last != null) {
            BlockPos r = BlockPos.of(last.pos());
            for (ConsoleFeed lf : launchers()) {
                BlockPos lp = BlockPos.of(lf.pos());
                if (lp.getX() - r.getX() == dx && lp.getZ() - r.getZ() == dz) return lf.label();
            }
        }
        return Component.translatable("gui.ntm_air_defense.device.sam");
    }

    /** The launchers bound to screens of this console, one each, in a fixed order. */
    private List<ConsoleFeed> launchers() {
        BlockEntityConsole b = be();
        if (b == null) return List.of();
        Map<Long, ConsoleFeed> by = new java.util.TreeMap<>();
        for (ConsoleFeed lf : b.feeds.values()) {
            if (!lf.kind().equals("radar") && !lf.kind().equals("lost")) by.putIfAbsent(lf.pos(), lf);
        }
        return List.copyOf(by.values());
    }

    /** Give launcher n (from 0) the selected target, or take its target away if it already has this one. */
    private void toggleLauncher(int n) {
        List<ConsoleFeed> ls = launchers();
        if (n < 0 || n >= ls.size()) return;
        long pos = ls.get(n).pos();
        Integer cur = assigned.get(pos);
        if (selected == null || selected.equals(cur)) {
            assigned.remove(pos);
            designate(-1, pos);
        } else {
            assigned.put(pos, selected);
            designate(selected, pos);
        }
    }

    /** Launchers aimed at this contact lose it. */
    private void unassign(int id) {
        for (Long pos : List.copyOf(assigned.keySet())) {
            if (assigned.get(pos) == id) {
                assigned.remove(pos);
                designate(-1, pos);
            }
        }
    }

    private Component name(int id) {
        Integer n = numbers.get(id);
        return Component.translatable("screen.ntm_air_defense.radar.name", n == null ? "??" : String.format("%02d", n));
    }

    private Component type(int id) {
        int lv = levels.getOrDefault(id, -1);
        String k = lv < 0 ? "air" : lv <= 9 ? "missile" : lv == 10 ? "abm" : lv == 11 ? "player" : lv == 12 ? "shell" : "special";
        return Component.translatable("screen.ntm_air_defense.radar.type." + k);
    }

    private boolean missile(int id) {
        int lv = levels.getOrDefault(id, -1);
        return lv >= 0 && lv <= 10;
    }

    /** Velocity (blocks / s) of a contact from its track; null until it has two points. */
    private double @Nullable [] motion(int id) {
        ArrayDeque<double[]> tr = tracks.get(id);
        if (tr == null || tr.size() < 2) return null;
        double[] a = tr.peekFirst(), b = tr.peekLast();
        double dt = (b[3] - a[3]) / 20;
        return dt <= 0 ? null : new double[]{(b[0] - a[0]) / dt, (b[1] - a[1]) / dt, (b[2] - a[2]) / dt};
    }

    private float view() {
        return Math.max(16, (last == null ? 1000 : last.range()) / (float) ZOOMS[zoom]);
    }

    /** Phosphor colour at intensity f (0..1), scaled by the brightness knob and the tube warming up. */
    private int ph(float f, float partial) {
        float warm = Math.min(1, (age + partial) / 25f);
        int a = (int) Math.clamp(f * (0.45f + 0.15f * bright) * warm * 255, 0, 255);
        return a << 24 | PHOSPHOR;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
        g.fill(0, 0, width, height, 0xFF0A0B0A);
        g.blit(RenderPipelines.GUI_TEXTURED, FACE, (int) ox, (int) oy, 0, 0, (int) (16 * s), (int) (9 * s), TEX_W, TEX_H, TEX_W, TEX_H);

        scope(g, time, partial, mx, my);
        characters(g, partial);
        controls(g);
        if (closing >= 0) collapse(g, (age - closing + partial) / 8f);
        super.extractRenderState(g, mx, my, partial);
    }

    private void scope(GuiGraphicsExtractor g, float time, float partial, int mx, int my) {
        float cx = px(CX), cy = py(CY), r = TUBE * s;
        // engraved-in-the-tube range rings and bearing marks, very dim
        for (int ring = 1; ring <= 4; ring++) circle(g, cx, cy, r * ring / 4f, ph(0.16f, partial), 1);
        for (int k = 0; k < 36; k++) {
            double a = Math.toRadians(k * 10);
            float in = k % 3 == 0 ? 0.92f : 0.96f;
            line(g, cx + r * in * (float) Math.sin(a), cy - r * in * (float) Math.cos(a), cx + r * (float) Math.sin(a), cy - r * (float) Math.cos(a), ph(0.35f, partial), 1);
        }
        // sweep with a phosphor afterglow wedge
        double sweep = time / SWEEP * 2 * Math.PI;
        for (int k = 48; k >= 0; k--) {
            double a = sweep - Math.toRadians(k * 0.9);
            line(g, cx, cy, cx + r * (float) Math.sin(a), cy - r * (float) Math.cos(a), ph(k == 0 ? 0.95f : 0.22f * (1 - k / 49f), partial), k == 0 ? 2 : 1);
        }
        // clutter near the centre, painted by the sweep like everything else
        for (float[] c : CLUTTER) {
            float f = (float) Math.exp(-ago(sweep, c[0]) * SWEEP / DECAY) * 0.55f;
            float x = cx + r * c[1] * (float) Math.sin(c[0]), y = cy - r * c[1] * (float) Math.cos(c[0]);
            g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, ph(f, partial));
        }

        boolean radar = last != null && last.kind().equals("radar");
        if (!radar) {
            if (last != null) centred(g, Component.translatable("screen.ntm_air_defense.lost"), cx, cy - 4, 1, 0xFFFF5A3A);
            lastSweep = sweep;
            return;
        }
        // launchers on this console: their zones as dashed circles
        BlockPos at = BlockPos.of(last.pos());
        BlockEntityConsole b = be();
        if (b != null) {
            for (ConsoleFeed lf : b.feeds.values()) {
                if (lf.kind().equals("radar") || lf.kind().equals("lost")) continue;
                BlockPos lp = BlockPos.of(lf.pos());
                float lx = sx(lp.getX() - at.getX()), ly = sy(lp.getZ() - at.getZ());
                float lr = lf.range() / view() * r;
                if (Math.hypot(lx - cx, ly - cy) - lr < r) dashed(g, lx, ly, lr, cx, cy, r, ph(0.3f, partial));
            }
        }

        // paint the contacts the sweep has just crossed
        if (Double.isNaN(lastSweep) || sweep - lastSweep > Math.PI) lastSweep = sweep;
        double swept = sweep - lastSweep;
        for (Map.Entry<Integer, ArrayDeque<double[]>> e : tracks.entrySet()) {
            double[] p = e.getValue().peekLast();
            double bearing = Math.atan2(p[0], -p[1]);
            if (((bearing - lastSweep) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) <= swept) {
                ArrayDeque<float[]> pt = paints.computeIfAbsent(e.getKey(), x -> new ArrayDeque<>());
                pt.addLast(new float[]{(float) p[0], (float) p[1], time});
                while (pt.size() > 5) pt.removeFirst();
            }
        }
        lastSweep = sweep;
        paints.values().removeIf(pt -> time - pt.peekLast()[2] > DECAY * 4);

        int dot = Math.max(2, Math.round(s / 14));
        for (Map.Entry<Integer, ArrayDeque<float[]>> e : paints.entrySet()) {
            int size = missile(e.getKey()) ? dot + 1 : dot;
            for (float[] q : e.getValue()) {
                float f = (float) Math.exp(-(time - q[2]) / DECAY);
                float x = sx(q[0]), y = sy(q[1]);
                if (Math.hypot(x - cx, y - cy) > r - size) continue;
                g.fill((int) x - size, (int) y - size / 2, (int) x + size, (int) y + (size + 1) / 2, ph(f, partial));  // arc-shaped return
                g.fill((int) x - size - 1, (int) y - size, (int) x + size + 1, (int) y + size, ph(f * 0.18f, partial));  // halo
            }
        }
        // the locked contact: brackets around its last paint, blinking
        ArrayDeque<float[]> sp = selected == null ? null : paints.get(selected);
        if (sp != null && (age / 6) % 4 != 0) {
            float x = sx(sp.peekLast()[0]), y = sy(sp.peekLast()[1]), h = dot * 3;
            int c = ph(1, partial);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    line(g, x + sx * h, y + sy * h, x + sx * h * 0.5f, y + sy * h, c, 1);
                    line(g, x + sx * h, y + sy * h, x + sx * h, y + sy * h * 0.5f, c, 1);
                }
            }
        }
        // launchers: a square at the site, a dashed line to the target the operator gave it
        List<ConsoleFeed> ls = launchers();
        for (int n = 0; n < ls.size(); n++) {
            BlockPos lp = BlockPos.of(ls.get(n).pos());
            float lx = sx(lp.getX() - at.getX()), ly = sy(lp.getZ() - at.getZ());
            if (Math.hypot(lx - cx, ly - cy) > r) continue;
            int q = Math.max(2, dot);
            g.fill((int) lx - q, (int) ly - q, (int) lx + q, (int) ly + q, ph(0.7f, partial));
            centred(g, Component.literal(String.valueOf(n + 1)), lx + q * 2.5f, ly - q * 3, 0.6f, ph(0.7f, partial));
            Integer tid = assigned.get(ls.get(n).pos());
            ArrayDeque<float[]> tp = tid == null ? null : paints.get(tid);
            if (tp != null) {
                float tx = sx(tp.peekLast()[0]), ty = sy(tp.peekLast()[1]);
                int seg = Math.max(2, (int) (Math.hypot(tx - lx, ty - ly) / 6));
                for (int k = 0; k < seg; k += 2) {
                    line(g, lx + (tx - lx) * k / seg, ly + (ty - ly) * k / seg, lx + (tx - lx) * (k + 1) / seg, ly + (ty - ly) * (k + 1) / seg, ph(0.5f, partial), 1);
                }
            }
        }
        // our hits (+) and misses (x), fading over half a minute
        marks.removeIf(mk -> time - mk[2] > 600);
        for (float[] mk : marks) {
            float mxp = sx(mk[0]), myp = sy(mk[1]), q = dot * 1.6f, f = 1 - (time - mk[2]) / 600;
            if (Math.hypot(mxp - cx, myp - cy) > r - q) continue;
            int c = ph(0.3f + 0.6f * f, partial);
            if (mk[3] == 1) {
                line(g, mxp - q, myp, mxp + q, myp, c, 1);
                line(g, mxp, myp - q, mxp, myp + q, c, 1);
            } else {
                line(g, mxp - q, myp - q, mxp + q, myp + q, c, 1);
                line(g, mxp - q, myp + q, mxp + q, myp - q, c, 1);
            }
        }
        // the marker
        float x = sx(markX), y = sy(markZ), h = s * 0.18f, gap = s * 0.06f;
        int c = ph(0.85f, partial);
        line(g, x - h, y, x - gap, y, c, 1);
        line(g, x + gap, y, x + h, y, c, 1);
        line(g, x, y - h, x, y - gap, c, 1);
        line(g, x, y + gap, x, y + h, c, 1);
        // the range of the outer ring, on the bottom edge
        centred(g, Component.literal(String.valueOf(Math.round(view()))), cx, cy + r * 0.82f, 0.75f, ph(0.5f, partial));
    }

    /** Fraction of a turn since the sweep passed a bearing. */
    private static double ago(double sweep, double bearing) {
        return ((sweep - bearing) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) / (2 * Math.PI);
    }

    /** Radar-relative x, z (blocks) to screen; north up. */
    private float sx(double x) {
        return px(CX) + (float) (x / view() * TUBE * s);
    }

    private float sy(double z) {
        return py(CY) + (float) (z / view() * TUBE * s);
    }

    /** The character tube: header, targets by threat, the locked one's formular, the last log lines. */
    private void characters(GuiGraphicsExtractor g, float partial) {
        float x0 = px(CH_X0) + s * 0.15f, y0 = py(CH_Y1) + s * 0.12f, w = (CH_X1 - CH_X0 - 0.3f) * s, hgt = (CH_Y1 - CH_Y0 - 0.24f) * s;
        float k = Math.min(1, Math.min(hgt / (18 * 9), w / 170));
        float lh = 9 * k;
        int bright = ph(1, partial), dim = ph(0.55f, partial);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale(k, k);
        float cw = w / k;  // width in text units
        boolean radar = last != null && last.kind().equals("radar");
        g.text(font, radar ? Component.translatable("screen.ntm_air_defense.radar.head", last.range(), ZOOMS[zoom], tracks.size())
                : Component.translatable("screen.ntm_air_defense.lost"), 0, 0, bright, false);
        float[] col = {0, cw * 0.22f, cw * 0.55f, cw * 0.8f};
        String[] heads = Component.translatable("screen.ntm_air_defense.radar.columns").getString().split("\\|");
        for (int c = 0; c < Math.min(col.length, heads.length); c++) g.text(font, heads[c], (int) col[c], 12, dim, false);
        List<Integer> ids = new ArrayList<>(tracks.keySet());
        ids.sort(Comparator.comparingDouble(this::threat));
        int row = 22;
        for (int i = 0; i < Math.min(4, ids.size()); i++, row += 9) {
            int id = ids.get(i);
            double[] p = tracks.get(id).peekLast();
            int c = Integer.valueOf(id).equals(selected) && (age / 8) % 2 == 0 ? dim : bright;
            g.text(font, name(id), (int) col[0], row, c, false);
            g.text(font, type(id), (int) col[1], row, c, false);
            g.text(font, String.valueOf((int) Math.hypot(p[0], p[1])), (int) col[2], row, c, false);
            g.text(font, String.valueOf(Math.round(p[2])), (int) col[3], row, c, false);
        }
        row = 22 + 4 * 9 + 4;
        g.fill(0, row, (int) cw, row + 1, ph(0.3f, partial));
        row += 4;
        if (selected != null && tracks.containsKey(selected)) {
            double[] p = tracks.get(selected).peekLast();
            double[] v = motion(selected);
            int az = (int) ((Math.toDegrees(Math.atan2(p[0], -p[1])) + 360) % 360);
            g.text(font, Component.translatable("screen.ntm_air_defense.radar.locked", name(selected)).append(" ").append(type(selected)), 0, row, bright, false);
            g.text(font, Component.translatable("screen.ntm_air_defense.radar.formular", String.format("%03d", az), (int) Math.hypot(p[0], p[1]),
                    Math.round(p[2]), v == null ? "-" : String.valueOf(Math.round(Math.hypot(v[0], v[1])))), 0, row + 9, bright, false);
        } else {
            g.text(font, Component.translatable("screen.ntm_air_defense.radar.none"), 0, row, dim, false);
        }
        row += 22;
        g.fill(0, row, (int) cw, row + 1, ph(0.3f, partial));
        row += 4;
        // the console's launchers: number (key 1-9 or a click gives it the locked target), kind, state, its target
        launcherRows.clear();
        List<ConsoleFeed> ls = launchers();
        for (int n = 0; n < Math.min(4, ls.size()); n++, row += 9) {
            ConsoleFeed lf = ls.get(n);
            Integer tid = assigned.get(lf.pos());
            int c = tid != null ? bright : dim;
            g.text(font, (n + 1) + " ", 0, row, c, false);
            g.text(font, lf.label(), (int) col[0] + 10, row, c, false);
            g.text(font, Component.translatable("screen.ntm_air_defense.state." + lf.state()), (int) col[1] + 14, row, c, false);
            g.text(font, Component.translatable(lf.manual() ? "gui.ntm_air_defense.fnshort.manual" : "gui.ntm_air_defense.launcher.auto"), (int) col[2] + 14, row, dim, false);
            if (tid != null) g.text(font, name(tid), (int) col[3], row, bright, false);
            launcherRows.add(new double[]{y0 + (row - 1) * k, y0 + (row + 8) * k, n});
        }
        if (ls.isEmpty()) {
            g.text(font, Component.translatable("screen.ntm_air_defense.radar.no_launchers"), 0, row, dim, false);
            row += 9;
        }
        g.fill(0, row, (int) cw, row + 1, ph(0.3f, partial));
        row += 4;
        for (int i = 0; i < Math.min(2, log.size()); i++, row += 9) g.text(font, log.get(i), 0, row, dim, false);
        pose.popMatrix();
        // scanlines over both tubes
        for (float y = py(CH_Y1); y < py(CH_Y0); y += 2) g.fill((int) px(CH_X0), (int) y, (int) px(CH_X1), (int) y + 1, 0x22000000);
    }

    /** Knob pointers, lit / pressed buttons, the labels on the black plates. */
    private void controls(GuiGraphicsExtractor g) {
        knob(g, RANGE_X, 0.34f, 150 - zoom * 40);
        knob(g, BRIGHT_X, 0.26f, 150 - bright * 50);
        for (int i = 0; i < 3; i++) {
            float bx = px(BUTTONS[i]), by = py(KNOB_Y), h = 0.26f * s;
            if (age - pressed[i] < 4) g.fill((int) (bx - h), (int) (by - h), (int) (bx + h), (int) (by + h), 0x70000000);
            else if (i == 0 && selected != null) g.fill((int) (bx - h), (int) (by - h), (int) (bx + h), (int) (by + h), 0x50C8FFC8);  // LOCK lit while locked
            label(g, Component.translatable("screen.ntm_air_defense.radar." + BUTTON_KEYS[i]), BUTTONS[i], LABEL_Y);
        }
        label(g, Component.translatable("screen.ntm_air_defense.radar.range"), RANGE_X, LABEL_Y);
        label(g, Component.translatable("screen.ntm_air_defense.radar.bright"), BRIGHT_X, LABEL_Y);
        label(g, Component.translatable("screen.ntm_air_defense.radar.list"), (CH_X0 + CH_X1) / 2, 8.65f);
    }

    private void knob(GuiGraphicsExtractor g, float u, float len, float deg) {
        float x = px(u), y = py(KNOB_Y), a = (float) Math.toRadians(deg);
        line(g, x + len * 0.35f * s * (float) Math.cos(a), y - len * 0.35f * s * (float) Math.sin(a),
                x + len * s * (float) Math.cos(a), y - len * s * (float) Math.sin(a), 0xFFE8E8DC, Math.max(1, s / 16));
    }

    private void label(GuiGraphicsExtractor g, Component t, float u, float v) {
        float k = Math.min(1, s * 0.3f / 8);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(px(u), py(v) - 3.5f * k);
        pose.scale(k, k);
        g.text(font, t, -font.width(t) / 2, 0, LABEL, false);
        pose.popMatrix();
    }

    private void centred(GuiGraphicsExtractor g, Component t, float x, float y, float k, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(k, k);
        g.text(font, t, -font.width(t) / 2, 0, color, false);
        pose.popMatrix();
    }

    /** Switching off: both pictures shrink to a line, then a dot, then dark. */
    private void collapse(GuiGraphicsExtractor g, float t) {
        float cx = px(CX), cy = py(CY), r = TUBE * s;
        int dark = (int) Math.clamp(t * 1.6f * 255, 0, 255) << 24;
        for (int y = (int) (cy - r); y <= (int) (cy + r); y++) {  // the round tube only, row by row
            float w = (float) Math.sqrt(Math.max(0, r * r - (y + 0.5f - cy) * (y + 0.5f - cy)));
            g.fill((int) (cx - w), y, (int) (cx + w), y + 1, dark);
        }
        g.fill((int) px(CH_X0), (int) py(CH_Y1), (int) px(CH_X1), (int) py(CH_Y0), dark);
        if (t < 1) {
            float w = r * Math.max(0, 1 - t * 2), d = Math.max(1, s * 0.08f * (1 - t));
            int c = (int) ((1 - t) * 255) << 24 | 0xE8FFEC;
            g.fill((int) (cx - w - d), (int) (cy - d / 2), (int) (cx + w + d), (int) (cy + d / 2), c);
        }
    }

    /** Lower = more dangerous: missiles first, then by how soon it gets here. */
    private double threat(int id) {
        double[] p = tracks.get(id).peekLast();
        double d = Math.hypot(p[0], p[1]);
        double[] v = motion(id);
        double closing = v == null ? 0 : -(p[0] * v[0] + p[1] * v[1]) / Math.max(1, d);
        double eta = closing > 0.5 ? d / closing : d + 10000;
        return (missile(id) ? 0 : 100000) + eta;
    }

    private void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color, float th) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.translate(0, -th / 2);
        pose.scale(len, th);
        g.fill(0, 0, 1, 1, color);
        pose.popMatrix();
    }

    private void circle(GuiGraphicsExtractor g, float x, float y, float rr, int color, float th) {
        int n = Math.max(24, (int) (rr / 2));
        for (int k = 0; k < n; k++) {
            double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
            line(g, x + rr * (float) Math.sin(a0), y - rr * (float) Math.cos(a0), x + rr * (float) Math.sin(a1), y - rr * (float) Math.cos(a1), color, th);
        }
    }

    /** A dashed circle, only the parts inside the tube. */
    private void dashed(GuiGraphicsExtractor g, float x, float y, float rr, float cx, float cy, float r, int color) {
        int n = Math.max(24, (int) (rr / 3)) & ~1;
        for (int k = 0; k < n; k += 2) {
            double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
            float x0 = x + rr * (float) Math.sin(a0), y0 = y - rr * (float) Math.cos(a0), x1 = x + rr * (float) Math.sin(a1), y1 = y - rr * (float) Math.cos(a1);
            if (Math.hypot(x0 - cx, y0 - cy) < r && Math.hypot(x1 - cx, y1 - cy) < r) line(g, x0, y0, x1, y1, color, 1);
        }
    }

    /** The contact nearest the marker within a short reach, or null. */
    private @Nullable Integer nearest() {
        Integer best = null;
        double bd = view() * 0.12;
        for (Map.Entry<Integer, ArrayDeque<float[]>> e : paints.entrySet()) {
            if (!tracks.containsKey(e.getKey())) continue;
            float[] q = e.getValue().peekLast();
            double d = Math.hypot(q[0] - markX, q[1] - markZ);
            if (d < bd) {
                bd = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private boolean near(double mx, double my, float u, float v, float reach) {
        return Math.hypot(mx - px(u), my - py(v)) < reach * s;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing >= 0) return true;
        double mx = event.x(), my = event.y();
        int dir = event.button() == 1 ? -1 : 1;
        if (near(mx, my, CX, CY, TUBE)) {
            markX = (float) ((mx - px(CX)) / (TUBE * s) * view());
            markZ = (float) ((my - py(CY)) / (TUBE * s) * view());
            if (doubleClick) press(0);
        } else if (mx >= px(CH_X0) && mx < px(CH_X1) && my >= py(CH_Y1) && my < py(CH_Y0)) {
            for (double[] lr : launcherRows) if (my >= lr[0] && my < lr[1]) toggleLauncher((int) lr[2]);
        } else if (near(mx, my, RANGE_X, KNOB_Y, 0.6f)) {
            zoom = Math.clamp(zoom + dir, 0, ZOOMS.length - 1);
        } else if (near(mx, my, BRIGHT_X, KNOB_Y, 0.45f)) {
            bright = Math.clamp(bright + dir, 0, 4);
        } else {
            for (int i = 0; i < 3; i++) {
                if (Math.abs(mx - px(BUTTONS[i])) < 0.35f * s && Math.abs(my - py(KNOB_Y)) < 0.35f * s) press(i);
            }
        }
        return true;
    }

    private void press(int i) {
        pressed[i] = age;
        switch (i) {
            case 0 -> {
                Integer n = nearest();
                if (n != null && !n.equals(selected)) {
                    selected = n;
                    log(Component.translatable("screen.ntm_air_defense.radar.locked", name(n)));
                    if (launchers().size() == 1) toggleLauncher(0);  // a single launcher needs no choosing
                }
            }
            case 1 -> {
                if (selected != null) unassign(selected);
                selected = null;
            }
            default -> onClose();
        }
    }

    /** Target designation for one launcher of the console, in manual mode (-1 = none). */
    private void designate(int id, long pos) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "designate");
        t.putInt("id", id);
        t.putLong("pos", pos);
        PacketWire.sendToServer(new NbtControlPayload(console, t));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() >= 49 && event.key() <= 57) {  // 1-9: give that launcher the locked target
            toggleLauncher(event.key() - 49);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int dir = dy > 0 ? 1 : -1;
        if (near(x, y, BRIGHT_X, KNOB_Y, 0.45f)) bright = Math.clamp(bright + dir, 0, 4);
        else zoom = Math.clamp(zoom + dir, 0, ZOOMS.length - 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {}
}
