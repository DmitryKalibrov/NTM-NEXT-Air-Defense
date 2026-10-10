package com.hbm.airdefense;

import com.hbm.client.ClientEffects;
import net.minecraft.world.phys.Vec3;

/** Client: smoke trail along the segment flown this tick, plus exhaust flame while the motor burns. */
final class SamTrail {

    private SamTrail() {}

    static void tick(EntitySAM sam) {
        if (!sam.motorOn()) return;
        Vec3 now = sam.position(), was = new Vec3(sam.xo, sam.yo, sam.zo);
        Vec3 step = now.subtract(was);
        double len = step.length();
        if (len < 1e-3) return;
        int n = (int) Math.min(Math.max(len / 1.5, 1), 6);
        for (int i = 1; i <= n; i++) {
            Vec3 p = was.add(step.scale((double) i / n));
            ClientEffects.spawnContrail(sam.level(), p.x, p.y, p.z, ClientEffects.Contrail.HYDROGEN);
        }
        Vec3 back = step.scale(-0.2 / len);
        ClientEffects.spawnRocketFlame(sam.level(), now.x, now.y, now.z,
                sam.kind() == EntitySAM.Kind.BUK ? 0.6f : 0.4f, back.x, back.y, back.z, 8);
    }
}
