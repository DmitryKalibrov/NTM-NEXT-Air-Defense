package com.hbm.airdefense;

import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Buk launcher (9A310 turret, full size) on the 4x4 footprint. */
public class TurretBuk extends TurretSAMBase {

    public TurretBuk(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MachineCaps caps() {
        return MachineCaps.of(AirDefense.BUK_BE).powerIn().itemsAtCells();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityBuk(pos, state);
    }
}
