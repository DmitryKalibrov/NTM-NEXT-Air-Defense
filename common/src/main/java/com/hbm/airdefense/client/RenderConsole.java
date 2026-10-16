package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockConsole;
import com.hbm.airdefense.BlockEntityConsole;
import com.hbm.airdefense.ConsoleCatalog;
import com.hbm.airdefense.ConsoleFeed;
import com.hbm.airdefense.ConsoleFunctions;
import com.hbm.airdefense.ConsoleGeometry;
import com.hbm.airdefense.Seismic;
import com.hbm.client.model.Meshes;
import com.hbm.render.loader.HFRWavefrontObject;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Console blocks: the body is put together from slices by the neighbours (a merged run loses its inner cheeks,
 * end caps, plinths and cornices), then every mounted element is drawn on its face with its moving parts posed.
 */
public class RenderConsole implements BlockEntityRenderer<BlockEntityConsole, RenderConsole.State> {

    static final HFRWavefrontObject BODY = Meshes.load(AirDefense.id("models/obj/control_console.obj"));
    static final HFRWavefrontObject PARTS = Meshes.load(AirDefense.id("models/obj/control_parts.obj"));
    private static final RenderType BODY_TYPE = RenderTypes.entityCutoutCull(AirDefense.id("textures/block/control_console.png"));
    private static final RenderType PARTS_TYPE = RenderTypes.entityCutoutCull(AirDefense.id("textures/block/control_parts.png"));
    private static final RenderType GLASS_TYPE = RenderTypes.entityTranslucent(AirDefense.id("textures/block/control_parts.png"));

    private static final Map<String, Integer> BODY_IDS = ids(BODY);
    private static final Map<String, Integer> PART_IDS = ids(PARTS);

    private static Map<String, Integer> ids(HFRWavefrontObject mesh) {
        Map<String, Integer> m = new HashMap<>();
        List<String> names = mesh.getGroupNames();
        for (int i = 0; i < names.size(); i++) m.put(names.get(i), i);
        return m;
    }

    /** Mesh group of an element part ("" = the element's fixed body), -1 when the element has none. */
    static int partId(String type, String part) {
        return PART_IDS.getOrDefault(part.isEmpty() ? type : type + "_" + part, -1);
    }

