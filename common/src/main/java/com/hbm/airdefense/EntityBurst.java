package com.hbm.airdefense;

import com.hbm.util.ChunkUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The look of an intercept, seen from any distance. Vanilla explosion packets reach 64 blocks and NTM's
 * smoke shock 250, so a far intercept used to show nothing; this entity is FarVisible (NTM 1.0.3+ tracks
 * and ticks it out to the LOD range) and draws the burst on the client: fireball, a lingering smoke
 * blot and, when a ballistic missile is killed, burning pieces of it falling away with smoke trails.
 */
public class EntityBurst extends Entity {

    private static final EntityDataAccessor<Boolean> KILL = SynchedEntityData.defineId(EntityBurst.class, EntityDataSerializers.BOOLEAN);
    /** Server life: the client draws the falling pieces only while the entity exists. */
    private static final int LIFE = 25, KILL_LIFE = 120;

    /** Client: falling fragments, moved by BurstFx, drawn by RenderBurst. */
    public final List<Piece> pieces = new ArrayList<>();

    /** A burning fragment: flies, keeps its positions of the last ticks for the trail, cools down. */
    public static final class Piece {
        public static final int TRAIL = 12;
        public double x, y, z, vx, vy, vz;
        /** Positions of the previous ticks, newest first. */
        public final double[] trail = new double[TRAIL * 3];
        public int age;
        public final int burnout;
        public final float size, seed;

        Piece(double x, double y, double z, double vx, double vy, double vz, float size, int burnout, float seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.size = size;
            this.burnout = burnout;
            this.seed = seed;
            for (int i = 0; i < TRAIL; i++) {
                trail[i * 3] = x;
                trail[i * 3 + 1] = y;
                trail[i * 3 + 2] = z;
            }
        }

        /** 1 white-hot at the break-up .. 0 burnt out. */
        public float heat() {
            return Math.max(0f, 1f - (float) age / burnout);
        }
    }

    public EntityBurst(EntityType<? extends EntityBurst> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** kill: a ballistic missile came apart here, moving at vel (blocks/tick). */
    static void spawn(Level level, Vec3 at, boolean kill, Vec3 vel) {
        EntityBurst b = new EntityBurst(AirDefense.BURST.get(), level);
        b.entityData.set(KILL, kill);
        b.setPos(at);
        b.setDeltaMovement(vel);
        level.addFreshEntity(b);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (tickCount > (entityData.get(KILL) ? KILL_LIFE : LIFE)) discard();
            else ChunkUtil.holdOwnChunk(this);
            return;
        }
        if (tickCount == 1) BurstFx.burst(this, entityData.get(KILL));
        BurstFx.fly(this);
    }

    /** A ballistic missile came apart here (bigger fireball, falling pieces). */
    public boolean kill() {
        return entityData.get(KILL);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KILL, false);
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
