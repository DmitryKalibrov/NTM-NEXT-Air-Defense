package com.hbm.airdefense;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.airdefense.mixin.LevelAccessor;
import com.hbm.sound.ModSounds;
import com.hbm.util.ChunkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Guided surface-to-air missile. Lead pursuit with a turn-rate limit, proximity fuse checked along
 * the whole segment flown each tick (it moves several blocks per tick). Server-only state; the
 * client just renders it and lays the trail.
 */
public class EntitySAM extends Entity {

    /** Direct holder: no registry entry needed, sounds.json is enough. */
    private static final Holder<SoundEvent> INTERCEPT = Holder.direct(SoundEvent.createVariableRangeEvent(AirDefense.id("sam.intercept_near")));

    public enum Kind {
        //    max speed, accel, turn deg/tick, life, fuse, damage, cold-launch ticks, vertical boost ticks, blast,
        //    staging tick (0 = single stage), stage centre behind the missile centre, miss chance
        // Must clearly outrun NTM missiles (~4-8 blocks/tick cruising) or it only catches them on descent.
        BUK(14.0, 1.0, 8, 300, 7.0, 60f, 0, 0, 4f, 0, 0, 0.15),
        TOR(12.0, 1.2, 22, 220, 5.0, 35f, 5, 0, 3f, 0, 0, 0.15),
        // 53T6: hot launch straight out of the silo, ~100 g, then pitches over onto a target 3+ km out.
        A135(18.0, 1.5, 8, 420, 10.0, 100f, 0, 8, 6f, 30, 1.925, 0.1),
        // 57E6: hot launch from the tube, the booster drops after half a second, the dart flies on.
        PANTSIR(20.0, 3.0, 14, 170, 4.5, 40f, 0, 0, 3f, 10, 0.9, 0.2);

        final double maxSpeed, accel, turn, stageBack;
        final int life, cold, boost, staging;
        final double fuse, miss;
        final float damage, blast;

        Kind(double maxSpeed, double accel, double turnDeg, int life, double fuse, float damage, int cold, int boost, float blast,
             int staging, double stageBack, double miss) {
            this.maxSpeed = maxSpeed;
            this.accel = accel;
            this.turn = Math.toRadians(turnDeg);
            this.life = life;
            this.fuse = fuse;
            this.damage = damage;
            this.cold = cold;
            this.boost = boost;
            this.blast = blast;
            this.staging = staging;
            this.stageBack = stageBack;
            this.miss = miss;
        }

