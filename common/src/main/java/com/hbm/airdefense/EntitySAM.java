package com.hbm.airdefense;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.airdefense.mixin.LevelAccessor;
import com.hbm.sound.ModSounds;
import com.hbm.util.ChunkUtil;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
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
        //    staging tick (0 = single stage), stage centre behind the missile centre, miss chance,
        //    lateral acceleration limit in blocks/tick^2 (0 = only the turn rate limits; 0.75 ~ 30 g), motor spool-up ticks
        // Must clearly outrun NTM missiles (~4-8 blocks/tick cruising) or it only catches them on descent.
        BUK(14.0, 1.0, 8, 300, 7.0, 60f, 0, 0, 4f, 0, 0, 0.02, 0, 0),
        // 9M338: thrown 10+ blocks straight up by the catapult, gas jets tip it at the target, then the motor lights.
        // After the catapult: the motor comes up to thrust over 6 ticks, ~20 g to full speed in about 1.2 s, and it
        // turns no harder than ~30 g allows, so the faster it flies the wider it turns.
        TOR(12.0, 0.5, 22, 220, 5.0, 35f, 18, 0, 3f, 0, 0, 0.02, 0.75, 6),
        // 53T6: hot launch straight out of the silo, ~100 g, then pitches over onto a target 3+ km out.
        A135(18.0, 1.5, 8, 420, 10.0, 100f, 0, 8, 6f, 30, 1.925, 0.02, 0, 0),
        // 57E6: hot launch from the tube, the booster drops after half a second, the dart flies on.
        PANTSIR(20.0, 3.0, 14, 170, 4.5, 40f, 0, 0, 3f, 10, 0.9, 0.02, 0, 0);

        final double maxSpeed, accel, turn, stageBack, lateral;
        final int life, cold, boost, staging, spool;
        final double fuse, miss;
        final float damage, blast;

        Kind(double maxSpeed, double accel, double turnDeg, int life, double fuse, float damage, int cold, int boost, float blast,
             int staging, double stageBack, double miss, double lateral, int spool) {
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
            this.lateral = lateral;
            this.spool = spool;
        }

        /** Turn rate allowed at this speed. */
        double turn(double speed) {
            return lateral > 0 ? Math.min(turn, lateral / Math.max(speed, 0.5)) : turn;
        }

        /** Speed gained this tick, ticks after the motor lit: spools up instead of kicking in at full thrust. */
        double accel(int sinceIgnition) {
            return spool > 0 ? accel * Math.clamp((sinceIgnition + 1) / (double) spool, 0, 1) : accel;
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
    /** Who fired it (set by the launcher), the target it went for, and whether its outcome is in the {@link SamLog}. */
    @Nullable BlockPos launcher;
    private int targetId = -1;
    private boolean reported;
    /** The last guided ticks: own position and the target's, for the consoles' TV channel. */
    private final ArrayDeque<double[]> clip = new ArrayDeque<>();
    static final int CLIP = 50;
    /**
     * A failed launch, rolled at launch: NOSE = the motor dies (Tor: never lights) and it noses into the ground near
     * the launcher; FLAT (Tor only) = it tips over, the motor coughs three times and it falls flat. On the ground it
     * either pops (no terrain damage) or lies there as an {@link EntitySAMWreck} to be picked up.
     * The chance can be raised with -Dntm_air_defense.dud=1, or every launch made a dud with the debug tool's world switch.
     */
    static final double DUD = Double.parseDouble(System.getProperty("ntm_air_defense.dud", "0.0001"));
    private static final int NOSE = 1, FLAT = 2;
    private int dud;

    public EntitySAM(EntityType<? extends EntitySAM> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Missiles in the air (server side), for {@link #engaged}. ponytail: one global set, fine for one server. */
    private static final Set<EntitySAM> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    /** Some launcher already has a guided missile in the air at this target. */
    static boolean engaged(Entity target) {
        LIVE.removeIf(s -> !s.isAlive());
        for (EntitySAM s : LIVE) if (!s.lost && s.target == target) return true;
        return false;
    }

    public static EntitySAM fire(Level level, Kind kind, Vec3 pos, Vec3 dir, Entity target) {
        EntitySAM sam = new EntitySAM(AirDefense.SAM.get(), level);
        sam.entityData.set(KIND, (byte) kind.ordinal());
        sam.setPos(pos);
        sam.speed = switch (kind) { case TOR -> 1.0; case PANTSIR -> 4.0; default -> 1.5; };
        sam.setDeltaMovement(dir.scale(sam.speed));
        sam.face(dir);
        sam.target = target;
        sam.targetId = target.getId();
        sam.miss = level.getRandom().nextDouble() < kind.miss;
        if (level.getRandom().nextDouble() < DUD || level instanceof ServerLevel s && ItemTargetCaller.allDuds(s)) sam.dud = kind == Kind.TOR && level.getRandom().nextBoolean() ? FLAT : NOSE;
        sam.lastTicked = level.getGameTime();
        level.addFreshEntity(sam);
        LIVE.add(sam);
        if (kind.cold > 0) level.playSound(null, pos.x, pos.y, pos.z, AdSounds.TOR_EJECT, SoundSource.BLOCKS, 6f, 1f); // gas throw, caps, turn jets
        else sam.ignitionSound();
        return sam;
    }


    private void ignitionSound() {
        if (kind() == Kind.TOR) {
            level().playSound(null, getX(), getY(), getZ(), AdSounds.TOR_IGNITION, SoundSource.BLOCKS, 10f, 1f);
            return;
        }
        float pitch = switch (kind()) { case TOR -> 1.3f; case BUK -> 0.9f; case A135 -> 0.6f; case PANTSIR -> 1.5f; };
        level().playSound(null, getX(), getY(), getZ(), ModSounds.WEAPON_ROCKET_FLAME.get(), SoundSource.BLOCKS, 10f, pitch);
    }

    public Kind kind() {
        return Kind.values()[entityData.get(KIND)];
    }

    /** Motor burning (not during the Tor's cold catapult phase). */
    public boolean motorOn() {
        return tickCount > kind().cold && !entityData.get(BURNT);
    }

    /** Ticks since the motor lit (negative while still on the catapult). */
    public float burnTicks(float partialTicks) {
        return tickCount + partialTicks - kind().cold;
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
            if (kind() == Kind.TOR) SamSounds.flight(this);
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
        if (!lost && target != null && !target.isAlive()) { // shot down by someone else: self-destruct
            reported = true;
            detonate(position());
            return;
        }

        Vec3 vel = getDeltaMovement();
        if (dud > 0 && tickCount > failTick()) {
            vel = dudFlight(vel);
        } else if (tickCount <= k.cold) {
            vel = catapult(vel, k);
        } else if (k.cold > 0 && tickCount == k.cold + 1) {
            ignitionSound();
            speed = 0.5;
            vel = vel.normalize().scale(speed);
        } else if (tickCount <= k.boost) {
            speed = Math.min(speed + k.accel, k.maxSpeed);  // out of the silo: straight up, full thrust
            vel = new Vec3(0, speed, 0);
        } else if (lost) {
            vel = tumble(vel, k);
        } else {
            Vec3 dir = vel.normalize();
            double cap = k.maxSpeed;
            if (missPoint != null) {
                dir = steer(dir, missPoint.subtract(position()).normalize(), k.turn(speed));
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
                dir = steer(dir, aim.subtract(position()).normalize(), k.turn(speed));
            }
            speed = Math.min(speed + k.accel(tickCount - k.cold - 1), cap);
            vel = dir.scale(speed);
        }

        if (dud == 0 && k.staging > 0 && tickCount >= k.staging && !staged()) stage(vel);

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
                if (dud > 0) crash(hit);
                else detonate(hit.getLocation());
                return;
            }
        }
        setPos(to);
        setDeltaMovement(vel);
        face(lost || dud > 0 ? heading : vel);
        if (!lost && target != null) {
            Vec3 tc = center(target);
            clip.addLast(new double[]{to.x, to.y, to.z, tc.x, tc.y, tc.z});
            if (clip.size() > CLIP) clip.removeFirst();
        }
    }

    /** Into the log once: a hit (with the TV picture for the launcher) or a miss. */
    private void report(Vec3 at, boolean hit) {
        if (reported) return;
        reported = true;
        BlockPos from = launcher != null ? launcher : blockPosition();
        SamLog.add(level(), from, at, hit ? SamLog.HIT : SamLog.MISS, targetId);
        if (hit && launcher != null && ChunkUtil.blockEntityIfLoaded(BlockEntitySAMBase.class, level(), launcher) instanceof BlockEntitySAMBase sam) {
            sam.clip(List.copyOf(clip), at);
        }
    }

    /**
     * Cold launch: thrown up, slowing under gravity to a near stop at the top (2/3 of the phase), then the gas
     * jets tip the nose at the lead point while it hangs there.
     */
    private Vec3 catapult(Vec3 vel, Kind k) {
        int tilt = k.cold / 3;
        // ~11 blocks up over 12 ticks (tor sounds: tools gen_systems.py tor_mod are timed to this)
        if (tickCount <= k.cold - tilt) return new Vec3(0, Math.max(0.12, 1.6 - 0.119 * (tickCount - 1)), 0);
        Vec3 dir = vel.normalize();
        if (target != null) dir = steer(dir, intercept().subtract(position()).normalize(), Math.toRadians(20));
        return dir.scale(0.25);
    }

    /** Stops guiding; burnTicks of motor left. Launchers stop counting it against their salvo. */
    private void loseGuidance(int burnTicks) {
        var r = level().getRandom();
        lost = true;
        report(position(), false);
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

    /** Lost guidance or a failed launch: the launcher no longer counts it. */
    boolean lost() {
        return lost || dud > 0 && tickCount > failTick();
    }

    /** The last tick a dud flies normally: the Tor's catapult still works, the others' motors die a few ticks out. */
    private int failTick() {
        return switch (kind()) { case TOR -> kind().cold; case A135 -> kind().boost; default -> 5; };
    }

    /** Half the missile's length, centre to nozzle. */
    static double halfLength(Kind k) {
        return switch (k) { case BUK -> 2.8; case TOR -> 1.5; case A135 -> 3.3; case PANTSIR -> 1.6; };
    }

    private Vec3 dudFlight(Vec3 vel) {
        var r = level().getRandom();
        int t = tickCount - failTick();
        if (t == 1) {
            entityData.set(BURNT, true);
            report(position(), false);
            target = null;
            missPoint = null;
            heading = vel.lengthSqr() > 1e-6 ? vel.normalize() : new Vec3(0, 1, 0);
            double a = r.nextDouble() * Math.PI * 2;
            spinAxis = new Vec3(Math.cos(a), 0, Math.sin(a)); // the way it falls over
            vel = vel.add(spinAxis.scale(0.25 + 0.15 * r.nextDouble()));  // clear of the launcher
            level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 4f, 0.6f);
        }
        if (dud == FLAT) {
            heading = steer(heading, spinAxis.add(0, -0.08, 0).normalize(), Math.toRadians(8)); // tips over level
            boolean pulse = t >= 6 && t < 24 && (t - 6) % 6 < 3;                                   // three coughs
            if (pulse != !entityData.get(BURNT)) entityData.set(BURNT, !pulse);
            if (pulse) {
                vel = vel.add(heading.scale(0.12));
                if ((t - 6) % 6 == 0) level().playSound(null, getX(), getY(), getZ(), ModSounds.WEAPON_ROCKET_FLAME.get(), SoundSource.BLOCKS, 3f, 1.8f);
            }
        } else if (vel.lengthSqr() > 1e-6) {
            heading = steer(heading, vel.normalize(), Math.toRadians(6)); // the nose drops after the fall
        }
        vel = vel.scale(0.98).add(0, -0.08, 0);
        return vel.length() > 3 ? vel.normalize().scale(3) : vel;
    }

    /** A dud hits the ground: sometimes the warhead pops (people get hurt, the ground doesn't), else it digs in. */
    private void crash(BlockHitResult hit) {
        ServerLevel server = (ServerLevel) level();
        Vec3 at = hit.getLocation();
        if (server.getRandom().nextDouble() < 0.4) {
            server.explode(this, at.x, at.y, at.z, kind().blast * 0.7f, Level.ExplosionInteraction.NONE);
            EntityBurst.spawn(server, at, false, Vec3.ZERO);
        } else {
            server.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, hit.getBlockPos(),
                    net.minecraft.world.level.block.Block.getId(server.getBlockState(hit.getBlockPos())));
            server.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2f, 0.5f);
            EntitySAMWreck.place(server, kind(), at, heading);
        }
        discard();
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
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, LevelAccessor.ntmad$blockParticles(),
                k == Kind.TOR ? AdSounds.EXPLOSION_SMALL : INTERCEPT);
        boolean kill = false;
        if (target != null && center(target).distanceTo(at) <= k.fuse * 1.5) {
            DamageSource src = damageSources().explosion(this, null);
            // NTM missiles have 50 health and blow up on their own when it runs out.
            target.hurtServer(server, src, target instanceof EntityMissileBaseNT ? 100f : k.damage);
            kill = !target.isAlive();
            report(at, true);
        }
        report(at, false);
        EntityBurst.spawn(server, at, kill, targetVel);
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
