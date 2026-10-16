package com.hbm.airdefense;

import com.hbm.airdefense.client.ParticleSmoke;
import com.hbm.api.environment.Environment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client: the smoke a SAM leaves (the flame itself is RenderSAM's plume). A thick column of puffs that come
 * out lit orange by the motor and cool to grey as the missile pulls away, then swell and linger. Hot launches
 * kick up a billow around the launcher; the Tor's catapult throws the missile out in a puff of powder gas,
 * its gas jets spit while it tips over, and the motor lights with a burst.
 */
final class SamTrail {

    private SamTrail() {}

    static void tick(EntitySAM sam, Vec3 step) {
        Level level = sam.level();
        RandomSource r = sam.getRandom();
        EntitySAM.Kind k = sam.kind();
        double len = step.length();
        Vec3 fwd = len > 1e-3 ? step.scale(1 / len) : new Vec3(0, 1, 0);

        if (!sam.motorOn()) {
            if (k == EntitySAM.Kind.TOR) catapult(sam, level, r, fwd);
            return;
        }
        Vec3 back = fwd.scale(-1);
        if (k == EntitySAM.Kind.PANTSIR && sam.staged()) { // the dart coasts, no motor: only its tracer's thin wisp
            if (len < 1e-3) return;
            Vec3 tail = sam.position().add(back.scale(0.2));
            int n = (int) Math.min(Math.ceil(len / 1.5), 14);
            for (int i = 0; i < n; i++) {
                Vec3 p = tail.add(back.scale(len * (i + r.nextDouble()) / n));
                ParticleSmoke.spawn(level, p.x, p.y, p.z, r.nextGaussian() * 0.01, r.nextGaussian() * 0.01, r.nextGaussian() * 0.01,
                        0.25f, 1.2f + r.nextFloat() * 0.6f, 60 + r.nextInt(40), 0, 0.92f, 0.35f);
            }
            return;
        }
        float size = switch (k) {
            case BUK -> 1.0f;
            case TOR -> 0.7f;
            case A135 -> sam.staged() ? 0.8f : 1.8f;
            case PANTSIR -> 0.6f;
        };
        double nozzle = switch (k) {
            case BUK -> 2.8;
            case TOR -> 1.5;
            case A135 -> sam.staged() ? 0.55 : 3.3;
            case PANTSIR -> 1.6;
        };
        Vec3 tail = sam.position().add(back.scale(nozzle));

        if (sam.tickCount == 1 && k.cold == 0) groundBillow(level, r, tail, size);
        if (sam.burnTicks(0) <= 1) ignition(level, r, tail, back, size);
        if (len < 1e-3) return;

        // Thin air: the smoke spreads and goes faster (NTM's pressure, as its own exhaust uses).
        double pressure = Environment.particlePressure(level);
        double thin = Math.clamp(pressure * 1.5, 0.3, 1.0);
        double spacing = Math.max(0.5, size * 0.6);
        int n = (int) Math.min(Math.ceil(len / spacing), 20);
        for (int i = 0; i < n; i++) {
            double d = len * (i + r.nextDouble()) / n;
            Vec3 p = tail.add(back.scale(d));
            ParticleSmoke.spawn(level, p.x, p.y, p.z,
                    back.x * 0.25 + r.nextGaussian() * 0.03, back.y * 0.25 + r.nextGaussian() * 0.03, back.z * 0.25 + r.nextGaussian() * 0.03,
                    size * (0.8f + r.nextFloat() * 0.4f), size * (4f + r.nextFloat() * 2f) / (float) thin,
                    (int) ((180 + r.nextInt(120)) * thin), 8, 0.8f + r.nextFloat() * 0.12f, 0.75f);
        }
    }

    /** Tor: powder-gas cloud as the catapult throws it out, then the gas jets puffing sideways at the nose. */
    private static void catapult(EntitySAM sam, Level level, RandomSource r, Vec3 fwd) {
        EntitySAM.Kind k = sam.kind();
        if (sam.tickCount == 1) {
            Vec3 p = sam.position().add(0, -1.5, 0);
            for (int i = 0; i < 12; i++) {
                double a = r.nextDouble() * Math.PI * 2, s = 0.15 + r.nextDouble() * 0.25;
                ParticleSmoke.spawn(level, p.x, p.y, p.z, Math.cos(a) * s, 0.05 + r.nextDouble() * 0.2, Math.sin(a) * s,
                        0.8f, 3.5f + r.nextFloat() * 1.5f, 100 + r.nextInt(40), 2, 0.88f, 0.8f);
            }
        }
        if (sam.tickCount > k.cold - k.cold / 3) {
            Vec3 nose = sam.position().add(fwd.scale(1.3));
            Vec3 side = fwd.cross(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()));
            if (side.lengthSqr() < 1e-6) return;
            side = side.normalize().scale(0.35);
            for (int i = 0; i < 2; i++)
                ParticleSmoke.spawn(level, nose.x, nose.y, nose.z, side.x, side.y, side.z,
                        0.25f, 1.6f, 30 + r.nextInt(15), 0, 0.95f, 0.6f);
        }
    }

    /** First motor tick: a burst of flame-lit smoke at the nozzle. */
    private static void ignition(Level level, RandomSource r, Vec3 at, Vec3 back, float size) {
        for (int i = 0; i < 16; i++)
            ParticleSmoke.spawn(level, at.x, at.y, at.z,
                    back.x * 0.35 + r.nextGaussian() * 0.15, back.y * 0.35 + r.nextGaussian() * 0.15, back.z * 0.35 + r.nextGaussian() * 0.15,
                    size, size * (5f + r.nextFloat() * 2f), 140 + r.nextInt(60), 6, 0.8f, 0.8f);
    }

    /** Hot launch: the blast spreads over the ground around the launcher. */
    private static void groundBillow(Level level, RandomSource r, Vec3 at, float size) {
        for (int i = 0; i < 18; i++) {
            double a = r.nextDouble() * Math.PI * 2, s = 0.3 + r.nextDouble() * 0.4;
            ParticleSmoke.spawn(level, at.x, at.y - 1, at.z, Math.cos(a) * s, 0.02 + r.nextDouble() * 0.08, Math.sin(a) * s,
                    size * 1.4f, size * (6f + r.nextFloat() * 3f), 200 + r.nextInt(100), 5, 0.82f, 0.85f);
        }
    }
}
