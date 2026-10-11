package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityBuk;
import com.hbm.client.model.Meshes;
import com.hbm.client.render.RenderTurretBase;
import com.hbm.client.render.TurretFrame;
import com.hbm.render.loader.HFRWavefrontObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Buk (9A310): carriage yaw with the radome in the nose, rail elevation about the rear head, the rams following it. */
public class RenderBuk extends RenderTurretBase<BlockEntityBuk> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/buk.obj"));
    static final Identifier TEX = AirDefense.id("textures/block/buk.png");
    private static final int CARRIAGE = MESH.partId("Carriage"), RADAR = MESH.partId("Radar"),
            LAUNCHER = MESH.partId("Launcher"), RAM_CYL = MESH.partId("RamCylinder"), RAM_ROD = MESH.partId("RamRod");
    private static final int[] MISSILES = MESH.partIds("Missile1", "Missile2", "Missile3", "Missile4");

    // Hinges in model space (y, z): launcher, ram on the hull (A) and its lug on the rail (B).
    private static final float LY = 2.05f, LZ = 2.6f;
    private static final float AY = 1.63f, AZ = 0.6f, BY = 2.45f, BZ = 1.2f;

    @Override
    protected void emit(BlockEntityBuk t, State s, TurretFrame frame) {
        TurretFrame.Part carriage = frame.push().obj(MESH, CARRIAGE).texture(TEX);
        carriage.pose.rotateY((s.yaw - 180F) * Mth.DEG_TO_RAD);

        frame.push().obj(MESH, RADAR).texture(TEX).pose.set(carriage.pose);

        float pitch = s.pitch * Mth.DEG_TO_RAD;
        TurretFrame.Part launcher = frame.push().obj(MESH, LAUNCHER).texture(TEX);
        launcher.pose.set(carriage.pose).translate(0, LY, LZ).rotateX(pitch).translate(0, -LY, -LZ);
        for (int i = 0; i < t.ammo && i < MISSILES.length; i++)
            frame.push().obj(MESH, MISSILES[i]).texture(TEX).pose.set(launcher.pose);

        // Ram: lug B swings with the rail; the cylinder turns about A to point at it, the rod slides out.
        float c = Mth.cos(pitch), sn = Mth.sin(pitch);
        float relY = BY - LY, relZ = BZ - LZ;
        float b1y = LY + relY * c - relZ * sn, b1z = LZ + relY * sn + relZ * c;
        float d0y = BY - AY, d0z = BZ - AZ, d1y = b1y - AY, d1z = b1z - AZ;
        float swing = (float) (Math.atan2(d1y, -d1z) - Math.atan2(d0y, -d0z));
        float len1 = Mth.sqrt(d1y * d1y + d1z * d1z), ext = len1 - Mth.sqrt(d0y * d0y + d0z * d0z);
        frame.push().obj(MESH, RAM_CYL).texture(TEX).pose
                .set(carriage.pose).translate(0, AY, AZ).rotateX(swing).translate(0, -AY, -AZ);
        frame.push().obj(MESH, RAM_ROD).texture(TEX).pose
                .set(carriage.pose).translate(0, d1y / len1 * ext, d1z / len1 * ext)
                .translate(0, AY, AZ).rotateX(swing).translate(0, -AY, -AZ);
    }
}
