package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityTor;
import com.hbm.client.model.Meshes;
import com.hbm.client.render.RenderTurretBase;
import com.hbm.client.render.TurretFrame;
import com.hbm.render.loader.HFRWavefrontObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Tor: turret yaw, spinning search antenna, tracking dish elevation, the launch cell's hatch. */
public class RenderTor extends RenderTurretBase<BlockEntityTor> {

    static final HFRWavefrontObject MESH = Meshes.load(AirDefense.id("models/obj/tor.obj"));
    static final Identifier TEX = AirDefense.id("textures/block/tor.png");
    private static final int TURRET = MESH.partId("Turret"), SEARCH = MESH.partId("SearchRadar"),
            TRACK = MESH.partId("TrackRadar");
    private static final int[] HATCHES =
            MESH.partIds("Hatch1", "Hatch2", "Hatch3", "Hatch4", "Hatch5", "Hatch6", "Hatch7", "Hatch8");

    private static final float SEARCH_Z = 1.1f, TRACK_Y = 2.1f, TRACK_Z = -0.95f, HINGE_Y = 2.22f;
    private static final float HATCH_OPEN = 110f * Mth.DEG_TO_RAD;

    @Override
    protected void emit(BlockEntityTor t, State s, TurretFrame frame) {
        TurretFrame.Part turret = frame.push().obj(MESH, TURRET).texture(TEX);
        turret.pose.rotateY((s.yaw - 180F) * Mth.DEG_TO_RAD);

        float time = s.gameTime + s.partialTicks;
        float spin = t.isOn() ? time * Mth.TWO_PI / 40f : 0f;
        frame.push().obj(MESH, SEARCH).texture(TEX).pose
                .set(turret.pose).translate(0, 0, SEARCH_Z).rotateY(spin).translate(0, 0, -SEARCH_Z);

        frame.push().obj(MESH, TRACK).texture(TEX).pose
                .set(turret.pose).translate(0, TRACK_Y, TRACK_Z).rotateX(s.pitch * Mth.DEG_TO_RAD)
                .translate(0, -TRACK_Y, -TRACK_Z);

        float dt = time - t.launchTime;
        float open = smooth(dt / 8f) * (1f - smooth((dt - 30f) / 10f));
        for (int c = 0; c < HATCHES.length; c++) {
            boolean back = c < 4;  // back row hinges at the rear edge, front row at the front edge
            float z = back ? 0.81f : -0.11f;
            float a = c == t.launchCell ? open * HATCH_OPEN * (back ? 1f : -1f) : 0f;
            frame.push().obj(MESH, HATCHES[c]).texture(TEX).pose
                    .set(turret.pose).translate(0, HINGE_Y, z).rotateX(a).translate(0, -HINGE_Y, -z);
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
