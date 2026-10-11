package com.hbm.airdefense;

import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Pantsir-S1 combat module (full size) on the 4x4 footprint. */
public class TurretPantsir extends TurretSAMBase {

    public TurretPantsir(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MachineCaps caps() {
        return MachineCaps.of(AirDefense.PANTSIR_BE).powerIn().itemsAtCells();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityPantsir(pos, state);
    }
}
