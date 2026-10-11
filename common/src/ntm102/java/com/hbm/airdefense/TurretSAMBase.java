package com.hbm.airdefense;

import com.hbm.blocks.turret.TurretBaseArtillery;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** SAM launcher on the 4x4 artillery footprint; power and ammo through any outer cell, like NTM's artillery. */
public abstract class TurretSAMBase extends TurretBaseArtillery {

    protected TurretSAMBase(BlockBehaviour.Properties props) {
        super(props);
    }
}
