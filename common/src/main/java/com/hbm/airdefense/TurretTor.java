package com.hbm.airdefense;

import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Tor combat module (Tor-M2KM style, full size) on the 4x4 footprint. */
public class TurretTor extends TurretSAMBase {

    public TurretTor(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MachineCaps caps() {
        return MachineCaps.of(AirDefense.TOR_BE).powerIn().itemsAtCells();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityTor(pos, state);
    }
}
