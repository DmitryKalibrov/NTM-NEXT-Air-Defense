package com.hbm.airdefense;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.sound.ModSounds;
import com.hbm.util.ChunkUtil;
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

    public enum Kind {
        //    max speed, accel, turn deg/tick, life, fuse, damage, cold-launch ticks
        // Must clearly outrun NTM missiles (~4-8 blocks/tick cruising) or it only catches them on descent.
        BUK(14.0, 1.0, 8, 300, 7.0, 60f, 0),
        TOR(10.0, 0.9, 16, 150, 5.0, 35f, 8);

        final double maxSpeed, accel, turn;
        final int life, cold;
        final double fuse;
        final float damage;

        Kind(double maxSpeed, double accel, double turnDeg, int life, double fuse, float damage, int cold) {
            this.maxSpeed = maxSpeed;
            this.accel = accel;
            this.turn = Math.toRadians(turnDeg);
            this.life = life;
            this.fuse = fuse;
            this.damage = damage;
            this.cold = cold;
        }
    }

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(EntitySAM.class, EntityDataSerializers.BYTE);

    /** Client: glide between server positions like NTM missiles do, instead of snapping each packet. */
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private @Nullable Entity target;
    private @Nullable Vec3 lastTargetPos;
    /** Target motion last tick, blocks/tick. */
    private Vec3 targetVel = Vec3.ZERO;
    private double speed, lastDist = Double.MAX_VALUE;
    private int receding;

    public EntitySAM(EntityType<? extends EntitySAM> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static EntitySAM fire(Level level, Kind kind, Vec3 pos, Vec3 dir, Entity target) {
        EntitySAM sam = new EntitySAM(AirDefense.SAM.get(), level);
        sam.entityData.set(KIND, (byte) kind.ordinal());
        sam.setPos(pos);
        sam.speed = kind == Kind.TOR ? 1.0 : 1.5;
        sam.setDeltaMovement(dir.scale(sam.speed));
        sam.face(dir);
        sam.target = target;
        level.addFreshEntity(sam);
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.WEAPON_ROCKET_FLAME.get(), SoundSource.BLOCKS, 10f, kind == Kind.TOR ? 1.3f : 0.9f);
        return sam;
    }

    public Kind kind() {
        return Kind.values()[entityData.get(KIND)];
    }

    /** Motor burning (not during the Tor's cold catapult phase). */
    public boolean motorOn() {
        return tickCount > kind().cold;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) 0);
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
        if (tickCount > k.life) {
            detonate(position());
            return;
        }
        ChunkUtil.holdOwnChunk(this);
        if (target != null && !target.isAlive()) target = null;

        Vec3 vel = getDeltaMovement();
        if (tickCount <= k.cold) {
            vel = new Vec3(0, Math.max(0.2, 1.0 - tickCount * 0.1), 0);  // catapult: up, slowing
        } else {
            Vec3 dir = vel.normalize();
            double cap = k.maxSpeed;
            if (target != null) {
                Vec3 aim = intercept();
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

        Vec3 from = position(), to = from.add(vel);
        if (target != null) {
            Vec3 tp = center(target);
            Vec3 closest = closestOnSegment(from, to, tp);
            if (closest.distanceTo(tp) <= k.fuse) {
                detonate(closest);
                return;
            }
        }
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            detonate(hit.getLocation());
            return;
        }
        setPos(to);
        setDeltaMovement(vel);
        face(vel);
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
        // Airburst: entity damage and the boom, no terrain damage.
        server.explode(this, at.x, at.y, at.z, k == Kind.BUK ? 4f : 3f, Level.ExplosionInteraction.NONE);
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
