package com.hbm.airdefense;

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
 * A gun tracer: pure visual, the hit is rolled by the gun. Flies straight at a fixed speed; the
 * client moves it from the spawn velocity on its own, the server only spawns and removes it.
 */
public class EntityTracer extends Entity {

    private int life = 20;

    public EntityTracer(EntityType<? extends EntityTracer> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static void fire(Level level, Vec3 from, Vec3 to, double speed) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-3) return;
        EntityTracer t = new EntityTracer(AirDefense.TRACER.get(), level);
        t.setPos(from);
        t.setDeltaMovement(d.scale(speed / len));
        t.life = (int) Math.ceil(len / speed) + 1;
        level.addFreshEntity(t);
    }

    @Override
    public void tick() {
        super.tick();
        setPos(position().add(getDeltaMovement()));
        if (!level().isClientSide() && tickCount > life) discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

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
