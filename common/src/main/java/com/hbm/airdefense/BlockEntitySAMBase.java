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

    /** The name from the field above its window ({@link DeviceName}). */
    @Override
    public void receiveControl(net.minecraft.nbt.CompoundTag data) {
        if (!DeviceName.receive(this, data)) super.receiveControl(data);
    }

    private static final long U_AMMO = 1L << 11, U_RADAR = 1L << 12, U_CELL = 1L << 13, U_TIME = 1L << 14;

    public @Nullable BlockPos radarPos;
    /** Missiles on the launcher: mirrors the single ammo slot, synced so the renderer can draw them. */
    public int ammo;
    /** Fire-control radar deployment 0..1 (Buk unfolds it, Tor ignores it). */
    public float radar, lastRadar;
    /** Last launched cell and when, for the hatch animation. */
    public int launchCell = -1;
    public long launchTime;

    /** How far out from the site terrain can hide a target, see {@link #unmasked}. */
    static final double MASK_RANGE = 96;

    private int cooldown;
    /** Manual mode (from a console): engage only the target designated on the radar, fire only on order. */
    public boolean manual;
    private @Nullable Entity designated;
    private int fireOrders;
    private final List<EntitySAM> inFlight = new ArrayList<>();

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

    /**
     * Radar horizon: a site higher above sea level sees and engages further. Grows with the square
     * root of the height (+20% at 16 blocks up, +40% at 64) and stops at +50%; no change at or
     * below sea level.
     */
    protected double reach(double base) {
        double above = getTurretPos().y - level.getSeaLevel();
        return base * Math.min(1.5, 1 + 0.05 * Math.sqrt(Math.max(0, above)));
    }

    protected int cooldownTicks() {
        return 40;
    }

    /** Missiles allowed in the air at once (a salvo on the same target). */
    protected int salvo() {
        return 1;
    }

    /** Must the launcher be pointed before firing (rail launch) or not (vertical launch). */
    protected boolean needsAlignment() {
        return true;
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
        return reach(range());
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

    /** Missiles of this launcher still flying (for the console monitors). */
    public int inFlightCount() {
        return inFlight.size();
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

        if (manual && target != null && target != designated) target = null;  // the operator moved the designation
        if (target != null && (!active || !stillValid(target))) {
            if (!target.isAlive()) hbm$syncSet$BlockEntityTurretBaseNT$stattrak(stattrak + 1);
            target = null;
        }
        if (active && target == null && --searchTimer <= 0) {
            searchTimer = getDetectorInterval();
            target = acquire();
        }
        if (target == null && designated == null) fireOrders = 0;
        hbm$syncSet$BlockEntityTurretBaseNT$tPos(target != null ? getEntityPos(target) : null);

        if (cooldown > 0) cooldown--;
        if (target != null) {
            aim(tPos);
            // a missile that stopped ticking (stuck in a chunk that never loaded) would block the salvo
            inFlight.removeIf(s -> {
                boolean frozen = s.isAlive() && level.getGameTime() - s.lastTicked > 40;
                if (frozen) s.discard();
                return frozen || !s.isAlive() || s.lost();
            });
            boolean busy = inFlight.size() >= salvo();
            Entity shot = missileTarget();
            // one missile per target across all launchers: the next goes only once that one is gone
            // manual: the operator decides, a second launcher may go at a target that already has a missile
            if (shot != null && (manual || !EntitySAM.engaged(shot)) && canLaunch() && readyToFire() && ammo > 0 && cooldown <= 0 && !busy
                    && (!manual || fireOrders > 0)) {
                if (manual) fireOrders--;
                EntitySAM sam = launch(shot, capacity() - ammo);
                removeItem(SLOT_AMMO_FIRST, 1);
                setAmmo(ammo - 1);
                cooldown = cooldownTicks();
                SamLog.add(level, worldPosition, getTurretPos(), SamLog.LAUNCH, shot.getId());
                if (sam != null) launched(sam);
            }
        } else {
            stow();
        }

        hbm$syncSet$BlockEntityTurretBaseNT$power(power + ItemEnergyTransfer.extract((Container) this, SLOT_BATTERY, getMaxPower() - power, false));
        if (power != prevPower) markChanged();
        networkPackNT(250);
    }

    /** A new target: the launcher's own pick, or in manual mode the designated one while it is engageable. */
    protected @Nullable Entity acquire() {
        if (!manual) return findTarget();
        return designated != null && operatorTarget(designated) ? designated : null;
    }

    /**
     * What the operator designated: the target-type toggles don't apply (the operator decides), only that it is
     * there, not friendly, not our own missile, in reach and not hidden by terrain.
     */
    protected boolean operatorTarget(Entity e) {
        return e.isAlive() && e.level() == level && !(e instanceof EntitySAM) && !AirDefenseTeams.friendly(level, worldPosition, e)
                && getEntityPos(e).distanceTo(getTurretPos()) <= reach(range()) * 1.1 && unmasked(getEntityPos(e));
    }

    /** A console control assigned to this launcher moved, see {@link ConsoleFunctions}. */
    /** A console control moved; for a launch order, why it can't be carried out (a lang key suffix), else null. */
    public @Nullable String command(String fn, int value) {
        String refused = null;
        switch (fn) {
            case "power" -> hbm$syncSet$BlockEntityTurretBaseNT$isOn(value != 0);
            case "manual" -> manual = value != 0;
            case "mode" -> {  // off / auto / manual
                hbm$syncSet$BlockEntityTurretBaseNT$isOn(value > 0);
                manual = value >= 2;
            }
            case "fire" -> {
                if (!isOn()) refused = "off";
                else if (!hasPower()) refused = "power";
                else if (!manual) refused = "auto";
                else if (designated == null) refused = "no_target";
                else if (!operatorTarget(designated)) refused = "out_of_reach";
                else if (ammo <= 0) refused = "ammo";
                else fireOrders = Math.min(fireOrders + 1, salvo());
            }
            case "f_players" -> hbm$syncSet$BlockEntityTurretBaseNT$targetPlayers(value != 0);
            case "f_animals" -> hbm$syncSet$BlockEntityTurretBaseNT$targetAnimals(value != 0);
            case "f_mobs" -> hbm$syncSet$BlockEntityTurretBaseNT$targetMobs(value != 0);
            case "f_machines" -> hbm$syncSet$BlockEntityTurretBaseNT$targetMachines(value != 0);
            case "drop" -> {
                designated = null;
                target = null;
            }
            default -> {}
        }
        markChanged();
        return refused;
    }

    /** Target filter bits for the consoles: players 1, animals 2, mobs 4, machines 8 (Pantsir: missiles << 4). */
    public int filterBits() {
        return (targetPlayers ? 1 : 0) | (targetAnimals ? 2 : 0) | (targetMobs ? 4 : 0) | (targetMachines ? 8 : 0);
    }

    /** The target picked on a console's radar screen (null = none). */
    public void designate(@Nullable Entity e) {
        designated = e;
        searchTimer = 0;  // take it up on the next tick
    }

    /** What the missiles go at; the aimed target unless a launcher picks its own. */
    protected @Nullable Entity missileTarget() {
        return target;
    }

    /** The launcher points where a missile can go now. */
    protected boolean canLaunch() {
        return aligned || !needsAlignment();
    }

    /** Hand a delayed missile over once it is spawned. */
    protected void launched(EntitySAM sam) {
        sam.launcher = worldPosition;
        inFlight.add(sam);
    }

    /** The last hit's TV picture: missile and target positions per tick, relative to the burst, in 1/8 blocks. */
    public List<Integer> clip = List.of();
    public long clipTime;

    void clip(List<double[]> track, Vec3 at) {
        List<Integer> out = new ArrayList<>();
        for (double[] t : track) {
            for (int k = 0; k < 6; k++) out.add((int) Math.round((t[k] - (k % 3 == 0 ? at.x : k % 3 == 1 ? at.y : at.z)) * 8));
        }
        clip = out;
        clipTime = level.getGameTime();
    }

    // ---- targeting ----

    protected boolean stillValid(Entity e) {
        if (manual && e == designated) return operatorTarget(e);
        return e.isAlive() && e.level() == level && isAirTarget(e)
                && getEntityPos(e).distanceTo(getTurretPos()) <= reach(range()) * 1.1 && unmasked(getEntityPos(e));
    }

    protected @Nullable Entity findTarget() {
        Vec3 pos = getTurretPos();
        Entity best = null;
        double max = reach(range()), bestScore = Double.MAX_VALUE;
        for (Entity e : seen()) {
            double d = getEntityPos(e).distanceTo(pos);
            if (d < max && d > getDetectorGrace() && isAirTarget(e) && unmasked(getEntityPos(e))) {
                // closest first, but one nobody is shooting at yet before one that already has a missile on it
                double score = EntitySAM.engaged(e) ? d + max : d;
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
        }
        return best;
    }

    /**
     * Terrain masking, the real limit on a short-range SAM: hills, trees and buildings around the site
     * hide low targets from its radar. Only the first {@link #MASK_RANGE} blocks of the line of sight
     * are checked (further out the target is high enough above anything that matters), skipping the
     * launcher's own blocks; unloaded chunks count as clear, so this never loads terrain.
     */
    protected boolean unmasked(Vec3 at) {
        Vec3 from = getTurretPos().add(0, 1, 0);
        Vec3 dir = at.subtract(from);
        double len = Math.min(dir.length(), MASK_RANGE);
        dir = dir.normalize();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double t = 4; t < len; t += 0.5) { // ponytail: point samples, can slip past a block's corner
            p.set(from.x + dir.x * t, from.y + dir.y * t, from.z + dir.z * t);
            if (!level.hasChunkAt(p)) return true;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** Everything the linked radar and the own search radar see. */
    protected List<Entity> seen() {
        Vec3 pos = getTurretPos();
        List<Entity> seen = new ArrayList<>();
        BlockEntityMachineRadar radarBe = linkedRadar();
        if (radarBe != null) {
            for (RadarEntry entry : radarBe.entries) {
                Entity e = level.getEntity(entry.entityID);
                if (e != null) seen.add(e);
            }
        }
        double own = reach(ownRadarRange());
        if (own > 0) seen.addAll(level.getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(own)));
        return seen;
    }

    public @Nullable BlockEntityMachineRadar linkedRadar() {
        return radarPos == null ? null : ChunkUtil.blockEntityIfLoaded(BlockEntityMachineRadar.class, level, radarPos);
    }

    /**
     * NTM's own filters (toggles, whitelist), and only things up in the air. Missiles are taken in
     * any flight phase: NTM turrets wait for the descent, by then a SAM is too late.
     */
    protected boolean isAirTarget(Entity e) {
        if (e instanceof EntitySAM || !e.isAlive() || AirDefenseTeams.friendly(level, worldPosition, e)) return false;
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
        return super.syncUnitMask() | U_AMMO | U_RADAR | U_CELL | U_TIME | DeviceName.BIT;
    }

    @Override
    public void writeSyncUnit(int unit, ByteBuf out) {
        switch (unit) {
            case 11 -> out.writeByte(ammo);
            case 12 -> out.writeFloat(radar);
            case 13 -> out.writeByte(launchCell);
            case 14 -> out.writeLong(launchTime);
            case DeviceName.UNIT -> DeviceName.write(this, out);
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
            case DeviceName.UNIT -> DeviceName.read(this, in);
            default -> super.readSyncUnit(unit, in);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        radarPos = input.getLong("radarPos").map(BlockPos::of).orElse(null);
        manual = input.getBooleanOr("manual", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (radarPos != null) output.putLong("radarPos", radarPos.asLong());
        output.putBoolean("manual", manual);
    }

    @Override
    public Component getName() {
        return getBlockState().getBlock().getName();
    }
}
