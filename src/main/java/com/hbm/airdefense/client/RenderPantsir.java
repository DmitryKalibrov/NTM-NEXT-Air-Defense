package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityPantsir;
import com.hbm.client.model.Meshes;
import com.hbm.client.render.RenderTurretBase;
import com.hbm.client.render.TurretFrame;
import com.hbm.render.loader.HFRWavefrontObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Pantsir: turret yaw; the two side weapon blocks and the tracking radar each pitch on their own
 * axis; the search radar folds back over the rear when off duty and turns when up; tube covers
 * show which tubes are still loaded.
 */
public class RenderPantsir extends RenderTurretBase<BlockEntityPantsir> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/pantsir.obj"));
    static final Identifier TEX = AirDefense.id("textures/block/pantsir.png");
    private static final int TURRET = MESH.partId("Turret"), SEARCH = MESH.partId("SearchRadar"),
            TRACK = MESH.partId("TrackRadar");
    private static final int[] WEAPONS = MESH.partIds("WeaponL", "WeaponR");
    private static final int[] CAPS = MESH.partIds("Cap1", "Cap2", "Cap3", "Cap4", "Cap5", "Cap6",
            "Cap7", "Cap8", "Cap9", "Cap10", "Cap11", "Cap12");

    // model space: side blocks (y, z), tracking radar (y, z), search radar column z and fold hinge y
    private static final float WY = 1.65f, WZ = -0.2f, TY = 1.45f, TZ = -1.15f, SZ = 1.9f, SY = 2.85f;

    @Override
    protected void emit(BlockEntityPantsir t, State s, TurretFrame frame) {
        TurretFrame.Part turret = frame.push().obj(MESH, TURRET).texture(TEX);
        turret.pose.rotateY((s.yaw - 180F) * Mth.DEG_TO_RAD);

        float time = s.gameTime + s.partialTicks;
        float up = Mth.lerp(s.partialTicks, t.lastRadar, t.radar);
        float spin = up >= 1f && t.isOn() ? time * Mth.TWO_PI / 40f : 0f;
        frame.push().obj(MESH, SEARCH).texture(TEX).pose.set(turret.pose)
                .translate(0, 0, SZ).rotateY(spin).translate(0, 0, -SZ)
                .translate(0, SY, SZ).rotateX((1f - up) * Mth.HALF_PI).translate(0, -SY, -SZ);

        float pitch = s.pitch * Mth.DEG_TO_RAD;
        frame.push().obj(MESH, TRACK).texture(TEX).pose.set(turret.pose)
                .translate(0, TY, TZ).rotateX(pitch).translate(0, -TY, -TZ);
        for (int part : WEAPONS)
            frame.push().obj(MESH, part).texture(TEX).pose.set(turret.pose)
                    .translate(0, WY, WZ).rotateX(pitch).translate(0, -WY, -WZ);
        // tubes fire in order, so the first (12 - ammo) covers are gone
        for (int c = 12 - t.ammo; c < CAPS.length; c++)
            frame.push().obj(MESH, CAPS[c]).texture(TEX).pose.set(turret.pose)
                    .translate(0, WY, WZ).rotateX(pitch).translate(0, -WY, -WZ);
    }
}
