package com.hbm.airdefense;

import com.hbm.api.environment.Environment;
import com.hbm.client.ClientEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Client: the same path tracing NTM's ballistic missiles leave - exhaust plume every block of the
 * segment flown this tick, lingering for a few seconds and thinning with altitude. During the Tor's
 * cold catapult phase there is no motor yet, just the ejection gas puff.
 */
final class SamTrail {

    private SamTrail() {}

    static void tick(EntitySAM sam, Vec3 step) {
        double len = step.length();
        if (len < 1e-3) return;
        Vec3 back = step.scale(-1 / len);

        if (!sam.motorOn()) {
            if (sam.tickCount <= 2)
                ClientEffects.spawnContrail(sam.level(), sam.getX(), sam.getY(), sam.getZ(), ClientEffects.Contrail.HYDROGEN);
            return;
        }

        float scale = sam.kind() == EntitySAM.Kind.BUK ? 0.6f : 0.4f;
        double pressure = Environment.particlePressure(sam.level());
        int count = (int) Math.max(Math.min(len, 16), 1);
        for (int i = 0; i < count; i++) {
            double d = len * i / count;
            ClientEffects.spawnRocketFlameInAir(sam.level(),
                    sam.getX() + back.x * d, sam.getY() + back.y * d, sam.getZ() + back.z * d,
                    scale, back.x, back.y, back.z, 60 + sam.getRandom().nextInt(20), pressure);
        }
    }
}
