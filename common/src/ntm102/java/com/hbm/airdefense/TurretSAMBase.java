package com.hbm.airdefense;

import com.hbm.blocks.turret.TurretBaseArtillery;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

/** SAM launcher on the 4x4 artillery footprint; power and ammo through any outer cell, like NTM's artillery. */
public abstract class TurretSAMBase extends TurretBaseArtillery {

    protected TurretSAMBase(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(Camo.CAMO, Camo.GREEN).setValue(Camo.NET, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Camo.CAMO, Camo.NET);
    }
}
