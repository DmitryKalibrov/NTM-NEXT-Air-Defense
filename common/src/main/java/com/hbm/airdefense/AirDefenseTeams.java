package com.hbm.airdefense;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Where a teams / IFF addon plugs in: register a {@link Provider} and the launchers spare friendly targets and
 * the consoles let team mates use them. With no provider nothing changes (only owner and access list).
 */
public final class AirDefenseTeams {

    public interface Provider {
        /** May `player` work a console owned by `owner`? */
        default boolean mayUse(@Nullable UUID owner, Player player) {
            return false;
        }

        /** Is `target` friendly to the air defence site at `site`? Friendly targets are never engaged. */
        default boolean friendly(Level level, BlockPos site, Entity target) {
            return false;
        }
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private AirDefenseTeams() {}

    public static void register(Provider p) {
        PROVIDERS.add(p);
    }

    static boolean mayUse(@Nullable UUID owner, Player player) {
        for (Provider p : PROVIDERS) if (p.mayUse(owner, player)) return true;
        return false;
    }

    static boolean friendly(Level level, BlockPos site, Entity target) {
        for (Provider p : PROVIDERS) if (p.friendly(level, site, target)) return true;
        return false;
    }
}
