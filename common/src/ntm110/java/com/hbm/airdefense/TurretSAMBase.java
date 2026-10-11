package com.hbm.airdefense;

import com.hbm.blocks.multiblock.MultiblockHandlerXR;
import com.hbm.blocks.multiblock.MultiblockLayout;
import com.hbm.blocks.turret.TurretBaseArtillery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * SAM launcher on the 4x4 artillery footprint. Power comes in only through the plug modelled on the
 * rear of the platform; ammo through any cell. NTM 1.1.0 variant: the layout is built at runtime.
 */
public abstract class TurretSAMBase extends TurretBaseArtillery {

    /** NTM's artillery footprint: up, down, north, south, west, east. */
    private static final int[] DIMENSIONS = {1, 0, 2, 1, 2, 1};
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
    protected MultiblockLayout runtimeLayout(int caps) {
        // the plug keeps its world position whatever way the launcher faces: one table per facing
        return LegacyLayout.bake(caps, DIMENSIONS, 1, true, new LegacyLayout.Shape() {
            @Override
            public void cells(BlockPos core, Direction facing, LegacyLayout.Cells out) {
                out.cell(plugCell(core, facing), SOUTH);
            }

            @Override
            public void passives(BlockPos core, Direction facing, LegacyLayout.Passives out) {
                BlockPos plug = plugCell(core, facing);
                MultiblockHandlerXR.visitBox(core, DIMENSIONS, facing,
                        (pos, dir) -> out.passive(pos.immutable(), 63, pos.equals(plug) ? 5 : 4));
            }
        });
    }
}
