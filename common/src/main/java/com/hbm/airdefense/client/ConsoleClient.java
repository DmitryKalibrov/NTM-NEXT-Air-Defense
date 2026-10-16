package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockConsole;
import com.hbm.airdefense.BlockEntityConsole;
import com.hbm.airdefense.BlockEntityEos;
import com.hbm.airdefense.ConsoleCatalog;
import com.hbm.airdefense.ConsoleFeed;
import com.hbm.airdefense.ConsoleFunctions;
import com.hbm.items.ModDataComponents;
import com.hbm.airdefense.ConsoleGeometry;
import com.hbm.airdefense.ItemConsoleKit;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toserver.NbtControlPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * What the player looks at on a console: an element part (pressable, outlined) or, with the mounting kit,
 * a free spot of a face (the ghost of the element about to be mounted). Updated every client tick; a right
 * click on either is turned into a control packet before vanilla sees it.
 */
public final class ConsoleClient {

    /** Element the mounting kit puts down next. */
    public static String selected = "btn_square";

    public static @Nullable BlockPos hoverPos;
    public static int hoverIndex = -1;
    public static String hoverPart = "";

    public static @Nullable BlockPos ghostPos;
    public static int ghostFace, ghostX, ghostY;
    public static boolean ghostOk;

    /** Lines of the hover card ({@link #hint}), refreshed every tick. */
    public static List<Component> card = List.of();

    /** One console action per press of the use key: holding it repeats vanilla's use, not a switch flip. */
    private static boolean armed = true;

