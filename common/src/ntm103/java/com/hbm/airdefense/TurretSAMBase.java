package com.hbm.airdefense;

import com.hbm.blocks.multiblock.MultiblockHandlerXR;
import com.hbm.blocks.multiblock.MultiblockLayout;
import com.hbm.blocks.multiblock.MultiblockMaskTable;
import com.hbm.blocks.turret.TurretBaseArtillery;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

/**
 * SAM launcher on the 4x4 artillery footprint; power and ammo through any outer cell, like NTM's
 * artillery. NTM 1.0.3+ variant: the layout is built at runtime, same as NTM bakes turret_arty.
 */
public abstract class TurretSAMBase extends TurretBaseArtillery {

    /** NTM's artillery footprint: up, down, north, south, west, east. */
    private static final int[] DIMENSIONS = {1, 0, 2, 1, 2, 1};

    protected TurretSAMBase(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(Camo.CAMO, Camo.GREEN).setValue(Camo.NET, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Camo.CAMO, Camo.NET);
    }

    @Override
    protected MultiblockLayout runtimeLayout(int caps) {
        return LegacyLayout.bake(caps, DIMENSIONS, 1, false, new LegacyLayout.Shape() {
            @Override
            public void cells(BlockPos core, Direction f, LegacyLayout.Cells out) {
                // every outer cell gets ports on its outward faces
                for (int y = 0; y <= 1; y++)
                    for (int x = -2; x <= 1; x++)
                        for (int z = -2; z <= 1; z++) {
                            int m = (x == -2 ? 16 : 0) | (x == 1 ? 32 : 0) | (z == -2 ? 4 : 0) | (z == 1 ? 8 : 0);
                            if (m != 0)
                                out.cell(core.offset(MultiblockMaskTable.worldX(x, y, z, f), y, MultiblockMaskTable.worldZ(x, y, z, f)), m);
                        }
            }

            @Override
            public void passives(BlockPos core, Direction f, LegacyLayout.Passives out) {
                Set<BlockPos> all = new LinkedHashSet<>();
                MultiblockHandlerXR.visitBox(core, DIMENSIONS, f, (p, d) -> all.add(p.immutable()));
                cells(core, f, (p, m, r, k) -> all.add(p.immutable()));
                for (BlockPos p : all) out.passive(p, 63, 5);
            }
        });
    }
}
