package com.hbm.airdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;

/**
 * Buk (9A310): no search radar of its own, needs a linked NTM radar. Raises the rail to 40 degrees
 * about its rear head and fires once the 9S35 in the nose is up.
 */
public class BlockEntityBuk extends BlockEntitySAMBase {

    static final double ELEVATION = Math.toRadians(40);
    /** Launcher hinge (y, z) and rail x positions in model space; missile centres sit at y 3.15, z -0.275. */
    static final double PIVOT_Y = 2.05, PIVOT_Z = 2.6;
    static final double[] RAIL_X = {-1.125, -0.375, 0.375, 1.125};

    public BlockEntityBuk(BlockPos pos, BlockState state) {
        super(AirDefense.BUK_BE.get(), pos, state);
    }

    @Override protected int capacity() { return 4; }
    @Override protected Item ammoItem() { return AirDefense.SAM_BUK.get(); }
    @Override protected double range() { return 1250; }
    @Override protected double ownRadarRange() { return 0; }
    @Override public double getHeightOffset() { return 3.0; }
    @Override public double getTurretYawSpeed() { return 6.0; }
    @Override public double getTurretPitchSpeed() { return 3.0; }

    @Override
    protected void aim(Vec3 t) {
        Vec3 d = t.subtract(getTurretPos());
        turnTowardsAngle(ELEVATION, -Math.atan2(d.x, d.z));
        deployRadar();
    }

    /**
     * On combat duty (switched on and powered) the rail stays raised and the radar up, so a
     * detected target only costs the slew. Lowers only when switched off or out of power.
     */
    @Override
    protected void stow() {
        if (isOn() && hasPower()) {
            turnTowardsAngle(ELEVATION, rotationYaw);
            deployRadar();
        } else if (radar > 0) {
            setRadar(Math.max(0f, radar - 0.1f));
        } else {
            turnTowardsAngle(0, rotationYaw);
        }
    }

    /** The 9S35 under the nose radome needs a moment to come up after switching on. */
    private void deployRadar() {
        setRadar(Math.min(1f, radar + 0.1f));
    }

    @Override
    protected boolean readyToFire() {
        return radar >= 1f;
    }

    @Override
    protected EntitySAM launch(Entity target, int index) {
        Matrix4d m = Geo.hingeX(Geo.carriage(this), PIVOT_Y, PIVOT_Z, rotationPitch);
        Vec3 pos = Geo.point(m, RAIL_X[ammo - 1], 3.15, -0.275);
        Vec3 dir = Geo.dir(m, 0, 0, -1);
        return EntitySAM.fire(level, EntitySAM.Kind.BUK, pos, dir, target);
    }
}
