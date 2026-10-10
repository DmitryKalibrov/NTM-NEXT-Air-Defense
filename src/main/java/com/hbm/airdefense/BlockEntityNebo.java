package com.hbm.airdefense;

import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** NTM's radar with the Nebo early-warning radar's reach; the antenna turns with NTM's radar spin. */
public class BlockEntityNebo extends BlockEntityMachineRadar {

    public BlockEntityNebo(BlockPos pos, BlockState state) {
        super(AirDefense.NEBO_BE.get(), pos, state);
    }

    @Override
    public int getRange() {
        return 5000;
    }

    /** Redstone goes out through the connector cells next to the core, as on NTM's large radar. */
    @Override
    protected void notifyRedstone() {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cell = worldPosition.relative(dir);
            level.updateNeighborsAt(cell, level.getBlockState(cell).getBlock());
        }
    }
}
