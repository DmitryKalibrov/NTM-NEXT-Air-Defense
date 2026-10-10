package com.hbm.airdefense;

import com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NTM's assembly machine logic and GUI, inside the hall. Its block entity type and recipe set are
 * swapped in during NTM's constructor by {@code AssemblyMachineMixin}.
 */
public class BlockEntityDefenseAssembler extends BlockEntityMachineAssemblyMachine {

    public BlockEntityDefenseAssembler(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public Component getName() {
        return getBlockState().getBlock().getName();
    }
}