        public double stageBack() {
            return stageBack;
        }
    }

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(EntitySAM.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> STAGED = SynchedEntityData.defineId(EntitySAM.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BURNT = SynchedEntityData.defineId(EntitySAM.class, EntityDataSerializers.BOOLEAN);
    /** How long a missile that lost guidance may tumble around before it is dropped. */
    private static final int LOST_LIFE = 600;

    /** Client: glide between server positions like NTM missiles do, instead of snapping each packet. */
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private @Nullable Entity target;
    private @Nullable Vec3 lastTargetPos;
    /** Target motion last tick, blocks/tick. */
    private Vec3 targetVel = Vec3.ZERO;
    private double speed, lastDist = Double.MAX_VALUE;
    private int receding, waiting;
    /**
     * A miss, rolled at launch (Kind.miss): after a few guided ticks the missile stops chasing and
     * flies to a lead point it worked out then, offset by a few fuse radii, and bursts there.
     */
    private boolean miss;
    private int guided;
    private @Nullable Vec3 missPoint;
    /**
     * Lost guidance (half the misses, and every missile whose motor runs out): the body spins about an
     * axis that wanders, the flight path bends after the nose, the motor burns on for a while and then
     * it noses over under gravity - corkscrews, loops and wobbling dives instead of a straight drop.
     */
    private boolean lost;
    private int burn;
    private double spin;
    private Vec3 heading = new Vec3(0, 1, 0), spinAxis = new Vec3(1, 0, 0);
    /** Game time of the last server tick: a launcher drops a missile that stopped ticking. */
    long lastTicked;

    public EntitySAM(EntityType<? extends EntitySAM> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static EntitySAM fire(Level level, Kind kind, Vec3 pos, Vec3 dir, Entity target) {
        EntitySAM sam = new EntitySAM(AirDefense.SAM.get(), level);
        sam.entityData.set(KIND, (byte) kind.ordinal());
        sam.setPos(pos);
        sam.speed = switch (kind) { case TOR -> 1.0; case PANTSIR -> 4.0; default -> 1.5; };
        sam.setDeltaMovement(dir.scale(sam.speed));
        sam.face(dir);
        sam.target = target;
        sam.miss = level.getRandom().nextDouble() < kind.miss;
        sam.lastTicked = level.getGameTime();
        level.addFreshEntity(sam);
        float pitch = switch (kind) { case TOR -> 1.3f; case BUK -> 0.9f; case A135 -> 0.6f; case PANTSIR -> 1.5f; };
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.WEAPON_ROCKET_FLAME.get(), SoundSource.BLOCKS, 10f, pitch);
        return sam;
    }


    public Kind kind() {
        return Kind.values()[entityData.get(KIND)];
    }

    /** Motor burning (not during the Tor's cold catapult phase). */
    public boolean motorOn() {
        return tickCount > kind().cold && !entityData.get(BURNT);
    }

    /** Motor section already dropped. */
    public boolean staged() {
        return entityData.get(STAGED);
    }

    private void stage(Vec3 vel) {
        entityData.set(STAGED, true);
        Vec3 dir = vel.normalize();
        EntitySpentStage.drop(level(), kind(), position().subtract(dir.scale(kind().stageBack)), vel.scale(0.5), getYRot(), getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) 0);
        builder.define(STAGED, false);
        builder.define(BURNT, false);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public void tick() {
        Vec3 prePos = position();
        interpolation.interpolate();
        super.tick();
        if (level().isClientSide()) {
            SamTrail.tick(this, position().subtract(prePos));
            return;
        }
        Kind k = kind();
        if (lost && tickCount > k.life + LOST_LIFE) {
            discard();
            return;
        }
        if (!lost && tickCount > k.life) loseGuidance(0); // out of fuel: falls, no more self-destruct in mid-air
        lastTicked = level().getGameTime();
        ChunkUtil.holdOwnChunk(this);
        if (target != null && !target.isAlive()) target = null;

        Vec3 vel = getDeltaMovement();
        if (tickCount <= k.cold) {
            vel = new Vec3(0, Math.max(0.2, 1.0 - tickCount * 0.1), 0);  // catapult: up, slowing
        } else if (tickCount <= k.boost) {
            speed = Math.min(speed + k.accel, k.maxSpeed);  // out of the silo: straight up, full thrust
            vel = new Vec3(0, speed, 0);
        } else if (lost) {
            vel = tumble(vel, k);
        } else {
            Vec3 dir = vel.normalize();
            double cap = k.maxSpeed;
            if (missPoint != null) {
                dir = steer(dir, missPoint.subtract(position()).normalize(), k.turn);
            } else if (target != null) {
                Vec3 aim = intercept();
                if (miss && ++guided >= 5) { // a few ticks in: the target's motion is known, the lead is plausible
                    var r = level().getRandom();
                    if (r.nextBoolean()) {
                        missPoint = aim.add(randomUnit().scale(k.fuse * (2 + 2 * r.nextDouble())));
                    } else {
                        loseGuidance(10 + r.nextInt(40)); // this step still flies straight on, tumbling from the next
                        aim = position().add(dir);
                    }
                }
                double dist = center(target).distanceTo(position());
                // Close in no faster than the turn rate allows (radius ~ speed / turn), but always
                // faster than the target: a slow missile can't make the turn onto a near target.
                cap = Math.clamp(Math.max(dist / 8, targetVel.length() * 1.5 + 1), 2.5, k.maxSpeed);
                // Flew past and opening: self-destruct so the launcher can send the next one.
                receding = dist > lastDist && dist < 80 ? receding + 1 : 0;
                lastDist = dist;
                if (receding >= 6) {
                    detonate(position());
                    return;
                }
                dir = steer(dir, aim.subtract(position()).normalize(), k.turn);
            }
            speed = Math.min(speed + k.accel, cap);
            vel = dir.scale(speed);
        }

        if (k.staging > 0 && tickCount >= k.staging && !staged()) stage(vel);

        // At up to 20 blocks a tick the next chunk may not tick entities yet: wait for it, give up after 2 s.
        if (!holdAhead(this, vel)) {
            if (++waiting > 40) detonate(position());
            return;
        }
        waiting = 0;

        Vec3 from = position(), to = from.add(vel);
        if (missPoint != null) {
            Vec3 closest = closestOnSegment(from, to, missPoint);
            if (closest.distanceTo(missPoint) <= k.fuse) {
                detonate(closest);
                return;
            }
        } else if (target != null) {
            Vec3 tp = center(target);
            Vec3 closest = closestOnSegment(from, to, tp);
            if (closest.distanceTo(tp) <= k.fuse) {
                detonate(closest);
                return;
            }
        }
        // Not while still climbing out of the silo: the shaft's own blocks are around it.
        if (tickCount > k.boost) {
            BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                detonate(hit.getLocation());
                return;
            }
        }
        setPos(to);
        setDeltaMovement(vel);
        face(lost ? heading : vel);
    }

    /** Stops guiding; burnTicks of motor left. Launchers stop counting it against their salvo. */
    private void loseGuidance(int burnTicks) {
        var r = level().getRandom();
        lost = true;
        burn = burnTicks;
        if (burn <= 0) entityData.set(BURNT, true);
        Vec3 v = getDeltaMovement();
        heading = v.lengthSqr() > 1e-6 ? v.normalize() : new Vec3(0, 1, 0);
        spinAxis = randomUnit();
        spin = Math.toRadians(3 + 12 * r.nextDouble());
    }

    private Vec3 tumble(Vec3 vel, Kind k) {
        var r = level().getRandom();
        target = null; // dropped here, not in loseGuidance: the guided step that called it still reads it
        missPoint = null;
        if (burn > 0 && --burn == 0) entityData.set(BURNT, true);
        spinAxis = spinAxis.add(r.nextGaussian() * 0.15, r.nextGaussian() * 0.15, r.nextGaussian() * 0.15).normalize();
        heading = rotate(heading, spinAxis, spin);
        if (burn <= 0) {
            spin *= 0.985;                                                   // fins slowly settle the spin
            if (vel.lengthSqr() > 1e-6) heading = steer(heading, vel.normalize(), Math.toRadians(2)); // and weathervane it
        }
        double s = vel.length();
        vel = vel.add(heading.scale(s).subtract(vel).scale(0.12));          // the path bends after the nose
        vel = vel.scale(0.985).add(heading.scale(burn > 0 ? k.accel * 0.5 : 0)).add(0, -0.05, 0);
        return vel.length() > k.maxSpeed ? vel.normalize().scale(k.maxSpeed) : vel;
    }

    boolean lost() {
        return lost;
    }

    private Vec3 randomUnit() {
        var r = level().getRandom();
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
        return v.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : v.normalize();
    }

    /** v turned by angle a about the unit axis k (Rodrigues). */
    private static Vec3 rotate(Vec3 v, Vec3 k, double a) {
        double c = Math.cos(a), sn = Math.sin(a);
        return v.scale(c).add(k.cross(v).scale(sn)).add(k.scale(k.dot(v) * (1 - c)));
    }

    /**
     * Ticket the chunks the next three steps run through (a ticket only takes effect a tick later, and
     * only its centre chunk ticks entities); true once the chunk of the next step ticks entities.
     */
    static boolean holdAhead(Entity e, Vec3 vel) {
        ServerLevel server = (ServerLevel) e.level();
        for (int k = 0; k <= 3; k++) {
            Vec3 p = e.position().add(vel.scale(k));
            ChunkUtil.holdForEntity(server, new ChunkPos(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4), 2);
        }
        return server.isPositionEntityTicking(BlockPos.containing(e.position().add(vel)));
    }

    /** Lead point: where the target will be when we get there, from its last-tick motion. */
    private Vec3 intercept() {
        Vec3 tp = center(target);
        targetVel = lastTargetPos == null ? Vec3.ZERO : tp.subtract(lastTargetPos);
        lastTargetPos = tp;
        double tGo = tp.distanceTo(position()) / Math.max(speed, 2.5);
        return tp.add(targetVel.scale(Math.min(tGo, 60)));
    }

    private static Vec3 steer(Vec3 dir, Vec3 want, double maxTurn) {
        double angle = Math.acos(Math.clamp(dir.dot(want), -1, 1));
        if (angle <= maxTurn) return want;
        return dir.add(want.subtract(dir).scale(maxTurn / angle)).normalize();
    }

    private static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1e-9 ? 0 : Math.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return a.add(ab.scale(t));
    }

    private void detonate(Vec3 at) {
        Kind k = kind();
        ServerLevel server = (ServerLevel) level();
        // Airburst: entity damage and the boom, no terrain damage. Our own close-up boom (vanilla range, 64 blocks);
        // Distant Thunder, if installed, plays sam.intercept_mid / _far further out.
        server.explode(this, null, null, at.x, at.y, at.z, k.blast, false, Level.ExplosionInteraction.NONE,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, LevelAccessor.ntmad$blockParticles(), INTERCEPT);
        if (target != null && center(target).distanceTo(at) <= k.fuse * 1.5) {
            DamageSource src = damageSources().explosion(this, null);
            // NTM missiles have 50 health and blow up on their own when it runs out.
            target.hurtServer(server, src, target instanceof EntityMissileBaseNT ? 100f : k.damage);
        }
        discard();
    }

    private void face(Vec3 v) {
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, -v.z)));
        setXRot((float) Math.toDegrees(Math.atan2(v.y, h)));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
