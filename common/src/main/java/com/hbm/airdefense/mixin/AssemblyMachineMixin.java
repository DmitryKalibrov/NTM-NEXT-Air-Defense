package com.hbm.airdefense.mixin;

import com.hbm.airdefense.AirDefense;
import com.hbm.airdefense.BlockEntityDefenseAssembler;
import com.hbm.airdefense.DefenseAssemblerRecipes;
import com.hbm.airdefense.MachineDefenseAssembler;
import com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * NTM's assembly machine has a single public constructor with its type and recipe set fixed. For the
 * defence hall (a subclass) hand the base constructor our type - BlockEntity checks it against the
 * block - and give the recipe module our recipe set.
 */
@Mixin(BlockEntityMachineAssemblyMachine.class)
abstract class AssemblyMachineMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/hbm/blockentity/BlockEntityMachineBase;<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V"),
            index = 0)
    private static BlockEntityType<?> ntmad$type(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        return state.getBlock() instanceof MachineDefenseAssembler ? AirDefense.ASSEMBLER_BE.get() : type;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/hbm/modules/machine/ModuleMachineAssembler;<init>(ILcom/hbm/api/energymk2/IEnergyHandlerMK2;Lcom/hbm/inventory/recipes/loader/GenericRecipes;Lnet/minecraft/world/Container;[I[I[Lcom/hbm/inventory/fluid/tank/FluidTankNTM;[Lcom/hbm/inventory/fluid/tank/FluidTankNTM;)V"),
            index = 2)
    private GenericRecipes<?, ?> ntmad$recipes(GenericRecipes<?, ?> set) {
        return (Object) this instanceof BlockEntityDefenseAssembler ? DefenseAssemblerRecipes.INSTANCE : set;
    }
}
