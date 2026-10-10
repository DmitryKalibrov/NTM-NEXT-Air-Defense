package com.hbm.airdefense;

import com.hbm.util.ChunkUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Debug: right-click to call a harmless practice V-2 onto the block you look at (up to 400 blocks).
 * It lifts off far away on the far side of you, so its arc passes over your head on the way in.
 */
public class ItemTargetCaller extends Item {

    private static final double REACH = 400, MIN_START = 600, EXTRA_START = 200;

    public ItemTargetCaller(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        HitResult hit = player.pick(REACH, 0f, false);
        if (hit.getType() == HitResult.Type.MISS) {
            player.sendSystemMessage(Component.translatable("ntm_air_defense.caller.no_target"));
            return InteractionResult.FAIL;
        }
        Vec3 target = hit.getLocation();

        // Launch site: beyond the player, on the line from the mark through them.
        Vec3 away = new Vec3(player.getX() - target.x, 0, player.getZ() - target.z);
        if (away.length() < 30) {
            double a = level.getRandom().nextDouble() * Mth.TWO_PI;
            away = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        double dist = MIN_START + level.getRandom().nextDouble() * EXTRA_START;
        Vec3 start = player.position().add(away.normalize().scale(dist));
        int sx = Mth.floor(start.x), sz = Mth.floor(start.z);

        // The site is outside the loaded area: keep its chunk ticking until the missile holds its own.
        ChunkUtil.holdForEntity(server, new ChunkPos(sx >> 4, sz >> 4), 2);
        int sy = server.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);

        EntityTargetDrone drone = new EntityTargetDrone(AirDefense.TARGET_DRONE.get(), server);
        drone.launch(sx + 0.5, sy + 2, sz + 0.5, Mth.floor(target.x), Mth.floor(target.z));
        server.addFreshEntity(drone);

        player.sendSystemMessage(Component.translatable("ntm_air_defense.caller.launched",
                (int) dist, compass(start.subtract(player.position())),
                Mth.floor(target.x), Mth.floor(target.y), Mth.floor(target.z)));
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
        return InteractionResult.SUCCESS;
    }

    private static String compass(Vec3 d) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double deg = Math.toDegrees(Math.atan2(-d.x, d.z));
        return names[Math.floorMod((int) Math.round(deg / 45), 8)];
    }
}
