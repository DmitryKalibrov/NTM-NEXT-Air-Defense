package com.hbm.airdefense.client;

import com.hbm.airdefense.BlockEntityEos;
import com.hbm.airdefense.EntitySAM;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.NbtControlPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Behind the optoelectronic station: the game is seen from its lenses (the client's camera entity moves to a
 * marker at the head), with the sight drawn over it. TV (dim, noisy) or thermal (dark, warm bodies white), five
 * zoom steps, laser range to whatever is in the cross, heading and elevation. WASD or a mouse drag steers (free
 * mode), F locks what is in the cross (track, and the launcher gets it), R gives the head back to the launcher.
 */
public class ScreenEos extends Screen {

    private static final float[] FOV = {50, 25, 12, 6, 3};
    private static final int SIGHT = 0xFFE8F0E8, DIM = 0xC0A8B4A8;

    /** The view open now (for the field of view and the panel stick), or null. */
    public static @Nullable ScreenEos active;

    private final BlockPos console, eosPos;
    private final int index;
    private final String key;
    private @Nullable Entity cam;
    private @Nullable Entity prevCam;
    private boolean prevHideGui, thermal;
    private int zoom, age, sent;
    private float aimYaw, aimPitch;
    private boolean steering;
    /** Stick deflection for the panel's joystick, -1..1. */
    public float stickX, stickY;
    private double range = -1;
    private @Nullable Entity inCross;

    public ScreenEos(BlockPos console, int index, String key, BlockPos eosPos) {
        super(Component.translatable("gui.ntm_air_defense.eos.title"));
        this.console = console;
        this.index = index;
        this.key = key;
        this.eosPos = eosPos;
    }

    public boolean drives(BlockPos pos, int i) {
        return console.equals(pos) && index == i;
    }

    public float fov() {
        return FOV[zoom];
    }

