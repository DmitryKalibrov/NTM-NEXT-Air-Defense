package com.hbm.airdefense;

import com.hbm.blocks.multiblock.BlockMultiblockCore;
import com.hbm.blocks.multiblock.MultiblockHandlerXR;
import com.hbm.blocks.turret.TurretBaseArtillery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * SAM launcher on the 4x4 artillery footprint. Power comes in only through the plug modelled on the
 * rear of the platform; ammo through any cell.
 */
public abstract class TurretSAMBase extends TurretBaseArtillery {

    private static final int SOUTH = 1 << Direction.SOUTH.get3DDataValue();

    protected TurretSAMBase(BlockBehaviour.Properties props) {
        super(props);
    }

    /**
     * The block the plug is modelled on: model x -1..0, z 1..2 (rear edge, left of centre). The base
     * model is not turned with the facing, it sits at the turret's horizontal offset, so neither is this.
     */
    static BlockPos plugCell(BlockPos core, Direction facing) {
        int ox = facing == Direction.NORTH || facing == Direction.WEST ? 1 : 0;
        int oz = facing == Direction.NORTH || facing == Direction.EAST ? 1 : 0;
        return core.offset(ox - 1, 0, oz + 1);
    }

    @Override
    protected void visitCells(BlockPos core, Direction facing, BlockMultiblockCore.CellVisitor visitor) {
        MultiblockHandlerXR.visitBox(core, getDimensions(), facing, visitor);
        visitor.cell(plugCell(core, facing), SOUTH);
    }

    @Override
    protected void visitPassiveCells(BlockPos core, Direction facing, BlockMultiblockCore.PassiveCellVisitor visitor) {
        BlockPos plug = plugCell(core, facing);
        MultiblockHandlerXR.visitBox(core, getDimensions(), facing,
                (pos, dir) -> visitor.passiveCell(pos.immutable(), 63, pos.equals(plug) ? 5 : 4));
    }
}
