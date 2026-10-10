package com.hbm.airdefense;

import com.hbm.blocks.turret.TurretBaseArtillery;
import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Buk launcher: the 4x4 artillery footprint, same as NTM's HIMARS. */
public class TurretBuk extends TurretBaseArtillery {

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
