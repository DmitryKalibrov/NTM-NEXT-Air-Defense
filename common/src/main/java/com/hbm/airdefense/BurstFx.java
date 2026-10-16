package com.hbm.airdefense;

import com.hbm.airdefense.client.ParticleSmoke;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Client: what an {@link EntityBurst} draws. */
final class BurstFx {

    private BurstFx() {}

    static void burst(EntityBurst e, boolean kill) {
        RandomSource r = e.getRandom();
        double x = e.getX(), y = e.getY(), z = e.getZ();
        // The fireball itself is RenderBurst's shader (no fog: fogged flame particles turn sky-blue far off at night).
        // Smoke blot that hangs in the sky like the contrail does.
        int puffs = kill ? 14 : 6;
        for (int i = 0; i < puffs; i++)
            smoke(e, x + r.nextGaussian() * 2, y + r.nextGaussian() * 2, z + r.nextGaussian() * 2,
                    r.nextGaussian() * 0.3, r.nextGaussian() * 0.15, r.nextGaussian() * 0.3, kill ? 14f : 9f, 25 * 20, 6);
        if (!kill) return;
        // The missile keeps some of its speed; the pieces fan out from it.
        Vec3 v = e.getDeltaMovement().scale(0.4);
        int n = 6 + r.nextInt(4);
        for (int i = 0; i < n; i++)
            e.pieces.add(new EntityBurst.Piece(x, y, z, v.x + r.nextGaussian() * 1.2, v.y + r.nextDouble() * 1.2, v.z + r.nextGaussian() * 1.2,
                    0.6f + r.nextFloat() * 0.8f, 50 + r.nextInt(50), r.nextFloat()));
    }

    /**
     * Falling fragments: the glow is RenderBurst's shader; here they fly, leave smoke and now and then shed
     * a smaller burning piece that tumbles off on its own.
     */
    static void fly(EntityBurst e) {
        if (e.pieces.isEmpty()) return;
        RandomSource r = e.getRandom();
        List<EntityBurst.Piece> shed = new ArrayList<>();
        for (EntityBurst.Piece p : e.pieces) {
            System.arraycopy(p.trail, 0, p.trail, 3, p.trail.length - 3);
            p.trail[0] = p.x;
            p.trail[1] = p.y;
            p.trail[2] = p.z;
            p.vx *= 0.97;
            p.vy = p.vy * 0.97 - 0.08;
            p.vz *= 0.97;
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.age++;
            float heat = p.heat();
            if (p.age < p.burnout + 30) {
                // along the whole step, puffs closer than their own size: one ragged column, not a string of beads
                float scale = 1.5f + 2.5f * p.size * (0.4f + heat);
                double len = Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                int n = (int) Math.min(Math.ceil(len / (scale * 0.2f)), 8);
                for (int i = 0; i < n; i++) {
                    double f = (i + r.nextDouble()) / n;
                    smoke(e, p.trail[0] + p.vx * f, p.trail[1] + p.vy * f, p.trail[2] + p.vz * f,
                            r.nextGaussian() * 0.02, r.nextGaussian() * 0.02, r.nextGaussian() * 0.02,
                            scale * (0.7f + r.nextFloat() * 0.6f), 160 + r.nextInt(80), heat > 0 ? 3 : 0);
                }
            }
            if (heat > 0.3f && p.size > 0.4f && r.nextInt(80) == 0)
                shed.add(new EntityBurst.Piece(p.x, p.y, p.z, p.vx + r.nextGaussian() * 0.5, p.vy + r.nextGaussian() * 0.5,
                        p.vz + r.nextGaussian() * 0.5, p.size * 0.5f, (int) (p.burnout * heat * 0.6f) + 10, r.nextFloat()));
        }
        e.pieces.addAll(shed);
    }

    /** Dark explosive / burning-fuel smoke; scale is the final half-size, warm the flame-lit ticks. */
    private static void smoke(EntityBurst e, double x, double y, double z, double mx, double my, double mz, float scale, int life, int warm) {
        RandomSource r = e.getRandom();
        ParticleSmoke.spawn(e.level(), x, y, z, mx, my, mz, scale * 0.3f, scale * (0.8f + r.nextFloat() * 0.4f), life, warm,
                0.42f + r.nextFloat() * 0.12f, 0.85f);
    }
}