    /** Bounds (x0 y0 z0 x1 y1 z1, element space) of a part, null when it has no mesh. */
    static float @Nullable [] bounds(String type, String part) {
        int id = partId(type, part);
        return id < 0 ? null : PARTS.boundsOfParts(id);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** The desk and things on it (a lamp) reach above the block: don't cull by the block's own box. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void extractRenderState(BlockEntityConsole be, State s, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, cameraPosition, breaking);
        s.draws.clear();
        s.body.clear();
        s.lines.clear();
        s.texts.clear();
        s.pools.clear();
        s.outline = null;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BlockConsole block)) return;
        Direction f = state.getValue(BlockConsole.FACING);
        s.facing = f;
        Level level = be.getLevel();
        if (level == null) return;
        ConsoleGeometry.Corner corner = ConsoleGeometry.corner(level, be.getBlockPos(), block.kind);
        if (corner != null) {  // mitred body only, nothing mounts on a corner
            s.facing = corner.facing();
            String k = corner.inner() ? "In" : "Out";
            int b = wall(level, be.getBlockPos().below(), corner.facing()) ? 1 : 0, t = wall(level, be.getBlockPos().above(), corner.facing()) ? 1 : 0;
            s.body.add(switch (block.kind) {
                case DESK -> "DeskCorner" + k;
                case PANEL -> "PanelCorner" + k + "_" + b + t;
                case TILT -> "TiltCorner" + k + "_" + t;
            });
            return;
        }
        body(s.body, block.kind, f, level, be.getBlockPos());

        float time = (level == null ? 0 : level.getGameTime()) + partialTicks;
        List<ConsoleGeometry.Face> faces = ConsoleGeometry.faces(block.kind);
        boolean hoverHere = be.getBlockPos().equals(ConsoleClient.hoverPos);
        for (int i = 0; i < be.elements.size(); i++) {
            BlockEntityConsole.Placed p = be.elements.get(i);
            ConsoleCatalog.Element e = ConsoleCatalog.get(p.type);
            if (e == null || p.face >= faces.size()) continue;
            Matrix4f m = ConsoleGeometry.element(f, faces.get(p.face), p.x, p.y);
            add(s, partId(p.type, ""), m, -1, false, false);
            if (p.type.equals("seismograph")) seismograph(s, be, i, m, time);
            if (p.type.equals("crt_ppi")) scope(s, m, 6, 6, 5.0f, 0.71f, time, be.feeds.get(i + "/"));
            if (p.type.equals("crt_ppi_desk")) scope(s, m, 4, 4, 3.3f, 0.51f, time, be.feeds.get(i + "/"));
            for (String part : e.parts()) {
                if (part.startsWith("pic") && !part.equals("pic" + p.state("pic"))) continue;  // only the portrait up now
                Matrix4f pm = new Matrix4f(m).mul(pose(be, i, p, e, part, time));
                String fn = p.fn.get(ConsoleCatalog.base(part));
                boolean blink = ((int) time / 5) % 2 == 0;
                boolean lit = ConsoleCatalog.lights(p.type, part) && (fn != null ? fn.equals("alarm") ? be.alarm == 1 || be.alarm == 2 && blink
                        : ConsoleFunctions.consoleOnly(fn) ? p.state(part) == 1
                        : ConsoleFunctions.lit(fn, be.feeds.get(i + "/" + ConsoleCatalog.base(part))) && !(fn.equals("missile") && be.alarm == 2 && !blink)
                        : part.equals("bulb") ? p.state("switch") == 1
                        : p.type.equals("seismograph") ? alarm(be, time) : p.state(part) == 1);
                int color = ConsoleCatalog.tinted(p.type, part) || part.equals("bulb") ? (part.equals("bulb") ? 0xFFFFE2A8 : p.tint) : -1;
                if (p.type.equals("readiness") && part.startsWith("lens")) {  // fixed colours, lit by the bound launcher
                    int k = part.charAt(4) - '0';
                    lit = ConsoleFunctions.lit(READY_FN[k], be.feeds.get(i + "/")) && (k != 3 || blink);
                    color = READY_COLOR[k];
                }
                if (ConsoleCatalog.lights(p.type, part) && !lit) color = dim(color);
                boolean glow = lit || part.startsWith("screen");
                ConsoleFeed feed = part.startsWith("screen") ? be.feeds.get(i + "/" + ConsoleFeed.key(p.type, part)) : null;
                if (feed != null) {  // a bound monitor: blank the tube, draw the live picture over it
                    color = 0xFF263426;
                    float[] sb = bounds(p.type, part);
                    if (sb != null && feed.kind().equals("eos")) camera(s, pm, sb, feed, time);
                    else if (sb != null) tv(s, pm, sb, feed, time);
                }
                add(s, partId(p.type, part), pm, color, glow, part.equals("cover"));
                if (hoverHere && i == ConsoleClient.hoverIndex && ConsoleCatalog.base(part).equals(ConsoleCatalog.base(ConsoleClient.hoverPart))
                        && !part.endsWith("_tint")) {
                    float[] b = bounds(p.type, part);
                    if (b != null) {
                        s.outline = pm;
                        s.outlineBox = b;
                    }
                }
            }
            if (p.type.equals("readiness")) readiness(s, p, m, be.feeds.get(i + "/"));
            if (ConsoleFunctions.digits(p.type) && p.fn.containsKey("")) {
                int n = ConsoleFunctions.number(p.fn.get(""), be.feeds.get(i + "/"));
                float[] w = p.type.equals("counter") ? new float[]{2.0f, 0.6f, 0.86f, 0.75f} : new float[]{3.0f, 0.55f, 0.56f, 0.85f};
                text(s, m, w[0], w[1], w[2], w[3], Component.literal(n < 0 ? "--" : String.format("%03d", Math.min(999, n))), 0xFFFF5A28, true);
            }
            labels(s, p, e, m);
            if (p.type.equals("desk_lamp") && p.state("switch") == 1) pool(s, m, faces.get(p.face), p);
        }
        // ghost of the element the kit would mount here
        if (be.getBlockPos().equals(ConsoleClient.ghostPos) && ConsoleClient.ghostFace < faces.size()) {
            ConsoleCatalog.Element e = ConsoleCatalog.get(ConsoleClient.selected);
            if (e != null) {
                Matrix4f m = ConsoleGeometry.element(f, faces.get(ConsoleClient.ghostFace), ConsoleClient.ghostX, ConsoleClient.ghostY);
                int c = ConsoleClient.ghostOk ? 0x90B0FFB0 : 0x90FF6060;
                add(s, partId(e.name(), ""), m, c, true, true);
                for (String part : e.parts()) add(s, partId(e.name(), part), m, c, true, true);
            }
        }
    }

    // seismograph: paper speed and pen geometry in grid px
    private static final int TRACE_POINTS = 200;
    private static final float TRACE_TICKS = 160, PEN_TIP_X = 5.05f, PEN_Y = 4.2f, PEN_SWING = 1.5f, PEN_ARM = 1.6f;
    private static final int INK = 0xFF7A1812;

    private static boolean alarm(BlockEntityConsole be, float time) {
        return Seismic.envelope(be.quakes, time) > 0.12f;
    }

    /** The live trace (newest at the pen, older to the left) and the last quake's magnitude. */
    private static void seismograph(State s, BlockEntityConsole be, int i, Matrix4f m, float time) {
        float[] paper = bounds("seismograph", "paper");
        if (paper == null) return;
        Lines ink = new Lines(m, paper[5] + 0.02f / 16, 0.07f / 16, false);
        float x0 = paper[0], x1 = PEN_TIP_X / 16, px = 0, py = 0;
        for (int k = 0; k < TRACE_POINTS; k++) {
            float age = (TRACE_POINTS - 1 - k) * TRACE_TICKS / (TRACE_POINTS - 1);
            float x = x0 + (x1 - x0) * k / (TRACE_POINTS - 1);
            float y = (PEN_Y + Seismic.pen(Seismic.motion(be.quakes, time - age, i)) * PEN_SWING) / 16;
            if (k > 0) ink.add(px, py, x, y, INK);
            px = x;
            py = y;
        }
        s.lines.add(ink);
        Seismic.Quake last = be.quakes.isEmpty() ? null : be.quakes.get(be.quakes.size() - 1);
        String text = last != null && time >= last.p() ? String.format("M%.1f", last.mag()) : "M-.-";
        s.texts.add(new Text(new Matrix4f(m).translate(1.0f / 16, 1.05f / 16, 1.54f / 16).scale(0.5f / 16 / 7, -0.5f / 16 / 7, 1), Component.literal(text), 0xFFFF5A28, true));
    }

    private static final int GRID = 0xFF1E6A34, SWEEP = 0xFF8CFFA8;

    /** Range rings, bearing lines every 30 degrees and the turning sweep with its afterglow, drawn on a scope face. */
    private static void scope(State s, Matrix4f m, float cx, float cy, float r, float z, float time, @Nullable ConsoleFeed feed) {
        Lines l = new Lines(m, z / 16, 0.05f / 16, true);
        cx /= 16;
        cy /= 16;
        r /= 16;
        for (int ring = 1; ring <= 3; ring++) {
            float rr = r * ring / 3.2f;
            for (int k = 0; k < 48; k++) {
                double a0 = k * Math.PI / 24, a1 = (k + 1) * Math.PI / 24;
                l.add(cx + rr * (float) Math.sin(a0), cy + rr * (float) Math.cos(a0), cx + rr * (float) Math.sin(a1), cy + rr * (float) Math.cos(a1), GRID);
            }
        }
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            l.add(cx, cy, cx + r * 0.94f * (float) Math.sin(a), cy + r * 0.94f * (float) Math.cos(a), GRID);
        }
        double sweep = time / 80.0 * 2 * Math.PI;  // one turn in 4 s, clockwise from the top
        for (int k = 24; k >= 0; k--) {
            double a = sweep - Math.toRadians(k * 1.6);
            float f = 1 - k / 25f;
            int c = 0xFF000000 | (int) (0x8C * f) << 16 | (int) (0x30 + (0xFF - 0x30) * f) << 8 | (int) (0x40 + (0xA8 - 0x40) * f);
            l.add(cx, cy, cx + r * 0.96f * (float) Math.sin(a), cy + r * 0.96f * (float) Math.cos(a), k == 0 ? SWEEP : c);
        }
        s.lines.add(l);
        if (feed == null) return;
        if (feed.kind().equals("lost")) {
            text(s, m, cx * 16, cy * 16 - 0.25f, z + 0.02f, 0.5f, Component.translatable("screen.ntm_air_defense.lost"), RED, true);
            return;
        }
        // contacts: each lights up as the sweep passes its bearing and fades through the turn (north up)
        Lines b = new Lines(m, z / 16 + 0.005f / 16, 0.13f / 16, true);
        List<Integer> bl = feed.blips();
        for (int k = 0; k + ConsoleFeed.BLIP - 1 < bl.size(); k += ConsoleFeed.BLIP) {
            float dx = bl.get(k), dz = bl.get(k + 1), d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d > feed.range() || feed.range() <= 0) continue;
            double bearing = Math.atan2(dx, -dz);
            double age = ((sweep - bearing) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) / (2 * Math.PI);
            float f = (float) Math.max(0.12, 1 - age);
            float rr = r * d / feed.range(), x = cx + rr * (float) Math.sin(bearing), y = cy + rr * (float) Math.cos(bearing);
            int c = 0xFF000000 | (int) (0xC8 * f) << 16 | (int) (0xFF * f) << 8 | (int) (0xC0 * f);
            float h = 0.13f / 16;
            b.add(x - h, y, x + h, y, c);
        }
        s.lines.add(b);
        text(s, m, (cx - r * 0.95f) * 16, (cy - r * 0.98f) * 16, z + 0.02f, 0.35f, Component.literal(String.valueOf(feed.range())), GRID_TEXT, false);
    }

    private static final int PHOSPHOR = 0xFF8CFFA0, AMBER = 0xFFFFB347, RED = 0xFFFF4A3A, GRID_TEXT = 0xFF3FAF5A;

    /**
     * A screen on an optoelectronic station while nobody looks through it: the sight's reticle over a dim noisy
     * picture and the station's mode. Clicking it puts the operator behind the camera (ScreenEos).
     */
    private static void camera(State s, Matrix4f m, float[] b, ConsoleFeed f, float time) {
        float x0 = b[0] * 16 + 0.3f, y0 = b[1] * 16 + 0.3f, x1 = b[3] * 16 - 0.3f, y1 = b[4] * 16 - 0.3f, z = b[5] * 16 + 0.03f;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hw = (x1 - x0) / 2, lh = (y1 - y0) / 7;
        Lines l = new Lines(m, z / 16, 0.035f / 16, true);
        java.util.Random noise = new java.util.Random((long) time * 17);
        for (int k = 0; k < 9; k++) {
            float x = x0 + noise.nextFloat() * (x1 - x0 - 0.6f), y = y0 + noise.nextFloat() * (y1 - y0);
            l.add(x / 16, y / 16, (x + 0.2f + noise.nextFloat() * 0.5f) / 16, y / 16, TV_DIM);
        }
        float g = hw * 0.12f, a = hw * 0.4f;
        l.add((cx - a) / 16, cy / 16, (cx - g) / 16, cy / 16, TV);
        l.add((cx + g) / 16, cy / 16, (cx + a) / 16, cy / 16, TV);
        l.add(cx / 16, (cy - a * 0.7f) / 16, cx / 16, (cy - g) / 16, TV);
        l.add(cx / 16, (cy + g) / 16, cx / 16, (cy + a * 0.7f) / 16, TV);
        s.lines.add(l);
        text(s, m, x0 + 0.1f, y1 - lh * 0.9f, z, lh * 0.6f, Component.translatable("screen.ntm_air_defense.eos." + f.state()), TV, false);
    }

    private static final int PAINT = 0xFFE6E6DA, KEY_INK = 0xFF262626;
    /** Readiness column, top to bottom: power, ready, target locked, launch (flashing), no missiles. */
    private static final String[] READY_FN = {"power", "ready", "lock", "launch", "noammo"};
    private static final int[] READY_COLOR = {0xFF7CE07C, 0xFF7CE07C, 0xFFF0C040, 0xFFFF5A3A, 0xFFFF5A3A};

    /** Readiness column: the lamp captions, the launcher's name on the plate, missiles left in the window. */
    private static void readiness(State s, BlockEntityConsole.Placed p, Matrix4f m, @Nullable ConsoleFeed f) {
        for (int k = 0; k < 5; k++) {
            float[] b = bounds("readiness", "lens" + k);
            if (b == null || p.labels.containsKey("lens" + k)) continue;  // the player's own caption is drawn by labels()
            fit(s, m, (b[0] + b[3]) * 8, (b[1] + b[4]) * 8 - 0.22f, b[5] * 16 + 0.02f, 0.44f, (b[3] - b[0]) * 16 * 0.9f,
                    Component.translatable("gui.ntm_air_defense.fnshort." + READY_FN[k]), KEY_INK);
        }
        if (!p.labels.containsKey("")) {
            Component name = f == null ? Component.literal("---") : f.label();
            fit(s, m, 2, 9.2f - 0.3f, 0.62f, 0.6f, 3.2f, name, PAINT);
        }
        String n = f == null || f.kind().equals("lost") ? "--" : String.format("%02d", f.ammo());
        text(s, m, 2, 0.6f, 0.57f, 0.75f, Component.literal(n), 0xFFFF5A28, true);
    }

    /**
     * The player's labels: on a label plate, on the key itself for keys of a row or field, otherwise painted on
     * the panel just above the element. Shrunk to fit; painted, so lit by the room, not glowing.
     */
    private static void labels(State s, BlockEntityConsole.Placed p, ConsoleCatalog.Element e, Matrix4f m) {
        if (p.labels.isEmpty()) return;
        float[] eb = bounds(p.type, "");
        for (Map.Entry<String, String> le : p.labels.entrySet()) {
            String v = le.getValue(), part = le.getKey();
            Component t = v.startsWith("#") ? Component.translatable("gui.ntm_air_defense.fnshort." + v.substring(1)) : Component.literal(v);
            float[] pb = part.isEmpty() ? null : bounds(p.type, part);
            if (p.type.equals("readiness") && part.isEmpty()) {  // the column's name plate
                fit(s, m, 2, 8.9f, 0.62f, 0.6f, 3.2f, t, PAINT);
            } else if (p.type.startsWith("label") && eb != null) {
                float h = Math.min((eb[4] - eb[1]) * 16 * 0.6f, 1.0f);
                fit(s, m, (eb[0] + eb[3]) * 8, (eb[1] + eb[4]) * 8 - h / 2, eb[5] * 16 + 0.02f, h, (eb[3] - eb[0]) * 16 * 0.9f, t, PAINT);
            } else if (pb != null && Character.isDigit(part.charAt(part.length() - 1))) {
                float h = Math.min((pb[4] - pb[1]) * 16 * 0.45f, 0.5f);
                fit(s, m, (pb[0] + pb[3]) * 8, (pb[1] + pb[4]) * 8 - h / 2, pb[5] * 16 + 0.02f, h, (pb[3] - pb[0]) * 16 * 0.9f, t, KEY_INK);
            } else {
                fit(s, m, e.w() / 2f, e.h() + 0.15f, 0.03f, 0.55f, Math.max(e.w() * 1.6f, 4), t, PAINT);
            }
        }
    }

    /** Centred unlit text, `size` px tall unless that would be wider than maxW. */
    private static void fit(State s, Matrix4f m, float cx, float y, float z, float size, float maxW, Component t, int color) {
        int w = Minecraft.getInstance().font.width(t);
        if (w * size / 8 > maxW) size = maxW * 8 / Math.max(1, w);
        float k = size / 16 / 8;
        s.texts.add(new Text(new Matrix4f(m).translate(cx / 16 - w * k / 2, y / 16 + size / 16, z / 16).scale(k, -k, 1), t, color, false));
    }

    /** The warm pool of a desk lamp on its face, kept inside the face. */
    private static void pool(State s, Matrix4f m, ConsoleGeometry.Face face, BlockEntityConsole.Placed p) {
        float[] b = bounds("desk_lamp", "bulb");
        if (b == null) return;
        s.pools.add(new Pool(m, (b[0] + b[3]) / 2, (b[1] + b[4]) / 2 - 1.0f / 16, 6.5f / 16, 4.0f / 16,
                -p.x / 16f, -p.y / 16f, (face.w() - p.x) / 16f, (face.h() - p.y) / 16f));
    }

    private static final int TV = 0xFFC8D2C8, TV_DIM = 0xFF4A524A;

    /**
     * The TV channel: the last hit replayed from the missile's seeker, black and white. Crosshair, the target
     * growing as the distance closes, the horizon tilting with the turns, noise; a white flash at the burst,
     * then the frozen "target destroyed" frame until the replay starts again.
     */
    private static void tv(State s, Matrix4f m, float[] b, ConsoleFeed f, float time) {
        float x0 = b[0] * 16 + 0.3f, y0 = b[1] * 16 + 0.3f, x1 = b[3] * 16 - 0.3f, y1 = b[4] * 16 - 0.3f, z = b[5] * 16 + 0.03f;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hw = (x1 - x0) / 2, hh = (y1 - y0) / 2, lh = (y1 - y0) / 7;
        Lines l = new Lines(m, z / 16, 0.035f / 16, true);
        java.util.Random noise = new java.util.Random((long) time * 31);
        for (int k = 0; k < 7; k++) {  // snow
            float x = x0 + noise.nextFloat() * (x1 - x0 - 0.6f), y = y0 + noise.nextFloat() * (y1 - y0);
            l.add(x / 16, y / 16, (x + 0.2f + noise.nextFloat() * 0.5f) / 16, y / 16, TV_DIM);
        }
        List<Integer> c = f.clip();
        int n = c.size() / 6;
        if (f.kind().equals("lost") || n < 2) {
            s.lines.add(l);
            text(s, m, cx, cy - lh * 0.4f, z, lh * 0.8f, Component.translatable(f.kind().equals("lost") ? "screen.ntm_air_defense.lost" : "screen.ntm_air_defense.tv.idle"), TV, true);
            return;
        }
        float period = n + 40, tt = ((time - f.clipTime()) % period + period) % period;
        if (tt >= n) {  // the burst: a flash, then the frozen frame
            if (tt < n + 4) {
                Lines flash = new Lines(m, z / 16, hh / 16, true);
                flash.add(x0 / 16, cy / 16, x1 / 16, cy / 16, 0xFFF4F8F4);
                s.lines.add(flash);
            } else {
                s.lines.add(l);
                text(s, m, cx, cy - lh * 0.4f, z, lh * 0.8f, Component.translatable("screen.ntm_air_defense.tv.kill"), TV, true);
            }
            return;
        }
        int j = (int) tt, j2 = Math.min(j + 1, n - 1);
        Vec3 mp = at(c, j, 0), tp = at(c, j, 3), fwd = at(c, j2, 0).subtract(mp);
        if (fwd.lengthSqr() < 1e-4) fwd = tp.subtract(mp);
        fwd = fwd.normalize();
        Vec3 right = Math.abs(fwd.y) > 0.99 ? new Vec3(1, 0, 0) : fwd.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(fwd);
        double tan = Math.tan(Math.toRadians(14)), depth = tp.subtract(mp).dot(fwd);
        // horizon
        float ho = (float) (-fwd.y / Math.sqrt(Math.max(1e-6, 1 - fwd.y * fwd.y)) / tan * hw);
        if (Math.abs(ho) < hh) l.add(x0 / 16, (cy + ho) / 16, x1 / 16, (cy + ho) / 16, TV_DIM);
        // crosshair
        float g = hw * 0.12f, a = hw * 0.35f;
        l.add((cx - a) / 16, cy / 16, (cx - g) / 16, cy / 16, TV);
        l.add((cx + g) / 16, cy / 16, (cx + a) / 16, cy / 16, TV);
        l.add(cx / 16, (cy - a) / 16, cx / 16, (cy - g) / 16, TV);
        l.add(cx / 16, (cy + g) / 16, cx / 16, (cy + a) / 16, TV);
        s.lines.add(l);
        if (depth > 0.2) {
            Vec3 d = tp.subtract(mp);
            float px = (float) (d.dot(right) / depth / tan * hw), py = (float) (d.dot(up) / depth / tan * hw);
            float size = (float) Math.min(hh * 0.8, Math.max(0.12, 1.6 / depth / tan * hw));
            if (Math.abs(px) < hw - size / 2 && Math.abs(py) < hh - size / 2) {
                Lines blob = new Lines(m, (z + 0.01f) / 16, size / 2 / 16, true);
                blob.add((cx + px - size / 2) / 16, (cy + py) / 16, (cx + px + size / 2) / 16, (cy + py) / 16, 0xFFE8F0E8);
                s.lines.add(blob);
            }
        }
        text(s, m, x0 + 0.1f, y0 + 0.1f, z, lh * 0.6f, Component.literal(String.format("D %.0f", Math.max(0, depth))), TV, false);
    }

    private static Vec3 at(List<Integer> c, int j, int o) {
        return new Vec3(c.get(j * 6 + o) / 8.0, c.get(j * 6 + o + 1) / 8.0, c.get(j * 6 + o + 2) / 8.0);
    }

    /** Text on an element face at grid px (x, y), `size` px tall, glowing. */
    private static void text(State s, Matrix4f m, float x, float y, float z, float size, Component t, int color, boolean centred) {
        float k = size / 16 / 8;
        float shift = centred ? -Minecraft.getInstance().font.width(t) * k / 2 : 0;
        s.texts.add(new Text(new Matrix4f(m).translate(x / 16 + shift, y / 16 + size / 16, z / 16).scale(k, -k, 1), t, color, true));
    }

    private static int dim(int argb) {
        return 0xFF000000 | ((argb >> 17) & 0x7F) << 16 | ((argb >> 9) & 0x7F) << 8 | ((argb >> 1) & 0x7F);
    }

    private static void add(State s, int id, Matrix4f m, int color, boolean glow, boolean glass) {
        if (id >= 0) s.draws.add(new Draw(id, m, color, glow, glass));
    }

    /** Element space pose of a moving part for its state. */
    private static Matrix4f pose(BlockEntityConsole be, int i, BlockEntityConsole.Placed p, ConsoleCatalog.Element e, String part, float time) {
        Matrix4f m = new Matrix4f();
        String b = ConsoleCatalog.base(part);
        float[] pv = e.pivots().getOrDefault(b, e.pivots().getOrDefault(part, new float[3]));
        int st = p.state(b);
        switch (ConsoleCatalog.kind(p.type, b)) {
            case PUSH -> {
                String key = i + "/" + b;
                Integer seen = be.clientSeen.put(key, st);
                if (seen != null && seen != st) be.clientPressed.put(key, time);
                float since = time - be.clientPressed.getOrDefault(key, -100f);
                if (since < 4) m.translate(0, 0, -0.35f / 16);
            }
            case TOGGLE -> {
                switch (b) {
                    case "lever" -> about(m, pv, 'x', st == 1 ? 40 : 0);
                    case "rocker" -> about(m, pv, 'x', st == 1 ? -16 : 0);
                    case "cover" -> about(m, pv, 'x', st == 1 ? -110 : 0);
                    case "key" -> about(m, pv, 'z', st == 1 ? -90 : 0);
                    case "knob" -> m.translate(0, (st == 1 ? 1.5f : -1.5f) / 16, 0);
                    case "cap", "switch" -> {
                        if (st == 1) m.translate(0, 0, -0.25f / 16);
                    }
                    default -> {}
                }
            }
            case ROTARY -> about(m, pv, 'z', -ConsoleCatalog.angle(p.type, st));
            case NONE -> {
                float wob = (float) Math.sin(time * 0.03 + i * 1.7);
                String meter = p.fn.get("");
                if (meter != null) {  // a bound dial reads its quantity: -1 at the left stop, 1 at the right
                    float target = ConsoleFunctions.fraction(meter, be.feeds.get(i + "/")) * 2 - 1;
                    float now = be.clientNeedle.getOrDefault(i, target);
                    now += (target - now) * 0.08f;  // the needle swings over, a little damped
                    be.clientNeedle.put(i, now);
                    wob = -now;
                }
                switch (p.type + "/" + b) {
                    case "joystick/stick" -> {  // leans with the operator's steering while the camera view is open
                        ScreenEos v = ScreenEos.active;
                        if (v != null && v.drives(be.getBlockPos(), i)) {
                            about(m, pv, 'x', v.stickY * 18);
                            about(m, pv, 'y', v.stickX * 18);
                        }
                    }
                    case "gauge_square/needle" -> about(m, pv, 'z', wob * 30);
                    case "gauge_round/needle" -> about(m, pv, 'z', wob * 90);
                    case "gauge_edgewise/pointer" -> m.translate(0, (meter != null ? -wob : wob) * 1.8f / 16, 0);  // bound: empty at the bottom
                    case "recorder/pen" -> m.translate(wob * 1.2f / 16, 0, 0);
                    case "seismograph/pen" -> {  // the arm swings so its tip follows the trace
                        float dy = Seismic.pen(Seismic.motion(be.quakes, time, i)) * PEN_SWING;
                        about(m, pv, 'z', (float) -Math.toDegrees(Math.asin(dy / PEN_ARM)));
                    }
                    case "seismograph/pointer" -> m.translate(0, (-1.0f + Math.min(1, Seismic.envelope(be.quakes, time)) * 2.1f) / 16, 0);
                    case "clock/hour", "clock/minute" -> {
                        Level level = be.getLevel();
                        long day = level == null ? 0 : level.getDefaultClockTime() % 24000;
                        double hours = day / 1000.0 + 6;
                        about(m, pv, 'z', (float) -(b.equals("hour") ? hours % 12 * 30 : hours % 1 * 360));
                    }
                    default -> {}
                }
            }
        }
        return m;
    }

    private static void about(Matrix4f m, float[] pv, char axis, float deg) {
        float r = (float) Math.toRadians(deg);
        m.translate(pv[0], pv[1], pv[2]);
        if (axis == 'x') m.rotateX(r);
        else if (axis == 'y') m.rotateY(r);
        else m.rotateZ(r);
        m.translate(-pv[0], -pv[1], -pv[2]);
    }

    /** The welded body mesh for this block's neighbours (see the variants in tools/blender/console.py). */
    private static void body(List<String> out, ConsoleGeometry.Kind kind, Direction f, Level level, BlockPos pos) {
        int l = same(level, pos.relative(f.getClockWise()), kind, f) ? 1 : 0;
        int r = same(level, pos.relative(f.getCounterClockWise()), kind, f) ? 1 : 0;
        int b = wall(level, pos.below(), f) ? 1 : 0, t = wall(level, pos.above(), f) ? 1 : 0;
        out.add(switch (kind) {
            case DESK -> "Desk_" + l + r;
            case PANEL -> "Panel_" + b + t + l + r;
            case TILT -> "Tilt_" + t + l + r;
        });
    }

    /** A neighbour continuing this run: same kind and facing, or a corner of this kind. */
    private static boolean same(Level level, BlockPos pos, ConsoleGeometry.Kind kind, Direction f) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BlockConsole b && b.kind == kind
                && (s.getValue(BlockConsole.FACING) == f || ConsoleGeometry.corner(level, pos, kind) != null);
    }

    /** A wall console stacked above or below (a corner stacks on corners whatever their own facing). */
    private static boolean wall(Level level, BlockPos pos, Direction f) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BlockConsole b && b.kind != ConsoleGeometry.Kind.DESK
                && (s.getValue(BlockConsole.FACING) == f || ConsoleGeometry.corner(level, pos, b.kind) != null);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        Matrix4f block = ConsoleGeometry.block(s.facing);
        List<String> body = List.copyOf(s.body);
        List<Draw> draws = List.copyOf(s.draws);
        collector.submitCustomGeometry(ps, BODY_TYPE, (pose, buffer) -> {
            PoseStack.Pose p = pose.copy();
            p.mulPose(block);
            for (String part : body) {
                Integer id = BODY_IDS.get(part);
                if (id != null) BODY.renderPart(p, buffer, light, -1, id);
            }
        });
        collector.submitCustomGeometry(ps, PARTS_TYPE, (pose, buffer) -> {
            for (Draw d : draws) {
                if (d.glass) continue;
                PoseStack.Pose p = pose.copy();
                p.mulPose(d.matrix);
                PARTS.renderPart(p, buffer, d.glow ? LightCoordsUtil.FULL_BRIGHT : light, d.color, d.id);
            }
        });
        if (!s.pools.isEmpty()) {
            List<Pool> pools = List.copyOf(s.pools);
            collector.submitCustomGeometry(ps, GLASS_TYPE, (pose, buffer) -> {
                for (Pool pl : pools) {
                    PoseStack.Pose p = pose.copy();
                    p.mulPose(pl.matrix);
                    int n = 24;
                    for (int k = 0; k < n; k++) {
                        double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
                        float z = 0.012f / 16;
                        vertex(p, buffer, pl.cx, pl.cy, z, 0x58FFDFA0, LightCoordsUtil.FULL_BRIGHT);
                        vertex(p, buffer, pl.x(a0), pl.y(a0), z, 0x00FFDFA0, LightCoordsUtil.FULL_BRIGHT);
                        vertex(p, buffer, pl.x(a1), pl.y(a1), z, 0x00FFDFA0, LightCoordsUtil.FULL_BRIGHT);
                        vertex(p, buffer, pl.cx, pl.cy, z, 0x58FFDFA0, LightCoordsUtil.FULL_BRIGHT);
                    }
                }
            });
        }
        if (draws.stream().anyMatch(d -> d.glass)) {
            collector.submitCustomGeometry(ps, GLASS_TYPE, (pose, buffer) -> {
                for (Draw d : draws) {
                    if (!d.glass) continue;
                    PoseStack.Pose p = pose.copy();
                    p.mulPose(d.matrix);
                    PARTS.renderPart(p, buffer, d.glow ? LightCoordsUtil.FULL_BRIGHT : light, d.color, d.id);
                }
            });
        }
        if (!s.lines.isEmpty()) {
            List<Lines> all = List.copyOf(s.lines);
            collector.submitCustomGeometry(ps, PARTS_TYPE, (pose, buffer) -> {
                for (Lines l : all) {
                    PoseStack.Pose p = pose.copy();
                    p.mulPose(l.matrix);
                    int lt = l.glow ? LightCoordsUtil.FULL_BRIGHT : light;
                    for (int k = 0; k < l.colors.size(); k++) {
                        line(p, buffer, l.seg.getFloat(4 * k), l.seg.getFloat(4 * k + 1), l.seg.getFloat(4 * k + 2), l.seg.getFloat(4 * k + 3),
                                l.z, l.width, l.colors.getInt(k), lt);
                    }
                }
            });
        }
        for (Text t : s.texts) {
            ps.pushPose();
            ps.last().mulPose(t.matrix);
            collector.submitText(ps, 0, 0, t.text.getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                    t.glow ? LightCoordsUtil.FULL_BRIGHT : light, t.color, 0, 0);
            ps.popPose();
        }
        if (s.outline != null) {
            float[] b = s.outlineBox;
            ps.pushPose();
            ps.last().mulPose(s.outline);
            float g = 0.3f / 16;  // a hair outside the part
            collector.submitShapeOutline(ps, Shapes.box(b[0] - g, b[1] - g, b[2] - g, b[3] + g, b[4] + g, b[5] + g),
                    RenderTypes.lines(), 0xE0FFFFFF, 2.0f, false);
            ps.popPose();
        }
    }

    /** One segment as a thin quad in the element's face plane (white swatch of the atlas, coloured by the vertices). */
    private static void line(PoseStack.Pose p, VertexConsumer b, float xa, float ya, float xb, float yb, float z, float w, int color, int light) {
        float dx = xb - xa, dy = yb - ya, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) return;
        float nx = -dy / len * w, ny = dx / len * w;
        vertex(p, b, xa - nx, ya - ny, z, color, light);
        vertex(p, b, xb - nx, yb - ny, z, color, light);
        vertex(p, b, xb + nx, yb + ny, z, color, light);
        vertex(p, b, xa + nx, ya + ny, z, color, light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer b, float x, float y, float z, int color, int light) {
        b.addVertex(p, x, y, z).setColor(color).setUv(18 / 256f, 254 / 256f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, 0, 0, 1);
    }

    record Draw(int id, Matrix4f matrix, int color, boolean glow, boolean glass) {}

    /** Segments drawn on an element face: seismograph ink, radar scope graticule and sweep. */
    static final class Lines {
        final Matrix4f matrix;
        final float z, width;
        final boolean glow;
        final FloatArrayList seg = new FloatArrayList();
        final IntArrayList colors = new IntArrayList();

        Lines(Matrix4f matrix, float z, float width, boolean glow) {
            this.matrix = matrix;
            this.z = z;
            this.width = width;
            this.glow = glow;
        }

        void add(float xa, float ya, float xb, float yb, int color) {
            seg.add(xa);
            seg.add(ya);
            seg.add(xb);
            seg.add(yb);
            colors.add(color);
        }
    }

    record Text(Matrix4f matrix, Component text, int color, boolean glow) {}

    /** A soft elliptic light pool (centre, radii) clipped to a rectangle, element space in blocks. */
    record Pool(Matrix4f matrix, float cx, float cy, float rx, float ry, float minX, float minY, float maxX, float maxY) {
        float x(double a) {
            return Math.clamp(cx + rx * (float) Math.cos(a), minX, maxX);
        }

        float y(double a) {
            return Math.clamp(cy + ry * (float) Math.sin(a), minY, maxY);
        }
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final List<String> body = new ArrayList<>();
        final List<Draw> draws = new ArrayList<>();
        final List<Lines> lines = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();
        final List<Pool> pools = new ArrayList<>();
        @Nullable Matrix4f outline;
        float[] outlineBox = new float[6];
    }
}
