package com.hbm.airdefense;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A failed missile dug into the ground where it fell (see {@link EntitySAM#DUD}): nose down or lying flat, as it
 * came in. Stays until someone hits or uses it, which gives the missile back.
 */
public class EntitySAMWreck extends Entity {

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(EntitySAMWreck.class, EntityDataSerializers.BYTE);

    public EntitySAMWreck(EntityType<? extends EntitySAMWreck> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Lying at the ground point it hit, model along heading (rotations as EntitySAM.face). */
    static void place(ServerLevel level, EntitySAM.Kind kind, Vec3 at, Vec3 heading) {
        EntitySAMWreck w = new EntitySAMWreck(AirDefense.SAM_WRECK.get(), level);
        w.entityData.set(KIND, (byte) kind.ordinal());
        w.setPos(at.add(0, 0.01, 0));
        double h = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
        w.setYRot((float) Math.toDegrees(Math.atan2(-heading.x, -heading.z)));
        w.setXRot((float) Math.toDegrees(Math.atan2(heading.y, h)));
        level.addFreshEntity(w);
    }

    public EntitySAM.Kind kind() {
        return EntitySAM.Kind.values()[entityData.get(KIND)];
    }

    private Item item() {
        return switch (kind()) {
            case BUK -> AirDefense.SAM_BUK.get();
            case TOR -> AirDefense.SAM_TOR.get();
            case A135 -> AirDefense.ABM_53T6.get();
            case PANTSIR -> AirDefense.SAM_PANTSIR.get();
        };
    }

    private void salvage(ServerLevel level, Player player) {
        if (isRemoved()) return;
        ItemStack stack = new ItemStack(item());
        if (!player.getInventory().add(stack)) spawnAtLocation(level, stack);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.7f);
        discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level() instanceof ServerLevel server) salvage(server, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player) salvage(level, player);
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(KIND, (byte) Math.clamp(input.getByteOr("kind", (byte) 0), 0, EntitySAM.Kind.values().length - 1));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putByte("kind", entityData.get(KIND));
    }
}
