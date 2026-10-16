package com.hbm.airdefense;

import com.hbm.util.ChunkUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Debug (see {@link ItemTargetCaller}): an invisible point far away that either fires bursts of one weapon's sound
 * for half a minute, or calls a practice V-2 over itself and shoots SAMs of one kind at it, so the far sounds can be
 * checked from where the player stands. Not saved.
 */
public class EntityDebugEmitter extends Entity {

    public static final int FIRE = 0, SAM = 1;
    private static final int FIRE_LIFE = 600, SAM_LIFE = 1600, SALVO = 2, SALVO_GAP = 30;

    private int mode, option, shots, wait, fired;
    private @Nullable EntityTargetDrone drone;

    public EntityDebugEmitter(EntityType<? extends EntityDebugEmitter> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** viewer: where the player stands; the practice V-2 comes in from beyond the emitter, towards them. */
    static void spawn(ServerLevel level, Vec3 at, int mode, int option, Vec3 viewer) {
        EntityDebugEmitter e = new EntityDebugEmitter(AirDefense.DEBUG_EMITTER.get(), level);
        e.mode = mode;
        e.option = option;
        e.setPos(at);
        level.addFreshEntity(e);
        if (mode != SAM) return;
        Vec3 away = new Vec3(at.x - viewer.x, 0, at.z - viewer.z);
        away = away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 start = at.add(away.scale(700)), aim = at.subtract(away.scale(150));  // lands short of the emitter's far side
        int sx = Mth.floor(start.x), sz = Mth.floor(start.z);
        ChunkUtil.loadTickingForEntity(level, new net.minecraft.world.level.ChunkPos(sx >> 4, sz >> 4));
        EntityTargetDrone d = new EntityTargetDrone(AirDefense.TARGET_DRONE.get(), level);
        d.launch(sx + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, sx, sz) + 2, sz + 0.5,
                Mth.floor(aim.x), Mth.floor(aim.z));
        d.addTag(ItemTargetCaller.PRACTICE);
        level.addFreshEntity(d);
        e.drone = d;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        ChunkUtil.holdOwnChunk(this);
        if (mode == FIRE) fire(server);
        else sam(server);
    }

    private void fire(ServerLevel server) {
        if (tickCount > FIRE_LIFE) {
            discard();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        ItemTargetCaller.Weapon w = ItemTargetCaller.WEAPONS.get(option);
        if (shots <= 0) {  // next burst after a pause
            shots = w.shots();
            wait = 20 + random.nextInt(60);
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.fromNamespaceAndPath("hbm", w.sound()));
        if (sound != null) server.playSound(null, getX(), getY() + 1.5, getZ(), sound, SoundSource.PLAYERS, 1f, 0.95f + random.nextFloat() * 0.1f);
        shots--;
        wait = w.gap();
    }

    private void sam(ServerLevel server) {
        if (tickCount > SAM_LIFE || drone == null || drone.isRemoved() && tickCount > 200) {
            discard();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        EntitySAM.Kind kind = EntitySAM.Kind.values()[option];
        double range = switch (kind) { case BUK -> 900; case TOR -> 600; case PANTSIR -> 500; case A135 -> 1500; };
        if (fired >= SALVO || !drone.isAlive() || drone.getY() < getY() + 40 || drone.distanceTo(this) > range) return;
        Vec3 pos = position().add(0, 3, 0);
        Vec3 dir = kind == EntitySAM.Kind.TOR || kind == EntitySAM.Kind.A135 ? new Vec3(0, 1, 0)
                : drone.position().subtract(pos).normalize().add(0, 0.5, 0).normalize();
        EntitySAM.fire(server, kind, pos, dir, drone);
        fired++;
        wait = SALVO_GAP;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
