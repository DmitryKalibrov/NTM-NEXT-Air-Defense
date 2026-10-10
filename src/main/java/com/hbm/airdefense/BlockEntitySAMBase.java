package com.hbm.airdefense;

import com.hbm.api.energymk2.ItemEnergyTransfer;
import com.hbm.api.entity.IRadarDetectableNT;
import com.hbm.api.entity.RadarEntry;
import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import com.hbm.blockentity.turret.BlockEntityTurretBaseArtillery;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.inventory.container.MenuTurretBase;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.util.ChunkUtil;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared SAM launcher logic on top of NTM's turret base (power, ammo slots, GUI, whitelist, target
 * toggles). Targets come from a linked NTM radar's scan list, plus an own short-range search radar
 * for launchers that have one. One missile per target in the air at a time.
 *
 * NTM's javac plugin turns @SyncField writes into dirty-marking setters; we don't run it, so our
 * own synced fields call {@link #syncUnitsChanged} by hand (units 11..14, like HIMARS).
 */
public abstract class BlockEntitySAMBase extends BlockEntityTurretBaseArtillery {

    private static final long U_AMMO = 1L << 11, U_RADAR = 1L << 12, U_CELL = 1L << 13, U_TIME = 1L << 14;

    public @Nullable BlockPos radarPos;
    /** Missiles on the launcher: mirrors the single ammo slot, synced so the renderer can draw them. */
    public int ammo;
    /** Fire-control radar deployment 0..1 (Buk unfolds it, Tor ignores it). */
    public float radar, lastRadar;
    /** Last launched cell and when, for the hatch animation. */
    public int launchCell = -1;
    public long launchTime;

    private int cooldown;
    private @Nullable EntitySAM inFlight;

    protected BlockEntitySAMBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- per launcher ----

    protected abstract int capacity();

    protected abstract Item ammoItem();

    /** Engagement range in blocks. */
    protected abstract double range();

    /** Range of the launcher's own search radar; 0 = needs a linked radar. */
    protected abstract double ownRadarRange();

    /** Point the launcher at the current target. */
    protected abstract void aim(Vec3 target);

    /** Return to the stowed pose. */
    protected abstract void stow();

    /** Launcher is pointed and deployed. */
    protected abstract boolean readyToFire();

    /** Put a missile into the air; null if it is not out yet (delayed launch). */
    protected abstract @Nullable EntitySAM launch(Entity target, int index);

    protected int cooldownTicks() {
        return 40;
    }

    // ---- turret base plumbing ----

    /** Firing is driven from {@link #tickServer}, not NTM's aligned-then-fire hook. */
    @Override
    public void updateFiringTick() {}

    @Override
    protected @Nullable List<BulletConfig> getAmmoList() {
        return List.of();
    }

    @Override
    public List<ItemStack> getAmmoTypesForDisplay() {
        return List.of(new ItemStack(ammoItem()));
    }

    @Override
    public boolean doLOSCheck() {
        return false;
    }

    @Override
    public double getDetectorRange() {
        return range();
    }

    @Override
    public double getDetectorGrace() {
        return 8.0;
    }

    @Override
    public long getMaxPower() {
        return 1_000_000L;
    }

    /** The missiles live in the first ammo slot only; its stack size is the launcher's load. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_AMMO_FIRST) return stack.is(ammoItem());
        return slot == SLOT_BATTERY || slot == SLOT_CHIP;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MenuTurretBase(containerId, playerInventory, this);
    }

    public void linkRadar(BlockPos pos) {
        radarPos = pos;
        markChanged();
    }

    @Override
    public void tickClient() {
        lastRadar = radar;
        super.tickClient();
    }

    @Override
    public void tickServer() {
        long prevPower = power;
        aligned = false;
        ItemStack load = getItem(SLOT_AMMO_FIRST);
        setAmmo(load.is(ammoItem()) ? Math.min(load.getCount(), capacity()) : 0);

        boolean active = isOn() && hasPower();
        if (active) setPower(getPower() - getConsumption());

        if (target != null && (!active || !stillValid(target))) {
            if (!target.isAlive()) hbm$syncSet$BlockEntityTurretBaseNT$stattrak(stattrak + 1);
            target = null;
        }
        if (active && target == null && --searchTimer <= 0) {
            searchTimer = getDetectorInterval();
            target = findTarget();
        }
        hbm$syncSet$BlockEntityTurretBaseNT$tPos(target != null ? getEntityPos(target) : null);

        if (cooldown > 0) cooldown--;
        if (target != null) {
            aim(tPos);
            boolean busy = inFlight != null && inFlight.isAlive();
            if (aligned && readyToFire() && ammo > 0 && cooldown <= 0 && !busy) {
                EntitySAM sam = launch(target, capacity() - ammo);
                removeItem(SLOT_AMMO_FIRST, 1);
                setAmmo(ammo - 1);
                cooldown = cooldownTicks();
                if (sam != null) inFlight = sam;
            }
        } else {
            stow();
        }

        hbm$syncSet$BlockEntityTurretBaseNT$power(power + ItemEnergyTransfer.extract((Container) this, SLOT_BATTERY, getMaxPower() - power, false));
        if (power != prevPower) markChanged();
        networkPackNT(250);
    }

    /** Hand a delayed missile over once it is spawned. */
    protected void launched(EntitySAM sam) {
        inFlight = sam;
    }

    // ---- targeting ----

    private boolean stillValid(Entity e) {
        return e.isAlive() && e.level() == level && isAirTarget(e)
                && getEntityPos(e).distanceTo(getTurretPos()) <= range() * 1.1;
    }

    private @Nullable Entity findTarget() {
        Vec3 pos = getTurretPos();
        List<Entity> seen = new ArrayList<>();
        BlockEntityMachineRadar radarBe = linkedRadar();
        if (radarBe != null) {
            for (RadarEntry entry : radarBe.entries) {
                Entity e = level.getEntity(entry.entityID);
                if (e != null) seen.add(e);
            }
        }
        double own = ownRadarRange();
        if (own > 0) seen.addAll(level.getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(own)));

        Entity best = null;
        double closest = range();
        for (Entity e : seen) {
            double d = getEntityPos(e).distanceTo(pos);
            if (d < closest && d > getDetectorGrace() && isAirTarget(e)) {
                closest = d;
                best = e;
            }
        }
        return best;
    }

    public @Nullable BlockEntityMachineRadar linkedRadar() {
        return radarPos == null ? null : ChunkUtil.blockEntityIfLoaded(BlockEntityMachineRadar.class, level, radarPos);
    }

    /**
     * NTM's own filters (toggles, whitelist), and only things up in the air. Missiles are taken in
     * any flight phase: NTM turrets wait for the descent, by then a SAM is too late.
     */
    protected boolean isAirTarget(Entity e) {
        if (e instanceof EntitySAM || !e.isAlive()) return false;
        if (e instanceof EntityMissileBaseNT) {
            return targetMachines && !(e instanceof IRadarDetectableNT d && !d.canBeSeenBy(this));
        }
        if (!entityAcceptableTarget(e)) return false;
        if (e instanceof LivingEntity living && living.isFallFlying()) return true;
        if (e.onGround()) return false;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, e.getBlockX(), e.getBlockZ());
        return e.getY() - ground > 5;
    }

    // ---- synced state ----

    protected void setAmmo(int v) {
        if (ammo == v) return;
        ammo = v;
        syncUnitsChanged(3, U_AMMO);
        markChanged();
    }

    protected void setRadar(float v) {
        if (radar == v) return;
        radar = v;
        syncUnitsChanged(3, U_RADAR);
    }

    protected void setLaunch(int cell, long time) {
        launchCell = cell;
        launchTime = time;
        syncUnitsChanged(3, U_CELL | U_TIME);
    }

    @Override
    public long syncUnitMask() {
        return super.syncUnitMask() | U_AMMO | U_RADAR | U_CELL | U_TIME;
    }

    @Override
    public void writeSyncUnit(int unit, ByteBuf out) {
        switch (unit) {
            case 11 -> out.writeByte(ammo);
            case 12 -> out.writeFloat(radar);
            case 13 -> out.writeByte(launchCell);
            case 14 -> out.writeLong(launchTime);
            default -> super.writeSyncUnit(unit, out);
        }
    }

    @Override
    public void readSyncUnit(int unit, ByteBuf in) {
        switch (unit) {
            case 11 -> ammo = in.readByte();
            case 12 -> radar = in.readFloat();
            case 13 -> launchCell = in.readByte();
            case 14 -> launchTime = in.readLong();
            default -> super.readSyncUnit(unit, in);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        radarPos = input.getLong("radarPos").map(BlockPos::of).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (radarPos != null) output.putLong("radarPos", radarPos.asLong());
    }

    @Override
    public Component getName() {
        return getBlockState().getBlock().getName();
    }
}
