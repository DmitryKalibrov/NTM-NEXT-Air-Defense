package com.hbm.airdefense;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.util.ChunkUtil;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Debug tool. Shift + use opens its settings ({@link com.hbm.airdefense.client.ScreenDebugTool}), use runs the picked
 * test at the picked distance: a practice strike by any NTM missile onto the block you look at (no warhead, no
 * blast when shot down), a blast far ahead (no terrain damage) for echo checks, gunfire far ahead, or an air defence
 * engagement far ahead. Also holds the world switch that makes every SAM launch a dud ({@link EntitySAM#DUD}).
 */
public class ItemTargetCaller extends Item implements com.hbm.items.IItemControlReceiver {

    /** Entity tag of practice missiles (see PracticeMissileMixin). */
    public static final String PRACTICE = "ntm_air_defense.practice";
    public static final int STRIKE = 0, BLAST = 1, GUNFIRE = 2, AIR_DEFENCE = 3, MODES = 4;
    public static final int[] DISTANCES = {50, 100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000};
    /** NTM missiles that fly on their own (not the custom missile or the shuttle). */
    public static final List<String> MISSILES = List.of("missile_generic", "missile_incendiary", "missile_cluster", "missile_buster",
            "missile_decoy", "missile_strong", "missile_incendiary_strong", "missile_cluster_strong", "missile_buster_strong",
            "missile_emp_strong", "missile_burst", "missile_inferno", "missile_rain", "missile_drill", "missile_nuclear",
            "missile_nuclear_cluster", "missile_volcano", "missile_stealth", "missile_test", "missile_micro", "missile_schrabidium",
            "missile_bhole", "missile_taint", "missile_emp");
    public static final float[] BLASTS = {2, 4, 8, 16};
    /** Gunfire: NTM sound, shots per burst, ticks between shots. */
    public record Weapon(String key, String sound, int shots, int gap) {}
    public static final List<Weapon> WEAPONS = List.of(
            new Weapon("pistol", "weapon.fire.pistol", 3, 6),
            new Weapon("rifle", "weapon.fire.assault", 10, 2),
            new Weapon("shotgun", "weapon.fire.shotgun", 2, 16),
            new Weapon("sniper", "weapon.fire.amat", 1, 1),
            new Weapon("cannon", "weapon.fire.mk108", 6, 4),
            new Weapon("artillery", "turret.jeremy_fire", 1, 1),
            new Weapon("ciws", "turret.chekhov_fire", 12, 1));
    private static final double REACH = 400;
    private static final Identifier WORLD = AirDefense.id("debug");

    public ItemTargetCaller(Properties props) {
        super(props);
    }

    /** The tool's settings: mode, dist, missile, blast, weapon, sam (indices), duds (last known world switch). */
    public static CompoundTag settings(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static int pick(CompoundTag s, String key, int size, int def) {
        return Math.clamp(s.getIntOr(key, def), 0, size - 1);
    }

    /** World switch: every SAM launch fails. */
    public static boolean allDuds(ServerLevel level) {
        return level.getServer().getCommandStorage().get(WORLD).getBooleanOr("duds", false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            if (player.isShiftKeyDown()) com.hbm.airdefense.client.ScreenDebugTool.open(settings(stack));
            return InteractionResult.SUCCESS;
        }
        boolean duds = allDuds(server);
        if (settings(stack).getBooleanOr("duds", false) != duds) CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean("duds", duds));
        if (player.isShiftKeyDown()) return InteractionResult.SUCCESS;

        CompoundTag s = settings(stack);
        int dist = DISTANCES[pick(s, "dist", DISTANCES.length, 4)];
        boolean done = switch (pick(s, "mode", MODES, STRIKE)) {
            case BLAST -> blast(server, player, dist, BLASTS[pick(s, "blast", BLASTS.length, 1)]);
            case GUNFIRE -> emitter(server, player, dist, EntityDebugEmitter.FIRE, pick(s, "weapon", WEAPONS.size(), 0));
            case AIR_DEFENCE -> emitter(server, player, dist, EntityDebugEmitter.SAM, pick(s, "sam", EntitySAM.Kind.values().length, 1));
            default -> strike(server, player, dist, MISSILES.get(pick(s, "missile", MISSILES.size(), 0)));
        };
        if (!done) return InteractionResult.FAIL;
        player.getCooldowns().addCooldown(stack, 20);
        return InteractionResult.SUCCESS;
    }

    /** Practice missile onto the block in sight, lifting off dist blocks away beyond the player. */
    private static boolean strike(ServerLevel server, Player player, int dist, String missile) {
        HitResult hit = player.pick(REACH, 0f, false);
        if (hit.getType() == HitResult.Type.MISS) {
            player.sendSystemMessage(Component.translatable("ntm_air_defense.caller.no_target"));
            return false;
        }
        Vec3 target = hit.getLocation();
        // Launch site: beyond the player, on the line from the mark through them, so the arc passes overhead.
        Vec3 away = new Vec3(player.getX() - target.x, 0, player.getZ() - target.z);
        if (away.length() < 30) {
            double a = server.getRandom().nextDouble() * Mth.TWO_PI;
            away = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        Vec3 start = player.position().add(away.normalize().scale(dist));
        int sx = Mth.floor(start.x), sz = Mth.floor(start.z);
        // A missile added to a chunk that doesn't tick entities yet just sits there; this waits until it does.
        ChunkUtil.loadTickingForEntity(server, new ChunkPos(sx >> 4, sz >> 4));
        int sy = server.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);

        Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("hbm", missile)).create(server, EntitySpawnReason.COMMAND);
        if (!(e instanceof EntityMissileBaseNT m)) return false;
        m.launch(sx + 0.5, sy + 2, sz + 0.5, Mth.floor(target.x), Mth.floor(target.z));
        m.isCluster = false;  // no submunitions
        m.addTag(PRACTICE);
        server.addFreshEntity(m);
        player.sendSystemMessage(Component.translatable("ntm_air_defense.caller.launched", m.getType().getDescription(), dist,
                compass(start.subtract(player.position())), Mth.floor(target.x), Mth.floor(target.y), Mth.floor(target.z)));
        return true;
    }

    /** dist blocks ahead along the player's look, on the ground. */
    private static Vec3 ahead(ServerLevel server, Player player, int dist) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        Vec3 p = player.position().add(flat.normalize().scale(dist));
        int x = Mth.floor(p.x), z = Mth.floor(p.z);
        return new Vec3(x + 0.5, server.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);  // loads the chunk
    }

    /** A warhead-like blast that only shakes and sounds: entity damage near it, no terrain damage. */
    private static boolean blast(ServerLevel server, Player player, int dist, float power) {
        Vec3 at = ahead(server, player, dist);
        // the source only names the blast for listeners such as Distant Thunder (com.hbm.* = a warhead)
        EntityBurst source = new EntityBurst(AirDefense.BURST.get(), server);
        source.setPos(at);
        server.explode(source, null, null, at.x, at.y + 1, at.z, power, false, Level.ExplosionInteraction.NONE);
        say(player, "blast", dist, at);
        return true;
    }

    private static boolean emitter(ServerLevel server, Player player, int dist, int mode, int option) {
        Vec3 at = ahead(server, player, dist);
        ChunkUtil.loadTickingForEntity(server, new ChunkPos(Mth.floor(at.x) >> 4, Mth.floor(at.z) >> 4));
        EntityDebugEmitter.spawn(server, at, mode, option, player.position());
        say(player, mode == EntityDebugEmitter.FIRE ? "gunfire" : "air_defence", dist, at);
        return true;
    }

    private static void say(Player player, String what, int dist, Vec3 at) {
        player.sendSystemMessage(Component.translatable("ntm_air_defense.caller." + what, dist,
                compass(at.subtract(player.position())), Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)));
    }

    /** From the settings screen: the new settings, or "toggle_duds" for the world switch. */
    @Override
    public void receiveControl(ServerPlayer player, ItemStack stack, CompoundTag data) {
        if (data.getBooleanOr("toggle_duds", false)) {
            if (!player.isCreative()) return;  // a world-wide switch: creative only
            ServerLevel server = player.level();
            CompoundTag w = server.getServer().getCommandStorage().get(WORLD);
            boolean on = !w.getBooleanOr("duds", false);
            w.putBoolean("duds", on);
            server.getServer().getCommandStorage().set(WORLD, w);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean("duds", on));
            player.sendSystemMessage(Component.translatable("ntm_air_defense.caller.duds_" + (on ? "on" : "off")));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.putInt("mode", pick(data, "mode", MODES, STRIKE));
            t.putInt("dist", pick(data, "dist", DISTANCES.length, 4));
            t.putInt("missile", pick(data, "missile", MISSILES.size(), 0));
            t.putInt("blast", pick(data, "blast", BLASTS.length, 1));
            t.putInt("weapon", pick(data, "weapon", WEAPONS.size(), 0));
            t.putInt("sam", pick(data, "sam", EntitySAM.Kind.values().length, 1));
        });
    }

    private static String compass(Vec3 d) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double deg = Math.toDegrees(Math.atan2(-d.x, d.z));
        return names[Math.floorMod((int) Math.round(deg / 45), 8)];
    }
}
