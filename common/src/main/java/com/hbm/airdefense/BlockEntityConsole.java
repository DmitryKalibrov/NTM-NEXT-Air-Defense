package com.hbm.airdefense;

import com.hbm.api.control.IControlReceiver;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One console block: the elements mounted on its faces, the state of their moving parts and its owner.
 * Clients ask for changes through NTM's NbtControlPayload (receiveControl, after hasPermission and a reach check);
 * every change is synced back with the vanilla block entity update.
 */
public class BlockEntityConsole extends BlockEntity implements IControlReceiver {

    /** An element on face `face` of this block, lower left corner at grid px (x, y). */
    public static final class Placed {
        public final String type;
        public final int face, x, y;
        public int tint;
        public final Map<String, Integer> state;
        /** Device each screen of this element shows, by binding key (see {@link ConsoleFeed#key}), or each assigned control part works. */
        public final Map<String, BlockPos> links;
        /** Function of each assigned control part (see {@link ConsoleFunctions}), by part. */
        public final Map<String, String> fn;
        /** Player-written label per part ("" = the element's own plate); "#fn" = the short name of a function. */
        public final Map<String, String> labels;
        /** More devices a control drives besides its link (a group of launchers). */
        public final Map<String, List<BlockPos>> group;

        Placed(String type, int face, int x, int y, int tint, Map<String, Integer> state, Map<String, BlockPos> links, Map<String, String> fn,
               Map<String, String> labels, Map<String, List<BlockPos>> group) {
            this.type = type;
            this.face = face;
            this.x = x;
            this.y = y;
            this.tint = tint;
            this.state = new HashMap<>(state);
            this.links = new HashMap<>(links);
            this.fn = new HashMap<>(fn);
            this.labels = new HashMap<>(labels);
            this.group = new HashMap<>();
            group.forEach((k, v) -> this.group.put(k, new ArrayList<>(v)));
        }

        /** Every device a part drives: its link, then its group. */
        public List<BlockPos> devices(String part) {
            List<BlockPos> out = new ArrayList<>();
            BlockPos l = links.get(part);
            if (l != null) out.add(l);
            out.addAll(group.getOrDefault(part, List.of()));
            return out;
        }

        public int state(String part) {
            String p = ConsoleCatalog.base(part);
            return state.getOrDefault(p, ConsoleCatalog.initial(type, p));
        }

