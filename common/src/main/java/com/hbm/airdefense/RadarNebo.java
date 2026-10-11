package com.hbm.airdefense;

import com.hbm.blocks.machine.MachineRadarLarge;
import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Nebo early-warning radar: NTM's large radar as a 5x5 site (pad, shelter, lattice mast) with a turning Yagi array, same GUI, linker and power hookup. */
public class RadarNebo extends MachineRadarLarge {

    private static final int[] DIMENSIONS = {3, 0, 2, 2, 2, 2};

    public RadarNebo(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public int[] getDimensions() {
        return DIMENSIONS;
    }

    @Override
    public int getOffset() {
        return 2;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityNebo(pos, state);
    }

    @Override
    public MachineCaps caps() {
        return MachineCaps.of(AirDefense.NEBO_BE).powerIn().fe();
    }
}