    private @Nullable BlockEntityEos eos() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(eosPos) instanceof BlockEntityEos e ? e : null;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        BlockEntityEos e = eos();
        if (cam != null || mc.level == null || e == null) return;
        cam = EntityTypes.MARKER.create(mc.level, EntitySpawnReason.LOAD);
        if (cam == null) return;
        aimYaw = e.yaw;
        aimPitch = e.pitch;
        cam.snapTo(e.eye(e.yaw, e.pitch), e.yaw, e.pitch);
        cam.setOldPosAndRot();
        prevCam = mc.getCameraEntity();
        prevHideGui = mc.gui.hud.isHidden();
        if (!prevHideGui) mc.gui.hud.toggle();  // the sight replaces the hotbar and hand
        mc.setCameraEntity(cam);
        active = this;
    }

    @Override
    public void tick() {
        age++;
        BlockEntityEos e = eos();
        if (e == null || cam == null) {
            onClose();
            return;
        }
        // steering: keys, scaled down as the view narrows
        var w = Minecraft.getInstance().getWindow();
        float rate = 2.5f * fov() / 50;
        float sx = (InputConstants.isKeyDown(w, InputConstants.KEY_D) ? 1 : 0) - (InputConstants.isKeyDown(w, InputConstants.KEY_A) ? 1 : 0);
        float sy = (InputConstants.isKeyDown(w, InputConstants.KEY_S) ? 1 : 0) - (InputConstants.isKeyDown(w, InputConstants.KEY_W) ? 1 : 0);
        if (sx != 0 || sy != 0) {
            aimYaw += sx * rate;
            aimPitch = Mth.clamp(aimPitch + sy * rate, -85, 30);
            steering = true;
        }
        stickX += (sx - stickX) * 0.4f;
        stickY += (sy - stickY) * 0.4f;
        if (steering && ++sent >= 2) {  // the head follows the operator
            sent = 0;
            steering = false;
            CompoundTag t = op("aim");
            t.putFloat("yaw", aimYaw);
            t.putFloat("pitch", aimPitch);
            PacketWire.sendToServer(new NbtControlPayload(console, t));
        }
        if (e.mode != BlockEntityEos.FREE) {  // slaved or tracking: the operator's aim rides along
            aimYaw = e.yaw;
            aimPitch = e.pitch;
        }
        cam.setOldPosAndRot();
        cam.snapTo(e.eye(e.yaw, e.pitch), e.yaw, e.pitch);
        laser(e);
    }

    /** Laser rangefinder: the nearest block or body along the line of sight, up to 2 km. */
    private void laser(BlockEntityEos e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || cam == null) return;
        Vec3 from = cam.position(), dir = Vec3.directionFromRotation(e.pitch, e.yaw), to = from.add(dir.scale(2000));
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
        double best = hit.getType() == HitResult.Type.MISS ? -1 : hit.getLocation().distanceTo(from);
        inCross = null;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en == mc.player || en == cam || !target(en)) continue;
            AABB box = en.getBoundingBox().inflate(Math.max(0.5, en.position().distanceTo(from) * 0.004));  // a few mrad of beam
            var c = box.clip(from, to);
            if (c.isPresent()) {
                double d = c.get().distanceTo(from);
                if (best < 0 || d < best) {
                    best = d;
                    inCross = en;
                }
            }
        }
        range = best;
    }

    private static boolean target(Entity e) {
        return e instanceof LivingEntity || e instanceof EntityMissileBaseNT || e instanceof EntitySAM;
    }

    private CompoundTag op(String cmd) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "eos");
        t.putInt("i", index);
        t.putString("part", key);
        t.putString("cmd", cmd);
        return t;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        BlockEntityEos e = eos();
        int cx = width / 2, cy = height / 2;
        float yaw = e == null ? 0 : Mth.rotLerp(partial, e.prevYaw, e.yaw), pitch = e == null ? 0 : Mth.lerp(partial, e.prevPitch, e.pitch);
        if (thermal) {
            g.fill(0, 0, width, height, 0xC8141814);  // the cold world goes dark, bodies glow white through it
            hot(g, yaw, pitch, partial);
        } else {
            g.fill(0, 0, width, height, 0x1E1E2A1E);
        }
        // noise, scanlines, vignette
        Random r = new Random(age * 7919L);
        for (int k = 0; k < 40; k++) {
            int x = r.nextInt(width), y = r.nextInt(height);
            g.fill(x, y, x + 1 + r.nextInt(6), y + 1, thermal ? 0x30FFFFFF : 0x28FFFFFF);
        }
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x18000000);
        for (int k = 0; k < 24; k++) {
            int c = (0x50 - k * 3) << 24;
            g.fill(0, k, width, k + 1, c);
            g.fill(0, height - k - 1, width, height - k, c);
            g.fill(k, 0, k + 1, height, c);
            g.fill(width - k - 1, 0, width - k, height, c);
        }
        // sight: cross with a gap, range brackets
        int gap = 6, arm = Math.min(width, height) / 6;
        g.fill(cx - arm, cy, cx - gap, cy + 1, SIGHT);
        g.fill(cx + gap, cy, cx + arm, cy + 1, SIGHT);
        g.fill(cx, cy - arm, cx + 1, cy - gap, SIGHT);
        g.fill(cx, cy + gap, cx + 1, cy + arm, SIGHT);
        int b = arm + 20;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * b, y = cy + sy * (b * 2 / 3);
                g.fill(Math.min(x, x - sx * 12), y, Math.max(x, x - sx * 12) + 1, y + 1, DIM);
                g.fill(x, Math.min(y, y - sy * 12), x + 1, Math.max(y, y - sy * 12) + 1, DIM);
            }
        }
        // the tracked contact: a box that follows it
        Minecraft mc = Minecraft.getInstance();
        if (e != null && e.mode == BlockEntityEos.TRACK && mc.level != null) {
            Entity t = mc.level.getEntity(e.trackId);
            float[] p = t == null ? null : project(t.getBoundingBox().inflate(0.3), yaw, pitch, partial);
            if (p != null && (age / 4) % 4 != 0) g.outline((int) p[0], (int) p[1], (int) Math.max(6, p[2] - p[0]), (int) Math.max(6, p[3] - p[1]), SIGHT);
        }
        // heading tape and elevation
        int hdg = Math.floorMod(Math.round(yaw + 180), 360);
        g.text(font, String.format("%03d", hdg), cx - 8, 14, SIGHT, false);
        for (int d = -30; d <= 30; d += 5) {
            int x = cx + (int) (d * height / fov());  // vertical field of view: height / fov pixels per degree
            int tick = Math.floorMod(hdg + d, 360);
            if (x < 20 || x > width - 20) continue;
            g.fill(x, 26, x + 1, tick % 10 == 0 ? 32 : 29, DIM);
        }
        g.text(font, String.format("%+03d", Math.round(-pitch)), 14, cy - 4, SIGHT, false);
        // data
        String mode = e == null ? "" : switch (e.mode) {
            case BlockEntityEos.TRACK -> "track";
            case BlockEntityEos.FREE -> "free";
            default -> "slave";
        };
        g.text(font, Component.translatable("screen.ntm_air_defense.eos." + mode), 14, height - 44, SIGHT, false);
        g.text(font, Component.translatable(thermal ? "screen.ntm_air_defense.eos.ir" : "screen.ntm_air_defense.eos.tv", Math.round(50 / fov())), 14, height - 32, SIGHT, false);
        g.text(font, range < 0 ? "Д ----" : String.format("Д %04d", Math.round(range)), width - 70, height - 44, SIGHT, false);
        if (inCross != null) g.text(font, Component.translatable("screen.ntm_air_defense.eos.contact"), width - 70, height - 32, SIGHT, false);
        g.text(font, Component.translatable("screen.ntm_air_defense.eos.hint"), 14, height - 16, DIM, false);
        super.extractRenderState(g, mx, my, partial);
    }

    /** Thermal: every body in view as a white-hot blob over its outline. */
    private void hot(GuiGraphicsExtractor g, float yaw, float pitch, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || cam == null) return;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en == cam || !target(en) || en.position().distanceTo(cam.position()) > 2000) continue;
            float[] p = project(en.getBoundingBox(), yaw, pitch, partial);
            if (p == null) continue;
            int x0 = (int) p[0], y0 = (int) p[1], x1 = Math.max(x0 + 2, (int) p[2]), y1 = Math.max(y0 + 2, (int) p[3]);
            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0x60E0E0E0);
            g.fill(x0, y0, x1, y1, en instanceof LivingEntity ? 0xF0F4F4F4 : 0xF0FFFFFF);
        }
    }

    /** Screen box of a world box seen from the camera, null when (partly) behind it or off screen. */
    private float @Nullable [] project(AABB box, float yaw, float pitch, float partial) {
        if (cam == null) return null;
        Vec3 eye = cam.getPosition(partial), f = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(f);
        double k = height / 2.0 / Math.tan(Math.toRadians(fov() / 2));
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            Vec3 v = new Vec3((c & 1) == 0 ? box.minX : box.maxX, (c & 2) == 0 ? box.minY : box.maxY, (c & 4) == 0 ? box.minZ : box.maxZ).subtract(eye);
            double z = v.dot(f);
            if (z < 0.2) return null;
            float x = (float) (width / 2.0 + v.dot(right) / z * k), y = (float) (height / 2.0 - v.dot(up) / z * k);
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
        }
        return x1 < 0 || y1 < 0 || x0 > width || y0 > height ? null : new float[]{x0, y0, x1, y1};
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_T -> thermal = !thermal;
            case InputConstants.KEY_F -> {
                if (inCross != null) {
                    CompoundTag t = op("lock");
                    t.putInt("id", inCross.getId());
                    PacketWire.sendToServer(new NbtControlPayload(console, t));
                }
            }
            case InputConstants.KEY_R -> PacketWire.sendToServer(new NbtControlPayload(console, op("slave")));
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        aimYaw += (float) dx * 0.15f * fov() / 50;
        aimPitch = Mth.clamp(aimPitch + (float) dy * 0.15f * fov() / 50, -85, 30);
        stickX = Mth.clamp((float) dx * 0.3f, -1, 1);
        stickY = Mth.clamp((float) dy * 0.3f, -1, 1);
        steering = true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        zoom = Mth.clamp(zoom + (dy > 0 ? 1 : -1), 0, FOV.length - 1);
        return true;
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (active == this) {
            active = null;
            if (mc.gui.hud.isHidden() != prevHideGui) mc.gui.hud.toggle();
            mc.setCameraEntity(prevCam != null ? prevCam : mc.player);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {}
}
