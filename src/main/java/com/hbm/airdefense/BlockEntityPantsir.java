package com.hbm.airdefense;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.items.weapon.sedna.factory.XFactoryTurret;
import com.hbm.lib.ModDamageTypes;
import com.hbm.sound.ModSounds;
import com.hbm.util.EntityDamageUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4d;
import org.jspecify.annotations.Nullable;

/**
 * Pantsir: own search radar, twelve 57E6 in two side packs and a twin 20 mm gun under each pack
 * (Goalkeeper CIWS belts, 200 rounds each). It picks the weapon per target:
 * beyond gun reach - missiles; inside it - guns, plus a missile for a fast inbound missile the guns
 * would not finish before it arrives; guns empty - missiles; inside the missile dead zone - guns only.
 * Missiles only go at a target in clear sight and up off the terrain (not one still climbing out
 * behind the trees); the guns also need clear sight.
 */
public class BlockEntityPantsir extends BlockEntitySAMBase {

    static final double GUN_RANGE = 150, MISSILE_MIN = 30, MISSILE_MIN_HEIGHT = 15;
    /** Rough gun output against an NTM missile (50 health): two guns, ~half the rounds hit. */
    static final double GUN_TICKS_TO_KILL = 40;
    static final int BELT = 200;
    /** Keeps aiming where the last target was this long after losing it, then stows. */
    static final int HOLD_TICKS = 100;
    static final double TRACER_SPEED = 25;

    // model space (OBJ): side blocks pitch about x through (y 1.65, z -0.2)
    static final double PIVOT_Y = 1.65, PIVOT_Z = -0.2;
    static final double PACK_X = 1.55, TUBE_MOUTH_Z = -1.7, MISSILE_HALF = 1.6;
    static final double[] COLS = {-0.27, 0.0, 0.27}, ROWS = {1.82, 2.1};
    static final double GUN_Y = 1.3, MUZZLE_Z = -3.1;

    private static @Nullable List<BulletConfig> configs;

    private int rounds;
    private double lastDist = -1, closing;
    private boolean clearSight;
    private int hold;

    public BlockEntityPantsir(BlockPos pos, BlockState state) {
        super(AirDefense.PANTSIR_BE.get(), pos, state);
    }

    @Override protected int capacity() { return 12; }
    @Override protected Item ammoItem() { return AirDefense.SAM_PANTSIR.get(); }
    @Override protected double range() { return 400; }
    @Override protected double ownRadarRange() { return 400; }
    @Override protected int cooldownTicks() { return 8; }
    @Override protected int salvo() { return 2; }
    @Override public double getHeightOffset() { return 1.65; }
    @Override public double getTurretYawSpeed() { return 10.0; }
    @Override public double getTurretPitchSpeed() { return 8.0; }
    @Override public double getAcceptableInaccuracy() { return 8.0; }

    @Override
    protected List<BulletConfig> getAmmoList() {
        if (configs == null) configs = List.of(XFactoryTurret.dgk_normal);
        return configs;
    }

    @Override
    public List<ItemStack> getAmmoTypesForDisplay() {
        return List.of(new ItemStack(ammoItem()), new ItemStack(ModItems.AMMO_DGK.get()));
    }

    /** Slot 1 holds the missiles, the other ammo slots take gun belts. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot > SLOT_AMMO_FIRST && slot <= SLOT_AMMO_LAST) return stack.is(ModItems.AMMO_DGK.get());
        return super.canPlaceItem(slot, stack);
    }

    // ---- aiming: one pitch for both side blocks and the tracking radar ----

    @Override
    protected void aim(Vec3 t) {
        Vec3 d = t.subtract(getTurretPos());
        double pitch = Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
        turnTowardsAngle(Math.clamp(pitch, Math.toRadians(-5), Math.toRadians(80)), -Math.atan2(d.x, d.z));
        deploySearch(true);
        hold = HOLD_TICKS;
    }

    @Override
    protected void stow() {
        if (hold > 0 && isOn() && hasPower()) {
            hold--;
            deploySearch(true);
            return;
        }
        turnTowardsAngle(0, rotationYaw);
        deploySearch(isOn() && hasPower());
    }

    /** The search radar stands up and turns while on duty, folds back over the rear otherwise. */
    private void deploySearch(boolean up) {
        setRadar(up ? Math.min(1f, radar + 0.05f) : Math.max(0f, radar - 0.05f));
    }

    // ---- weapon choice ----

    private boolean gunsReady() {
        return rounds > 0 || getFirstConfigLoaded() != null;
    }

