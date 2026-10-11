package com.hbm.airdefense;

import com.hbm.blocks.machine.MachineAssemblyMachine;
import com.hbm.blocks.multiblock.MultiblockLayout;
import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Defence assembly hall: NTM's assembly machine on a 9x9x7 footprint, with its own recipe set (the
 * launchers). Power, items and fluids connect along the whole ground-level rim. NTM 1.0.3+ variant:
 * the layout is built at runtime.
 */
public class MachineDefenseAssembler extends MachineAssemblyMachine {

    private static final int[] DIMENSIONS = {6, 0, 4, 4, 4, 4};
    private static final int R = 4;

    public MachineDefenseAssembler(BlockBehaviour.Properties props) {
        super(props);
    }

    /** Outward faces of a ground-level rim cell, 0 inside the rim. */
    private static int rimMask(int i, int j) {
        return (i == -R ? bit(Direction.WEST) : 0) | (i == R ? bit(Direction.EAST) : 0)
                | (j == -R ? bit(Direction.NORTH) : 0) | (j == R ? bit(Direction.SOUTH) : 0);
    }

    private static int bit(Direction d) {
        return 1 << d.get3DDataValue();
    }

    @Override
    protected MultiblockLayout runtimeLayout(int caps) {
        return LegacyLayout.bake(caps, DIMENSIONS, R, false, new LegacyLayout.Shape() {
            @Override
            public void cells(BlockPos core, Direction facing, LegacyLayout.Cells out) {
                for (int i = -R; i <= R; i++)
                    for (int j = -R; j <= R; j++)
                        if (rimMask(i, j) != 0) out.cell(core.offset(i, 0, j), rimMask(i, j));
            }

            @Override
            public void passives(BlockPos core, Direction facing, LegacyLayout.Passives out) {
                for (int i = -R; i <= R; i++)
                    for (int j = -R; j <= R; j++)
                        if (rimMask(i, j) != 0) out.passive(core.offset(i, 0, j), 63, 7);
            }
        });
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityDefenseAssembler(pos, state);
    }

    @Override
    public MachineCaps caps() {
        return MachineCaps.of(AirDefense.ASSEMBLER_BE).powerIn().fluidIn().fluidOut().items().itemsAtCells().fe();
    }
}
