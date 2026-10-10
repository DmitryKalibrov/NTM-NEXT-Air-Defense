package com.hbm.airdefense;

import com.hbm.entity.missile.EntityMissileTier1;
import com.hbm.items.ModItems;
import com.hbm.particle.helper.ExplosionCreator;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Practice target: a real NTM tier-1 (V-2) missile as far as radars and SAMs care, but its impact
 * is only the visual puff - no blast, no crater, no debris when shot down.
 */
public class EntityTargetDrone extends EntityMissileTier1 {

    public EntityTargetDrone(EntityType<? extends EntityTargetDrone> type, Level level) {
        super(type, level);
    }

    @Override
    public void onMissileImpact(BlockHitResult hit) {
        ExplosionCreator.composeEffectSmall(level(), getX(), getY(), getZ());
    }

    @Override
    public List<ItemStack> getDebris() {
        return List.of();
    }

    @Override
    public ItemStack getMissileItemForInfo() {
        return new ItemStack(ModItems.MISSILE_GENERIC);
    }
}