    private double targetDist() {
        return tPos == null ? Double.MAX_VALUE : tPos.distanceTo(getTurretPos());
    }

    /** Nothing solid between the launcher (above its own cells) and the target. */
    private boolean inSight(Vec3 at) {
        Vec3 from = Vec3.atCenterOf(worldPosition).add(0, 2.5, 0);
        return level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    /** Height of the target above the terrain under it. */
    private double heightAboveGround(Vec3 at) {
        return at.y - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
    }

    /** Fire a missile at the current target? */
    private boolean wantMissile() {
        double d = targetDist();
        if (d < MISSILE_MIN || !clearSight || heightAboveGround(tPos) < MISSILE_MIN_HEIGHT) return false;
        if (d > GUN_RANGE || !gunsReady()) return true;
        // inside gun reach: only for a missile closing too fast for the guns alone
        return target instanceof EntityMissileBaseNT && closing * GUN_TICKS_TO_KILL > d - MISSILE_MIN;
    }

    @Override
    protected boolean readyToFire() {
        return tPos != null && radar >= 1f && wantMissile();
    }

    @Override
    protected EntitySAM launch(Entity target, int index) {
        int side = index < 6 ? -1 : 1, row = index % 6 / 3, col = index % 3;
        Matrix4d m = Geo.hingeX(Geo.carriage(this), PIVOT_Y, PIVOT_Z, rotationPitch);
        Vec3 pos = Geo.point(m, side * PACK_X + COLS[col], ROWS[row], TUBE_MOUTH_Z - MISSILE_HALF);
        return EntitySAM.fire(level, EntitySAM.Kind.PANTSIR, pos, Geo.dir(m, 0, 0, -1), target);
    }

    @Override
    public void tickServer() {
        if (rounds <= 0) {
            BulletConfig belt = getFirstConfigLoaded();
            if (belt != null) {
                consumeAmmo(belt);
                rounds = BELT;
                level.playSound(null, worldPosition, ModSounds.TURRET_CIWS_RELOAD.get(), SoundSource.BLOCKS, 4f, 1f);
                markChanged();
            }
        }
        super.tickServer();

        double d = targetDist();
        closing = lastDist < 0 || target == null ? 0 : Math.max(0, lastDist - d);
        lastDist = target == null ? -1 : d;
        clearSight = tPos != null && inSight(tPos);
        if (target != null && aligned && clearSight && rounds > 0 && d <= GUN_RANGE) fireGuns(d);
    }

    /** Both twin guns, a burst every tick; hits are rolled like NTM's Howard CIWS, worse with range. */
    private void fireGuns(double dist) {
        float pitchJitter = level.getRandom().nextFloat() * 0.3f;
        level.playSound(null, worldPosition, ModSounds.TURRET_HOWARD_FIRE.get(), SoundSource.BLOCKS, 5f, 0.8f + pitchJitter);
        if (level.getGameTime() % 2 != 0) return;
        rounds--;
        markChanged();
        Matrix4d m = Geo.hingeX(Geo.carriage(this), PIVOT_Y, PIVOT_Z, rotationPitch);
        double hitChance = 0.6 * (1 - 0.5 * dist / GUN_RANGE);
        Vec3 aimAt = target.position().add(0, target.getBbHeight() / 2, 0);
        for (int side = -1; side <= 1; side += 2) {
            for (double dx : new double[]{-0.1, 0.1})
                muzzleFlash(Geo.point(m, side * PACK_X + dx, GUN_Y, MUZZLE_Z), 1.2f, 1);
            // one tracer per gun and burst, scattered a little around the target
            double spread = 0.015 * dist;
            Vec3 jitter = new Vec3(level.getRandom().nextGaussian(), level.getRandom().nextGaussian(), level.getRandom().nextGaussian()).scale(spread);
            EntityTracer.fire(level, Geo.point(m, side * PACK_X, GUN_Y, MUZZLE_Z), aimAt.add(jitter), TRACER_SPEED);
            if (level.getRandom().nextDouble() < hitChance) {
                EntityDamageUtil.attackEntityFromIgnoreIFrame(target,
                        level.damageSources().source(ModDamageTypes.SHRAPNEL), 2f + level.getRandom().nextInt(2));
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SMOKE, true, false, aimAt.x, aimAt.y, aimAt.z, 3, 0.4, 0.4, 0.4, 0.02);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rounds = input.getIntOr("rounds", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("rounds", rounds);
    }
}
