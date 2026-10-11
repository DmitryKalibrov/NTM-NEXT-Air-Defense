package com.hbm.airdefense;

import com.hbm.api.environment.Environment;
import com.hbm.client.ClientEffects;
import com.hbm.particle.RocketFlameColor;
import com.hbm.util.ChunkUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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

/**
 * A burnt-out motor section (53T6 motor, 57E6 booster): tumbles down on a ballistic arc trailing smoke that cools from
 * the motor's glow to thin grey, and blows up (residual propellant) where it lands.
 */
public class EntitySpentStage extends Entity {

    private static final double GRAVITY = 0.15, DRAG = 0.96;
    private static final int LIFE = 900, HOT = 30, TRAIL = 140;

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(EntitySpentStage.class, EntityDataSerializers.BYTE);

    private final InterpolationHandler interpolation = new InterpolationHandler(this);

    public EntitySpentStage(EntityType<? extends EntitySpentStage> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static void drop(Level level, EntitySAM.Kind kind, Vec3 pos, Vec3 vel, float yaw, float pitch) {
        EntitySpentStage stage = new EntitySpentStage(AirDefense.SPENT_STAGE.get(), level);
        stage.entityData.set(KIND, (byte) kind.ordinal());
        stage.setPos(pos);
        stage.setDeltaMovement(vel);
        stage.setYRot(yaw);
        stage.setXRot(pitch);
        level.addFreshEntity(stage);
    }

    public EntitySAM.Kind kind() {
        return EntitySAM.Kind.values()[entityData.get(KIND)];
    }

    /** The small Pantsir booster leaves less smoke and only a pop where it lands. */
    private boolean small() {
        return kind() == EntitySAM.Kind.PANTSIR;
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
            trail(position().subtract(prePos));
            return;
        }
        if (tickCount > LIFE) {
            discard();
            return;
        }
        ChunkUtil.holdOwnChunk(this);
        Vec3 vel = getDeltaMovement().scale(DRAG).add(0, -GRAVITY, 0);
        if (!EntitySAM.holdAhead(this, vel)) return;  // wait for the chunk ahead to load
        Vec3 from = position(), to = from.add(vel);
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 at = hit.getLocation();
            ((ServerLevel) level()).explode(this, at.x, at.y, at.z, small() ? 1.0f : 2.5f,
                    small() ? Level.ExplosionInteraction.NONE : Level.ExplosionInteraction.TNT);
            discard();
            return;
        }
        setPos(to);
        setDeltaMovement(vel);
    }

    /** Glowing exhaust right after burnout, cooling to grey smoke that thins out and stops. */
    private void trail(Vec3 step) {
        if (tickCount > TRAIL) return;
        float heat = Mth.clamp(1f - tickCount / (float) HOT, 0f, 1f);
        float grey = 0.45f;
        RocketFlameColor color = new RocketFlameColor(Mth.lerp(heat, grey, 1f), Mth.lerp(heat, grey, 0.6f),
                Mth.lerp(heat, grey, 0f), heat > 0, heat > 0, heat > 0, 240);
        float scale = (small() ? 0.4f : 0.9f) * (1f - tickCount / (float) TRAIL) + 0.1f;
        double pressure = Environment.particlePressure(level());
        double len = step.length();
        int count = (int) Mth.clamp(len, 1, 8);
        for (int i = 0; i < count; i++) {
            Vec3 p = position().subtract(step.scale(i / (double) count));
            ClientEffects.spawnRocketFlameInAir(level(), p.x, p.y, p.z, scale, 0, 0, 0,
                    60 + getRandom().nextInt(30), pressure, color);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) 0);
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