    private ConsoleClient() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isDown()) armed = true;
        hoverPos = ghostPos = null;
        if (mc.level == null || mc.player == null || mc.gui.screen() != null) return;
        Player player = mc.player;
        Vec3 eye = player.getEyePosition(1f), look = player.getViewVector(1f);
        double reach = player.blockInteractionRange();
        HitResult vanilla = mc.hitResult;
        double limit = (vanilla != null && vanilla.getType() != HitResult.Type.MISS ? vanilla.getLocation().distanceTo(eye) : reach) + 0.1;
        boolean kit = player.getMainHandItem().is(AirDefense.CONSOLE_KIT.get());
        double best = limit;
        int r = (int) Math.ceil(reach);
        BlockPos at = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r + 2, r))) {
            if (!(mc.level.getBlockEntity(pos) instanceof BlockEntityConsole be)
                    || !(be.getBlockState().getBlock() instanceof BlockConsole block)) continue;
            Direction f = be.getBlockState().getValue(BlockConsole.FACING);
            Vector3f o = new Vector3f((float) (eye.x - pos.getX()), (float) (eye.y - pos.getY()), (float) (eye.z - pos.getZ()));
            Vector3f d = new Vector3f((float) look.x, (float) look.y, (float) look.z);
            List<ConsoleGeometry.Face> faces = ConsoleGeometry.faces(block.kind);
            for (int i = 0; i < be.elements.size(); i++) {
                BlockEntityConsole.Placed p = be.elements.get(i);
                ConsoleCatalog.Element e = ConsoleCatalog.get(p.type);
                if (e == null || p.face >= faces.size()) continue;
                Matrix4f inv = ConsoleGeometry.element(f, faces.get(p.face), p.x, p.y).invert();
                Vector3f lo = inv.transformPosition(new Vector3f(o)), ld = inv.transformDirection(new Vector3f(d));
                double tb = slab(lo, ld, RenderConsole.bounds(p.type, "")), tp = Double.MAX_VALUE;
                String hit = "";
                for (String part : e.parts()) {
                    double tq = slab(lo, ld, RenderConsole.bounds(p.type, part));
                    if (tq < tp) {
                        tp = tq;
                        hit = part;
                    }
                }
                // the nearest moving part wins unless the element's body is clearly in front of it
                if (tp > tb + 0.02) hit = "";
                double t = Math.min(tb, tp);
                if (t < best) {
                    best = t;
                    hoverPos = pos.immutable();
                    hoverIndex = i;
                    hoverPart = hit;
                }
            }
            if (!kit || ConsoleGeometry.corner(mc.level, pos, block.kind) != null) continue;
            for (int fi = 0; fi < faces.size(); fi++) {
                ConsoleGeometry.Face face = faces.get(fi);
                Matrix4f inv = ConsoleGeometry.element(f, face, 0, 0).invert();
                Vector3f lo = inv.transformPosition(new Vector3f(o)), ld = inv.transformDirection(new Vector3f(d));
                if (ld.z >= -1e-6f) continue;
                float t = -lo.z / ld.z, u = (lo.x + ld.x * t) * 16, v = (lo.y + ld.y * t) * 16;
                if (t > best || u < 0 || v < 0 || u > face.w() || v > face.h()) continue;
                ConsoleCatalog.Element e = ConsoleCatalog.get(selected);
                if (e == null) continue;
                best = t;
                hoverPos = null;
                ghostPos = pos.immutable();
                ghostFace = fi;
                ghostX = Mth.clamp(Math.round(u - e.w() / 2f), face.minX(), Math.max(face.minX(), face.maxX() - e.w()));
                ghostY = Mth.clamp(Math.round(v - e.h() / 2f), face.minY(), Math.max(face.minY(), face.maxY() - e.h()));
                ghostOk = be.fits(selected, fi, ghostX, ghostY);
            }
        }
        if (hoverPos != null) ghostPos = null;
    }

    /** Shift + wheel with the kit: the next / previous group or remembered device is selected. True when taken. */
    public static boolean scroll(double dy) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.screen() != null || dy == 0 || !player.isShiftKeyDown()) return false;
        ItemStack held = player.getMainHandItem();
        if (!held.is(AirDefense.CONSOLE_KIT.get())) return false;
        // the ring: groups first, then single devices
        List<String> groups = List.copyOf(ItemConsoleKit.groups(held).keySet());
        List<ItemConsoleKit.Device> mem = ItemConsoleKit.memory(held);
        int n = groups.size() + mem.size();
        if (n == 0) return true;
        String sg = ItemConsoleKit.selectedGroup(held);
        Long cur = held.get(ModDataComponents.RADAR_LINKER_TARGET.get());
        int i = 0;
        if (!sg.isEmpty()) i = groups.indexOf(sg);
        else for (int k = 0; k < mem.size(); k++) if (cur != null && mem.get(k).pos() == cur) i = groups.size() + k;
        int next = Math.floorMod(i + (dy > 0 ? -1 : 1), n);
        if (next < groups.size()) {
            String g = groups.get(next);
            ScreenKitMemory.select(0, g);
            player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.kit_memory.current_group", g, ItemConsoleKit.groups(held).get(g).size()));
        } else {
            ItemConsoleKit.Device d = mem.get(next - groups.size());
            ScreenKitMemory.select(d.pos(), "");
            player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.kit_memory.current",
                    d.label(), BlockPos.of(d.pos()).toShortString()));
        }
        return true;
    }

    /** Ray against a box (element space); distance along the ray, MAX_VALUE when missed. */
    private static double slab(Vector3f o, Vector3f d, float @Nullable [] b) {
        if (b == null) return Double.MAX_VALUE;
        double t0 = 0, t1 = Double.MAX_VALUE;
        for (int a = 0; a < 3; a++) {
            double oa = o.get(a), da = d.get(a), lo = b[a], hi = b[a + 3];
            if (Math.abs(da) < 1e-9) {
                if (oa < lo || oa > hi) return Double.MAX_VALUE;
                continue;
            }
            double ta = (lo - oa) / da, tb = (hi - oa) / da;
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
            if (t0 > t1) return Double.MAX_VALUE;
        }
        return t0;
    }

    /** What a right click does here and now; {@link #use} performs it, {@link #hint} names it. */
    private enum Act { NONE, VANILLA, REMOVE, ASSIGN, PICKER, RADAR, RADAR_UNBOUND, EOS, EOS_UNBOUND, TINT, PRESS, MEMORY, ACCESS, NO_ROOM, PLACE, REMEMBER, SLAVE }

    private static BlockEntityConsole.@Nullable Placed hovered(Minecraft mc) {
        return hoverPos != null && mc.level.getBlockEntity(hoverPos) instanceof BlockEntityConsole hb && hoverIndex < hb.elements.size()
                ? hb.elements.get(hoverIndex) : null;
    }

    private static @Nullable ConsoleFeed feed(Minecraft mc, String key) {
        return hoverPos != null && mc.level.getBlockEntity(hoverPos) instanceof BlockEntityConsole hb ? hb.feeds.get(hoverIndex + "/" + key) : null;
    }

    private static boolean control(BlockEntityConsole.@Nullable Placed hp) {
        return hp != null && ConsoleCatalog.kind(hp.type, hoverPart) != ConsoleCatalog.Kind.NONE;
    }

    private static Act act(Minecraft mc, Player player, ItemStack held, boolean sneak) {
        boolean kit = held.is(AirDefense.CONSOLE_KIT.get()), dye = held.has(DataComponents.DYE);
        if (hoverPos != null) {
            BlockEntityConsole.Placed hp = hovered(mc);
            if (kit) return sneak ? Act.REMOVE : hp != null ? Act.ASSIGN : Act.PICKER;
            if (hp != null && !dye && ConsoleFeed.wantsRadar(hp.type)) return feed(mc, "") != null ? Act.RADAR : Act.RADAR_UNBOUND;
            if (hp != null && !dye && !control(hp) && ConsoleFeed.wantsCamera(hp.type)) {
                ConsoleFeed f = feed(mc, ConsoleFeed.key(hp.type, hoverPart));
                if (f != null && f.kind().equals("eos")) return Act.EOS;
                if (!hp.type.equals("tv_screen")) return Act.EOS_UNBOUND;
            }
            return dye ? Act.TINT : Act.PRESS;
        }
        if (!kit) return Act.NONE;
        if (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) return sneak ? Act.MEMORY : Act.PICKER;
        // shift-click on a radar or launcher remembers it, a click on a station slaves it (ItemConsoleKit.useOn)
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            BlockEntity be = mc.level.getBlockEntity(bh.getBlockPos());
            if (be instanceof BlockEntityEos) return sneak ? Act.REMEMBER : Act.SLAVE;
            if (sneak && !(be instanceof BlockEntityConsole)) return ItemConsoleKit.deviceCore(mc.level, bh.getBlockPos()) != null ? Act.REMEMBER : Act.VANILLA;
        }
        if (sneak && ghostPos != null && mc.level.getBlockEntity(ghostPos) instanceof BlockEntityConsole gb && player.getUUID().equals(gb.owner)) return Act.ACCESS;
        if (sneak || ghostPos == null) return Act.PICKER;
        return ghostOk ? Act.PLACE : Act.NO_ROOM;
    }

    /** Right click. True when the console took it (vanilla's use is then skipped). */
    public static boolean use() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (mc.level == null || player == null) return false;
        tick();
        ItemStack held = player.getMainHandItem();
        boolean sneak = player.isShiftKeyDown();
        CompoundTag t = new CompoundTag();
        BlockPos target = hoverPos;
        if (hoverPos != null) t.putInt("i", hoverIndex);
        BlockEntityConsole.Placed hp = hovered(mc);
        switch (act(mc, player, held, sneak)) {
            case NONE, VANILLA, REMEMBER, SLAVE -> {
                return false;
            }
            case REMOVE -> t.putString("op", "remove");
            case ASSIGN -> {
                // set the element up: its label, a function for a control, binding a screen or column to the remembered device
                boolean control = control(hp);
                boolean bindable = !control && (ConsoleFeed.wantsRadar(hp.type) || ConsoleFeed.wantsLauncher(hp.type) || ConsoleFeed.wantsCamera(hp.type));
                List<BlockPos> sel = ItemConsoleKit.selection(held);  // a group answers for its first device
                String dev = sel.isEmpty() ? "" : ItemConsoleKit.kind(held, mc.level, sel.get(0));  // out of view: as the kit remembers it
                if (dev.equals("lost")) dev = "";
                String part = control ? ConsoleCatalog.base(hoverPart)
                        : hp.type.equals("readiness") && hoverPart.startsWith("lens") ? hoverPart : "";
                List<String> choices = control || ConsoleFunctions.meter(hp.type) || hp.type.equals("speaker") ? ConsoleFunctions.choices(hp.type, part, dev) : List.of();
                boolean canBind = bindable && (dev.equals("radar") ? ConsoleFeed.wantsRadar(hp.type)
                        : dev.equals("eos") ? ConsoleFeed.wantsCamera(hp.type) : !dev.isEmpty() && ConsoleFeed.wantsLauncher(hp.type));
                mc.setScreenAndShow(new ScreenAssign(hoverPos, hoverIndex, part, dev, choices, hp.fn.get(part), hp.labels.getOrDefault(part, ""),
                        canBind ? hoverPart : null));
                return true;
            }
            case PICKER -> {
                mc.setScreenAndShow(new ScreenElementPicker());
                return true;
            }
            case RADAR -> {
                mc.setScreenAndShow(new ScreenRadar(hoverPos, hoverIndex));  // sit at the scope
                return true;
            }
            case RADAR_UNBOUND -> {
                player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.radar.unbound"));
                return true;
            }
            case EOS -> {
                // a screen or stick bound to an optoelectronic station: look through it
                String key = ConsoleFeed.key(hp.type, hoverPart);
                mc.setScreenAndShow(new ScreenEos(hoverPos, hoverIndex, key, BlockPos.of(feed(mc, key).pos())));
                return true;
            }
            case EOS_UNBOUND -> {
                player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.eos.unbound"));
                return true;
            }
            case TINT -> t.putString("op", "tint");
            case PRESS -> {
                t.putString("op", "press");
                t.putString("part", hoverPart);
                t.putBoolean("back", sneak);
            }
            case MEMORY -> {
                mc.setScreenAndShow(new ScreenKitMemory());
                return true;
            }
            case ACCESS -> {
                mc.setScreenAndShow(new ScreenAccess(ghostPos));  // the owner's access list
                return true;
            }
            case NO_ROOM -> {
                player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.console_kit.no_room"));
                return true;
            }
            case PLACE -> {
                target = ghostPos;
                t.putString("op", "place");
                t.putString("type", selected);
                t.putInt("face", ghostFace);
                t.putInt("x", ghostX);
                t.putInt("y", ghostY);
            }
        }
        if (!armed) return true;
        armed = false;
        PacketWire.sendToServer(new NbtControlPayload(target, t));
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * The hover card next to the crosshair: what is looked at (label, function, the device it works), what the kit
     * holds, and what a click and a shift-click would do. Empty when there is nothing to say.
     */
    public static List<Component> hint() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        List<Component> out = new ArrayList<>();
        if (mc.level == null || player == null || mc.gui.screen() != null) return out;
        ItemStack held = player.getMainHandItem();
        boolean kit = held.is(AirDefense.CONSOLE_KIT.get());
        BlockEntityConsole.Placed hp = hovered(mc);
        if (hp != null) {
            boolean control = control(hp);
            String part = control ? ConsoleCatalog.base(hoverPart) : hp.type.equals("readiness") && hoverPart.startsWith("lens") ? hoverPart : "";
            String label = hp.labels.getOrDefault(part, "");
            out.add(label.isEmpty() || label.startsWith("#") ? Component.translatable("element.ntm_air_defense." + hp.type).withStyle(ChatFormatting.WHITE)
                    : Component.literal(label).withStyle(ChatFormatting.WHITE));
            String fn = hp.fn.get(part);
            if (fn != null) out.add(Component.translatable("gui.ntm_air_defense.fn." + fn).withStyle(ChatFormatting.GOLD));
            String key = control ? part : ConsoleFeed.key(hp.type, hoverPart);
            ConsoleFeed f = feed(mc, key);
            if (f == null) f = feed(mc, "");
            BlockPos link = hp.links.containsKey(key) ? hp.links.get(key) : hp.links.get("");
            if (f != null && !f.kind().equals("lost")) {
                int more = hp.group.getOrDefault(part, List.of()).size();
                out.add(Component.translatable("gui.ntm_air_defense.hint.linked", device(f.label(), BlockPos.of(f.pos())))
                        .append(more > 0 ? Component.translatable("gui.ntm_air_defense.hint.more", more) : Component.empty())
                        .withStyle(ChatFormatting.GREEN));
            } else if (link != null) {
                out.add(Component.translatable("gui.ntm_air_defense.hint.lost", link.toShortString()).withStyle(ChatFormatting.RED));
            }
        } else if (kit) {
            List<BlockPos> sel = ItemConsoleKit.selection(held);
            String grp = ItemConsoleKit.selectedGroup(held);
            out.add(Component.translatable("item.ntm_air_defense.console_kit").withStyle(ChatFormatting.WHITE));
            out.add(!grp.isEmpty() ? Component.translatable("gui.ntm_air_defense.hint.kit_group", grp, sel.size()).withStyle(ChatFormatting.GREEN)
                    : sel.isEmpty() ? Component.translatable("gui.ntm_air_defense.hint.kit_empty").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("gui.ntm_air_defense.hint.kit_device",
                            device(ItemConsoleKit.label(held, sel.get(0), ConsoleFeed.poll(mc.level, sel.get(0)).kind()), sel.get(0))).withStyle(ChatFormatting.GREEN));
        } else {
            return out;  // nothing of ours under the crosshair
        }
        Act a = act(mc, player, held, false), b = act(mc, player, held, true);
        if (shown(a)) out.add(key(mc.options.keyUse.getTranslatedKeyMessage(), a));
        if (shown(b) && b != a) out.add(key(mc.options.keyShift.getTranslatedKeyMessage().copy().append("+").append(mc.options.keyUse.getTranslatedKeyMessage()), b));
        return out;
    }

    /** A plain press needs no explaining; nothing happening needs no line either. */
    private static boolean shown(Act a) {
        return a != Act.NONE && a != Act.VANILLA && a != Act.PRESS;
    }

    private static Component key(Component key, Act a) {
        Component what = a == Act.PLACE ? Component.translatable("gui.ntm_air_defense.hint.place", Component.translatable("element.ntm_air_defense." + selected))
                : Component.translatable("gui.ntm_air_defense.hint." + a.name().toLowerCase(Locale.ROOT));
        return Component.literal("[").append(key).append("] ").withStyle(ChatFormatting.YELLOW).append(what.copy().withStyle(ChatFormatting.GRAY));
    }

    private static Component device(Component label, BlockPos pos) {
        return Component.translatable("gui.ntm_air_defense.hint.device", label, pos.toShortString());
    }
}
