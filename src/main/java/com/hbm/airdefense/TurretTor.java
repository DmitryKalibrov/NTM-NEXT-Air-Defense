package com.hbm.airdefense;

import com.hbm.blocks.multiblock.BlockMultiblockCore;
import com.hbm.blocks.turret.TurretBaseNT;
import com.hbm.capability.MachineCaps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Tor module: the 2x2 turret footprint, same as NTM's Chekhov. */
public class TurretTor extends TurretBaseNT {

    public TurretTor(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public MachineCaps caps() {
        return MachineCaps.of(AirDefense.TOR_BE).powerIn().itemsAtCells();
    }

    @Override
    protected void visitPassiveCells(BlockPos core, Direction facing, BlockMultiblockCore.PassiveCellVisitor visitor) {
        passiveEverywhere(core, facing, visitor, 5);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityTor(pos, state);
    }
}
