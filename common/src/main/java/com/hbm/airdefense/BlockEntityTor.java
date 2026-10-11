package com.hbm.airdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tor: own search radar, cold vertical launch out of eight cells. The cell hatch opens first,
 * the missile pops out {@link #HATCH_LEAD} ticks later.
 */
public class BlockEntityTor extends BlockEntitySAMBase {

    static final int HATCH_LEAD = 4;
    static final double MAX_TRACK = Math.toRadians(60);

    private @Nullable Entity pending;
    private int pendingCell;

    public BlockEntityTor(BlockPos pos, BlockState state) {
        super(AirDefense.TOR_BE.get(), pos, state);
    }

    @Override protected int capacity() { return 8; }
    @Override protected Item ammoItem() { return AirDefense.SAM_TOR.get(); }
    @Override protected double range() { return 750; }
    @Override protected double ownRadarRange() { return 750; }
    @Override protected int cooldownTicks() { return 10; }
    /** Two missiles per target, like the real Tor; the vertical launch needs no slewing first. */
    @Override protected int salvo() { return 2; }
    @Override protected boolean needsAlignment() { return false; }
    @Override public double getHeightOffset() { return 2.9; }
    @Override public double getTurretYawSpeed() { return 6.0; }
    @Override public double getTurretPitchSpeed() { return 3.0; }
    @Override public double getAcceptableInaccuracy() { return 20.0; }

    /** Cell c in model space: two rows of four, back row first. */
    static double cellX(int c) { return -0.72 + 0.48 * (c % 4); }
    static double cellZ(int c) { return c < 4 ? 0.415 : -0.415; }

    @Override
    protected void aim(Vec3 t) {
        Vec3 d = t.subtract(getTurretPos());
        double pitch = Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
        turnTowardsAngle(Math.clamp(pitch, 0, MAX_TRACK), -Math.atan2(d.x, d.z));
    }

    @Override
    protected void stow() {
        turnTowardsAngle(0, rotationYaw);
    }

    @Override
    protected boolean readyToFire() {
        return pending == null;
    }

    @Override
    protected @Nullable EntitySAM launch(Entity target, int index) {
        setLaunch(index, level.getGameTime());
        pending = target;
        pendingCell = index;
        return null;
    }

    @Override
    public void tickServer() {
        super.tickServer();
        if (pending != null && level.getGameTime() >= launchTime + HATCH_LEAD) {
            Vec3 pos = Geo.point(Geo.carriage(this), cellX(pendingCell), 3.4, cellZ(pendingCell));
            launched(EntitySAM.fire(level, EntitySAM.Kind.TOR, pos, new Vec3(0, 1, 0), pending));
            pending = null;
        }
    }
}
