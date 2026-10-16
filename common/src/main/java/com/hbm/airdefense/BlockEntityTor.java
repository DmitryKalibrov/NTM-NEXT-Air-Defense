package com.hbm.airdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

    public static final int HATCH_LEAD = 6;
    static final double MAX_TRACK = Math.toRadians(60);

    private @Nullable Entity pending;
    private int pendingCell;

    public BlockEntityTor(BlockPos pos, BlockState state) {
        super(AirDefense.TOR_BE.get(), pos, state);
    }

    @Override protected int capacity() { return 8; }
    @Override protected Item ammoItem() { return AirDefense.SAM_TOR.get(); }
    @Override protected double range() { return 600; }
    @Override protected double ownRadarRange() { return 600; }
    @Override protected int cooldownTicks() { return 10; }
    /** The vertical launch needs no slewing first. */
    @Override protected boolean needsAlignment() { return false; }
    @Override public double getHeightOffset() { return 2.9; }
    // NTM snaps to the target once within one step; slew() never asks for more than that
    @Override public double getTurretYawSpeed() { return 3.0; }
    @Override public double getTurretPitchSpeed() { return 2.0; }
    @Override public double getAcceptableInaccuracy() { return 20.0; }

    /** Cell c in model space: two rows of four, back row first. */
    static double cellX(int c) { return -0.72 + 0.48 * (c % 4); }
    static double cellZ(int c) { return c < 4 ? 0.415 : -0.415; }

    @Override
    protected void aim(Vec3 t) {
        Vec3 d = t.subtract(getTurretPos());
        double pitch = Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
        slew(Math.clamp(pitch, 0, MAX_TRACK), -Math.atan2(d.x, d.z));
    }

    @Override
    protected void stow() {
        slew(0, rotationYaw);
    }

    /** Turret slew rates (rad/tick): the heavy turret speeds up and brakes instead of starting and stopping dead. */
    private double yawRate, pitchRate;

    private void slew(double targetPitch, double targetYaw) {
        double maxYaw = Math.toRadians(getTurretYawSpeed()) * 0.99, maxPitch = Math.toRadians(getTurretPitchSpeed()) * 0.99;
        yawRate = rate(yawRate, Mth.wrapDegrees(Math.toDegrees(targetYaw - rotationYaw)) * Mth.DEG_TO_RAD, maxYaw, maxYaw / 14);
        pitchRate = rate(pitchRate, targetPitch - rotationPitch, maxPitch, maxPitch / 10);
        turnTowardsAngle(rotationPitch + pitchRate, rotationYaw + yawRate);
    }

    /** Next rate towards an angle left to go: accelerate, cruise, brake so it stops right on it. */
    private static double rate(double rate, double left, double max, double accel) {
        double want = Math.signum(left) * Math.min(max, Math.sqrt(2 * accel * Math.abs(left)));
        rate += Math.clamp(want - rate, -accel, accel);
        return Math.abs(left) < accel && Math.abs(rate) <= accel ? left : rate;
    }

    @Override
    protected boolean readyToFire() {
        return pending == null;
    }

    @Override
    protected @Nullable EntitySAM launch(Entity target, int index) {
        setLaunch(index, level.getGameTime());
        Vec3 cell = Geo.point(Geo.carriage(this), cellX(index), 3.2, cellZ(index));
        level.playSound(null, cell.x, cell.y, cell.z, AdSounds.TOR_HATCH, SoundSource.BLOCKS, 3f, 1f);
        pending = target;
        pendingCell = index;
        return null;
    }

    @Override
    public void tickClient() {
        super.tickClient();
        SamSounds.tor(this);
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
