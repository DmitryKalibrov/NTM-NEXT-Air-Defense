package com.hbm.airdefense;

import com.hbm.blocks.machine.MachineRadarLarge;
import com.hbm.blocks.multiblock.MultiblockLayout;
import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Nebo early-warning radar: NTM's large radar as a 5x5 site (pad, shelter, lattice mast) with a turning Yagi array, same GUI, linker and power hookup. */
public class RadarNebo extends MachineRadarLarge {

    private static final int[] DIMENSIONS = {3, 0, 2, 2, 2, 2};

    public RadarNebo(BlockBehaviour.Properties props) {
        super(props);
    }

    /** NTM 1.0.3+: the large radar's power ports (the four cells around the core) on the Nebo's 5x5 box. */
    @Override
    protected MultiblockLayout runtimeLayout(int caps) {
        return LegacyLayout.bake(caps, DIMENSIONS, 2, false, new LegacyLayout.Shape() {
            @Override
            public void cells(BlockPos core, Direction facing, LegacyLayout.Cells out) {
                out.cell(core.east(), 32, 1, 1);
                out.cell(core.west(), 16, 1, 1);
                out.cell(core.south(), 8, 1, 1);
                out.cell(core.north(), 4, 1, 1);
            }

            @Override
            public void passives(BlockPos core, Direction facing, LegacyLayout.Passives out) {
                for (Direction d : Direction.Plane.HORIZONTAL) out.passive(core.relative(d), 63, 1);
            }
        });
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
