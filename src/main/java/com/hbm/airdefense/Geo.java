package com.hbm.airdefense;

import com.hbm.blockentity.turret.BlockEntityTurretBaseNT;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Model space -> world, mirroring what the renderers do: model origin at the turret's horizontal
 * offset on the block, carriage turned by (yaw - 180) degrees, parts posed inside that.
 * Model space is the OBJ's: Y up, launcher nose towards -Z.
 */
final class Geo {

    private Geo() {}

    /** Carriage transform of a turret, in world space. */
    static Matrix4d carriage(BlockEntityTurretBaseNT be) {
        Vec3 o = be.getHorizontalOffset();
        double yawDeg = -Math.toDegrees(be.rotationYaw);
        return new Matrix4d()
                .translate(be.getBlockPos().getX() + o.x, be.getBlockPos().getY(), be.getBlockPos().getZ() + o.z)
                .rotateY(Math.toRadians(yawDeg - 180));
    }

    /** Rotate about the X axis through a pivot (the launcher/radar hinge idiom). */
    static Matrix4d hingeX(Matrix4d m, double py, double pz, double angle) {
        return m.translate(0, py, pz).rotateX(angle).translate(0, -py, -pz);
    }

    static Vec3 point(Matrix4d m, double x, double y, double z) {
        Vector3d v = m.transformPosition(new Vector3d(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    static Vec3 dir(Matrix4d m, double x, double y, double z) {
        Vector3d v = m.transformDirection(new Vector3d(x, y, z)).normalize();
        return new Vec3(v.x, v.y, v.z);
    }
}
