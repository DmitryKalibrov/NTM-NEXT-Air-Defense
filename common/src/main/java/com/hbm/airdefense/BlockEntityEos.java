package com.hbm.airdefense;

import com.hbm.util.ChunkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Optoelectronic station: thermal imager, TV camera and laser rangefinder on a pan / tilt head. Slaved to a
 * launcher it looks where the launcher aims; the operator can take it over (free) or let it track a contact it
 * locked (track, which also designates that contact to the launcher). Yaw and pitch are Minecraft's (yaw 0 =
 * south, pitch positive = down), so the operator's camera takes them as they are.
 */
public class BlockEntityEos extends BlockEntity {

    public static final int SLAVE = 0, FREE = 1, TRACK = 2;
    /** Height of the tilt axis over the block's floor, and the lenses ahead of it (tools/blender/eos.py). */
    public static final double PITCH_Y = 1.55, LENS = 0.32;
    private static final float SLEW = 9f;  // degrees per tick

    public @Nullable BlockPos launcher;
    public int mode = SLAVE;
    public float yaw = 180, pitch, aimYaw = 180, aimPitch;
    public float prevYaw = 180, prevPitch;
    public int trackId = -1;
    private int synced;

    public BlockEntityEos(BlockPos pos, BlockState state) {
        super(AirDefense.EOS_BE.get(), pos, state);
    }

    /** Where the tilt axis is, in the world. */
    public Vec3 pivot() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, PITCH_Y, 0);
    }

    /** Where the camera sits: just in front of the lenses. */
    public Vec3 eye(float yawDeg, float pitchDeg) {
        return pivot().add(Vec3.directionFromRotation(pitchDeg, yawDeg).scale(LENS));
    }

    public void tickClient() {
        prevYaw = yaw;
        prevPitch = pitch;
    }

    public void tickServer() {
        if (level == null) return;
        Vec3 want = null;
        if (mode == TRACK) {
            Entity e = level.getEntity(trackId);
            if (e == null || !e.isAlive() || e.position().distanceTo(pivot()) > 2000) mode = FREE;
            else want = e.position().add(0, e.getBbHeight() / 2, 0);
        } else if (mode == SLAVE && launcher != null
                && ChunkUtil.blockEntityIfLoaded(BlockEntitySAMBase.class, level, launcher) instanceof BlockEntitySAMBase sam && sam.tPos != null) {
            want = sam.tPos;
        }
        if (want != null) {
            Vec3 d = want.subtract(pivot());
            aimYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            aimPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }
        float ny = yaw + Mth.clamp(Mth.wrapDegrees(aimYaw - yaw), -SLEW, SLEW);
        float np = Mth.clamp(pitch + Mth.clamp(aimPitch - pitch, -SLEW, SLEW), -85, 30);
        boolean moved = Math.abs(ny - yaw) > 0.01f || Math.abs(np - pitch) > 0.01f;
        yaw = Mth.wrapDegrees(ny);
        pitch = np;
        if (moved || ++synced >= 40) {  // the head is synced while it moves, and now and then
            synced = 0;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** From a console: "aim" (free, to yaw / pitch), "lock" (track entity id, designate it), "slave". */
    public void command(CompoundTag data) {
        switch (data.getStringOr("cmd", "")) {
            case "aim" -> {
                mode = FREE;
                aimYaw = data.getFloatOr("yaw", aimYaw);
                aimPitch = Mth.clamp(data.getFloatOr("pitch", aimPitch), -85, 30);
            }
            case "lock" -> {
                Entity e = level == null ? null : level.getEntity(data.getIntOr("id", -1));
                if (e == null) return;
                mode = TRACK;
                trackId = e.getId();
                if (launcher != null && ChunkUtil.blockEntityIfLoaded(BlockEntitySAMBase.class, level, launcher) instanceof BlockEntitySAMBase sam) sam.designate(e);
            }
            case "slave" -> {
                mode = SLAVE;
                trackId = -1;
            }
            default -> {}
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        launcher = input.getLong("launcher").map(BlockPos::of).orElse(null);
        mode = input.getIntOr("mode", SLAVE);
        yaw = input.getFloatOr("yaw", 180);
        pitch = input.getFloatOr("pitch", 0);
        aimYaw = input.getFloatOr("aimYaw", yaw);
        aimPitch = input.getFloatOr("aimPitch", pitch);
        trackId = input.getIntOr("track", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (launcher != null) output.putLong("launcher", launcher.asLong());
        output.putInt("mode", mode);
        output.putFloat("yaw", yaw);
        output.putFloat("pitch", pitch);
        output.putFloat("aimYaw", aimYaw);
        output.putFloat("aimPitch", aimPitch);
        output.putInt("track", trackId);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