        static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(p -> p.type),
                Codec.INT.fieldOf("face").forGetter(p -> p.face),
                Codec.INT.fieldOf("x").forGetter(p -> p.x),
                Codec.INT.fieldOf("y").forGetter(p -> p.y),
                Codec.INT.optionalFieldOf("tint", 0xFFFFFFFF).forGetter(p -> p.tint),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("state", Map.of()).forGetter(p -> p.state),
                Codec.unboundedMap(Codec.STRING, BlockPos.CODEC).optionalFieldOf("links", Map.of()).forGetter(p -> p.links),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("fn", Map.of()).forGetter(p -> p.fn),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("labels", Map.of()).forGetter(p -> p.labels),
                Codec.unboundedMap(Codec.STRING, BlockPos.CODEC.listOf()).optionalFieldOf("group", Map.of()).forGetter(p -> p.group)
        ).apply(i, Placed::new));
    }

    public final List<Placed> elements = new ArrayList<>();
    public @Nullable UUID owner;
    /** Players the owner let use the console (uuid -> name, for the access list). */
    public final Map<UUID, String> access = new HashMap<>();
    /** Missile alarm of the whole console: 0 none, 1 acknowledged, 2 new (buzzer, flashing lamps). Synced. */
    public int alarm;
    /** This block's own alarm, from its radar screens (server); the unit shows the highest. */
    private int ownAlarm;
    private final java.util.Set<Integer> knownMissiles = new java.util.HashSet<>();
    /** Recent blasts felt here, for the seismographs (see {@link Seismic}). */
    public final List<Seismic.Quake> quakes = new ArrayList<>();
    /** What each bound screen shows, by "element index/binding key"; polled on the server, sent with updates. */
    public final Map<String, ConsoleFeed> feeds = new HashMap<>();
    static final int BIND_RANGE = 512;
    private static final Codec<Map<String, String>> ACCESS = Codec.unboundedMap(Codec.STRING, Codec.STRING);
    private static final Codec<Map<String, ConsoleFeed>> FEEDS = Codec.unboundedMap(Codec.STRING, ConsoleFeed.CODEC);
    /** Client only: last seen press counters and when each press arrived, to replay push buttons. */
    public final Map<String, Integer> clientSeen = new HashMap<>();
    public final Map<String, Float> clientPressed = new HashMap<>();
    /** Client only: where each bound dial's needle is, so it swings instead of jumping. */
    public final Map<Integer, Float> clientNeedle = new HashMap<>();

    public BlockEntityConsole(BlockPos pos, BlockState state) {
        super(AirDefense.CONSOLE_BE.get(), pos, state);
    }

    public ConsoleGeometry.Kind kind() {
        return getBlockState().getBlock() instanceof BlockConsole b ? b.kind : ConsoleGeometry.Kind.PANEL;
    }

    /** Would `type` fit at (x, y) of `face` without leaving it or covering another element? */
    public boolean fits(String type, int face, int x, int y) {
        ConsoleCatalog.Element e = ConsoleCatalog.get(type);
        List<ConsoleGeometry.Face> faces = ConsoleGeometry.faces(kind());
        if (e == null || face < 0 || face >= faces.size()) return false;
        if (level != null && ConsoleGeometry.corner(level, worldPosition, kind()) != null) return false;  // mitred: no faces to mount on
        ConsoleGeometry.Face f = faces.get(face);
        if (e.levelOnly() && !f.level()) return false;
        if (x < f.minX() || y < f.minY() || x + e.w() > f.maxX() || y + e.h() > f.maxY()) return false;
        for (Placed p : elements) {
            ConsoleCatalog.Element o = ConsoleCatalog.get(p.type);
            if (p.face == face && o != null && x < p.x + o.w() && p.x < x + e.w() && y < p.y + o.h() && p.y < y + e.h()) return false;
        }
        return true;
    }

    /** Game time until which someone has the radar interface open: then devices are polled twice as often. */
    private long watchedUntil;

    public void tickServer() {
        if (level == null) return;
        long t = level.getGameTime();
        if (t % (t < watchedUntil ? 5 : 10) == 0 && elements.stream().anyMatch(p -> !p.links.isEmpty())) poll();
        if (t % 10 == 0) {
            int a = 0;
            List<BlockEntityConsole> unit = unit();
            for (BlockEntityConsole b : unit) a = Math.max(a, b.ownAlarm);
            soundAt = leaderCentre(unit);
            if (a != alarm) {
                alarm = a;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        // one alarm for the whole console, from its middle, not one per screen block
        if (alarm == 2 && soundAt != null && t % 30 == 0) {
            level.playSound(null, soundAt.x, soundAt.y, soundAt.z, com.hbm.sound.ModSounds.WARN_OVERSPEED.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    /** New missiles on a radar screen of this block raise the alarm; it ends when none are left. */
    private void watchMissiles() {
        java.util.Set<Integer> now = new java.util.HashSet<>();
        for (ConsoleFeed f : feeds.values()) {
            if (!f.kind().equals("radar")) continue;
            List<Integer> bl = f.blips();
            for (int k = 0; k + ConsoleFeed.BLIP - 1 < bl.size(); k += ConsoleFeed.BLIP) {
                if (bl.get(k + 4) >= 0 && bl.get(k + 4) <= 10) now.add(bl.get(k + 3));
            }
        }
        if (now.isEmpty()) ownAlarm = 0;
        else if (!knownMissiles.containsAll(now)) ownAlarm = 2;
        knownMissiles.clear();
        knownMissiles.addAll(now);
    }

    /** This block and the console blocks joined to it (touching, same owner), at most 64. */
    public List<BlockEntityConsole> unit() {
        List<BlockEntityConsole> out = new ArrayList<>();
        if (level == null) return out;
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>(List.of(worldPosition));
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(open);
        while (!open.isEmpty() && out.size() < 64) {
            BlockPos at = open.poll();
            if (!(level.getBlockEntity(at) instanceof BlockEntityConsole b) || !java.util.Objects.equals(b.owner, owner)) continue;
            out.add(b);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = at.relative(d);
                if (seen.add(n)) open.add(n);
            }
        }
        return out;
    }

    /** A console with key switches works only while one of them is turned on. */
    public boolean locked() {
        boolean keyed = false;
        for (BlockEntityConsole b : unit()) {
            for (Placed p : b.elements) {
                if (!p.type.equals("key_switch")) continue;
                if (p.state("key") == 1) return false;
                keyed = true;
            }
        }
        return keyed;
    }

    private void ack() {
        for (BlockEntityConsole b : unit()) if (b.ownAlarm == 2) b.ownAlarm = 1;
    }

    /** Ask every bound device for its state; send an update when a screen would change. */
    private void poll() {
        Map<String, ConsoleFeed> now = new HashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            for (Map.Entry<String, BlockPos> l : elements.get(i).links.entrySet()) now.put(i + "/" + l.getKey(), ConsoleFeed.poll(level, l.getValue()));
        }
        if (now.equals(feeds)) return;
        feeds.clear();
        feeds.putAll(now);
        watchMissiles();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Do this console's speakers voice dev? Yes unless a key with the "speaker" function for dev is switched off
     * (several keys for one device: all must be on). No key at all: always on.
     */
    public boolean speakerOn(BlockPos dev) {
        for (BlockEntityConsole b : unit()) {
            for (Placed p : b.elements) {
                for (Map.Entry<String, String> e : p.fn.entrySet()) {
                    if (e.getValue().equals("speaker") && p.devices(e.getKey()).contains(dev) && p.state(e.getKey()) == 0) return false;
                }
            }
        }
        return true;
    }

    /** A speaker's volume from its knob (6 positions), 0.15 .. 1. */
    public static float speakerVolume(Placed p) {
        return 0.15f + 0.85f * p.state("knob") / (ConsoleCatalog.positions(p.type) - 1);
    }

    /**
     * The console unit's sounds (hum, alarm) come from one block, the lowest with elements, and are placed in the
     * middle of the whole unit; null on every other block.
     */
    Vec3 soundAt;

    private Vec3 leaderCentre(List<BlockEntityConsole> unit) {
        if (elements.isEmpty() || !unit.stream().filter(b -> !b.elements.isEmpty()).map(BlockEntity::getBlockPos)
                .min(BlockPos::compareTo).map(worldPosition::equals).orElse(false)) return null;
        Vec3 sum = Vec3.ZERO;
        for (BlockEntityConsole b : unit) sum = sum.add(Vec3.atCenterOf(b.getBlockPos()));
        return sum.scale(1.0 / unit.size());
    }

    public void tickClient() {
        if (level != null && level.getGameTime() % 40 == 0) soundAt = leaderCentre(unit());
        if (soundAt != null) SamSounds.console(this);
    }

    public boolean hasSeismograph() {
        return elements.stream().anyMatch(p -> p.type.equals("seismograph"));
    }

    /** A blast reached this console: keep the last few quakes, drop those whose coda has died out. */
    void quake(Seismic.Quake q) {
        long now = level == null ? 0 : level.getGameTime();
        quakes.removeIf(o -> now - o.s() > 600);
        if (quakes.size() >= 8) quakes.remove(0);
        quakes.add(q);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) Seismic.STATIONS.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Seismic.STATIONS.remove(this);
    }

    @Override
    public boolean hasPermission(Player player) {
        return owner == null || owner.equals(player.getUUID()) || access.containsKey(player.getUUID()) || AirDefenseTeams.mayUse(owner, player);
    }

    @Override
    public void receiveControl(CompoundTag data) {}

    @Override
    public void receiveControl(Player player, CompoundTag data) {
        String op = data.getStringOr("op", "");
        int i = data.getIntOr("i", -1);
        Placed p = i >= 0 && i < elements.size() ? elements.get(i) : null;
        switch (op) {
            case "press" -> {
                if (p == null) return;
                if (!p.type.equals("key_switch") && locked()) {
                    sound(SoundEvents.DISPENSER_FAIL, 1.6f);  // keyed off: the controls are dead
                    return;
                }
                press(p, data.getStringOr("part", ""), data.getBooleanOr("back", false), player);
            }
            case "label" -> {
                if (p == null || !player.getMainHandItem().is(AirDefense.CONSOLE_KIT.get())) return;
                String part = ConsoleCatalog.base(data.getStringOr("part", "")), text = data.getStringOr("text", "").strip();
                if (text.length() > 16) text = text.substring(0, 16);
                String fn = p.fn.get(part);
                if (!text.isEmpty()) p.labels.put(part, text);
                else if (fn != null) p.labels.put(part, "#" + fn);  // back to the function's name
                else p.labels.remove(part);
            }
            case "eos" -> {  // the camera view at this console steers the station bound to the screen / stick
                BlockPos dev = p == null ? null : p.links.get(data.getStringOr("part", ""));
                if (dev == null || locked() || !(level.getBlockEntity(dev) instanceof BlockEntityEos eos)) return;
                eos.command(data);
                return;
            }
            case "access" -> {  // the owner lets a player use the whole console, or takes it back
                if (owner == null || !owner.equals(player.getUUID())) return;
                UUID who;
                try {
                    who = UUID.fromString(data.getStringOr("uuid", ""));
                } catch (IllegalArgumentException ex) {
                    return;
                }
                if (who.equals(owner)) return;
                for (BlockEntityConsole b : unit()) {
                    if (data.getBooleanOr("allow", false)) b.access.put(who, data.getStringOr("name", "?"));
                    else b.access.remove(who);
                    if (b != this) b.changed();
                }
            }
            case "place" -> {
                String type = data.getStringOr("type", "");
                int face = data.getIntOr("face", -1), x = data.getIntOr("x", -1), y = data.getIntOr("y", -1);
                if (!player.getMainHandItem().is(AirDefense.CONSOLE_KIT.get()) || !fits(type, face, x, y)) return;
                elements.add(new Placed(type, face, x, y, 0xFFFFFFFF, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
                sound(SoundEvents.METAL_PLACE, 1.0f);
            }
            case "remove" -> {
                if (p == null || !player.getMainHandItem().is(AirDefense.CONSOLE_KIT.get())) return;
                elements.remove(i);
                sound(SoundEvents.CHAIN_PLACE, 1.2f);
            }
            case "bind" -> {
                Long at = player.getMainHandItem().get(com.hbm.items.ModDataComponents.RADAR_LINKER_TARGET.get());
                if (p == null || at == null || !player.getMainHandItem().is(AirDefense.CONSOLE_KIT.get())) return;
                BlockPos dev = BlockPos.of(at);
                String kind = ItemConsoleKit.kind(player.getMainHandItem(), level, dev);  // an unloaded device: as the kit remembers it
                boolean sam = !kind.equals("radar") && !kind.equals("lost") && !kind.equals("eos");
                boolean ok = ConsoleFeed.wantsRadar(p.type) ? kind.equals("radar")
                        : ConsoleFeed.wantsLauncher(p.type) && sam || ConsoleFeed.wantsCamera(p.type) && kind.equals("eos");
                if (!ok || dev.distSqr(worldPosition) > BIND_RANGE * BIND_RANGE) {
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.ntm_air_defense.console_kit.wrong_device"));
                    return;
                }
                p.links.put(ConsoleFeed.key(p.type, data.getStringOr("part", "")), dev);
                sound(SoundEvents.CHAIN_PLACE, 1.6f);
                poll();
            }
            case "assign" -> {  // a control part takes a function of the kit's selected device or group ("" clears it)
                List<BlockPos> sel = ItemConsoleKit.selection(player.getMainHandItem());
                String part = ConsoleCatalog.base(data.getStringOr("part", "")), fn = data.getStringOr("fn", "");
                if (p == null || !player.getMainHandItem().is(AirDefense.CONSOLE_KIT.get())) return;
                String old = p.fn.get(part);
                if (fn.isEmpty()) {
                    p.fn.remove(part);
                    p.links.remove(part);
                    p.group.remove(part);
                    if (old != null && p.labels.getOrDefault(part, "").equals("#" + old)) p.labels.remove(part);
                } else if (ConsoleFunctions.internal(fn)) {
                    if (!ConsoleFunctions.choices(p.type, part, "").contains(fn)) return;
                    p.fn.put(part, fn);
                    p.links.remove(part);
                    p.group.remove(part);
                } else if (data.getBooleanOr("add", false) && fn.equals(old) && !sel.isEmpty() && p.links.containsKey(part)) {
                    List<BlockPos> g = p.group.computeIfAbsent(part, k -> new ArrayList<>());  // more launchers for this control
                    for (BlockPos dev : sel) {
                        if (launcherNear(player, dev) && !dev.equals(p.links.get(part)) && !g.contains(dev)) g.add(dev);
                    }
                } else {
                    if (sel.isEmpty()) return;
                    BlockPos dev = sel.get(0);
                    if (dev.distSqr(worldPosition) > BIND_RANGE * BIND_RANGE
                            || !ConsoleFunctions.choices(p.type, part, ItemConsoleKit.kind(player.getMainHandItem(), level, dev)).contains(fn)) {
                        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.ntm_air_defense.console_kit.wrong_device"));
                        return;
                    }
                    p.fn.put(part, fn);
                    p.links.put(part, dev);
                    p.group.remove(part);
                    List<BlockPos> rest = sel.subList(1, sel.size()).stream().filter(d -> launcherNear(player, d)).toList();  // a selected group
                    if (!rest.isEmpty()) p.group.put(part, new ArrayList<>(rest));
                }
                // a fresh function names its key unless the player wrote something
                String label = p.labels.get(part);
                if (!fn.isEmpty() && (label == null || label.startsWith("#"))) p.labels.put(part, "#" + fn);
                sound(SoundEvents.CHAIN_PLACE, 1.6f);
                poll();
            }
            case "designate" -> {  // the target picked on the radar screen, for one launcher ("pos") or all of this console
                if (locked()) return;
                int id = data.getIntOr("id", -1);
                long only = data.getLongOr("pos", Long.MIN_VALUE);
                net.minecraft.world.entity.Entity e = id < 0 || level == null ? null : level.getEntity(id);
                for (Placed q : elements) {
                    for (BlockPos dev : q.links.values()) {
                        if (only != Long.MIN_VALUE && dev.asLong() != only) continue;
                        if (com.hbm.util.ChunkUtil.blockEntityIfLoaded(BlockEntitySAMBase.class, level, dev) instanceof BlockEntitySAMBase sam) sam.designate(e);
                    }
                }
                return;
            }
            case "watch" -> {  // the radar interface is open (sent every second)
                if (level != null) watchedUntil = level.getGameTime() + 40;
                return;
            }
            case "tint" -> {
                DyeColor dye = player.getMainHandItem().get(DataComponents.DYE);
                if (p == null || dye == null) return;
                p.tint = 0xFF000000 | dye.getTextureDiffuseColor();
                sound(SoundEvents.CHAIN_PLACE, 1.6f);
            }
            default -> {
                return;
            }
        }
        changed();
    }

    /** A launcher there, loaded and within binding range. */
    private boolean launcherNear(Player player, BlockPos dev) {
        String k = ItemConsoleKit.kind(player.getMainHandItem(), level, dev);
        return dev.distSqr(worldPosition) <= BIND_RANGE * BIND_RANGE && !k.equals("lost") && !k.equals("radar");
    }

    private void press(Placed p, String part, boolean back, Player player) {
        String b = ConsoleCatalog.base(part);
        String fn = p.fn.get(b);
        if (fn != null && ConsoleFunctions.display(p.type, fn)) return;  // an assigned lamp only shows
        if ("ack".equals(fn)) ack();
        switch (ConsoleCatalog.kind(p.type, b)) {
            case PUSH -> {  // a counter: every change replays the press on the clients
                p.state.put(b, (p.state(b) + 1) % 1000);
                sound(SoundEvents.STONE_BUTTON_CLICK_ON, 1.4f);
            }
            case TOGGLE -> {
                if (p.type.equals("toggle_covered") && b.equals("lever") && p.state("cover") == 0) return;
                int on = p.state(b) == 0 ? 1 : 0;
                p.state.put(b, on);
                if (b.equals("cover") && on == 0) {  // a closing guard knocks the switch back
                    p.state.put("lever", 0);
                    String lf = p.fn.get("lever");
                    if (lf != null && level != null) for (BlockPos dev : p.devices("lever")) ConsoleFunctions.apply(level, dev, lf, 0);
                }
                sound(SoundEvents.LEVER_CLICK, on == 1 ? 0.75f : 0.6f);
            }
            case ROTARY -> {
                int n = ConsoleCatalog.positions(p.type), s = p.state(b) + (back ? -1 : 1);
                if (p.type.equals("portrait")) s = Math.floorMod(s, n);  // leaders go round
                if (s < 0 || s >= n) return;
                p.state.put(b, s);
                sound(SoundEvents.LEVER_CLICK, 1.3f);
            }
            case NONE -> {}
        }
        if (fn != null && level != null && p.links.containsKey(b) && !ConsoleFunctions.consoleOnly(fn)) {
            int v = ConsoleCatalog.kind(p.type, b) == ConsoleCatalog.Kind.PUSH ? 1 : p.state(b);
            String refused = null;
            for (BlockPos dev : p.devices(b)) {
                String r = ConsoleFunctions.apply(level, dev, fn, v);
                if (r != null && refused == null) refused = r;
            }
            if (refused != null && fn.equals("fire")) {  // say why nothing went: the operator can't see it from here
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.ntm_air_defense.fire." + refused));
                sound(SoundEvents.DISPENSER_FAIL, 1.2f);
            }
            poll();
        }
    }

    private void sound(SoundEvent e, float pitch) {
        if (level != null) level.playSound(null, worldPosition, e, SoundSource.BLOCKS, 0.4f, pitch);
    }

    private void changed() {
        setChanged();
        if (level == null) return;
        boolean lit = elements.stream().anyMatch(p -> p.type.equals("desk_lamp") && p.state("switch") == 1);
        BlockState s = getBlockState();
        if (s.hasProperty(BlockConsole.LIT) && s.getValue(BlockConsole.LIT) != lit) {
            level.setBlock(worldPosition, s = s.setValue(BlockConsole.LIT, lit), 3);
        }
        level.sendBlockUpdated(worldPosition, s, s, 3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        elements.clear();
        input.read("elements", Placed.CODEC.listOf()).ifPresent(elements::addAll);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        access.clear();
        input.read("access", ACCESS).ifPresent(m -> m.forEach((k, v) -> access.put(UUID.fromString(k), v)));
        alarm = input.getIntOr("alarm", 0);
        quakes.clear();
        input.read("quakes", Seismic.Quake.CODEC.listOf()).ifPresent(quakes::addAll);
        input.read("feeds", FEEDS).ifPresent(f -> {  // only in updates from the server
            feeds.clear();
            feeds.putAll(f);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("elements", Placed.CODEC.listOf(), elements);
        output.storeNullable("owner", UUIDUtil.CODEC, owner);
        Map<String, String> acc = new HashMap<>();
        access.forEach((k, v) -> acc.put(k.toString(), v));
        output.store("access", ACCESS, acc);
        output.putInt("alarm", alarm);
        output.store("quakes", Seismic.Quake.CODEC.listOf(), quakes);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        FEEDS.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, feeds).ifSuccess(t -> tag.put("feeds", t));
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
