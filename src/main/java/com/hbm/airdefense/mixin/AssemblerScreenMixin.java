package com.hbm.airdefense.mixin;

import com.hbm.airdefense.BlockEntityDefenseAssembler;
import com.hbm.airdefense.DefenseAssemblerRecipes;
import com.hbm.client.gui.ScreenMachineAssemblyMachine;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The assembly machine screen opens its recipe picker on NTM's set; in the defence hall, on ours. */
@Mixin(ScreenMachineAssemblyMachine.class)
abstract class AssemblerScreenMixin {

    @ModifyArg(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/hbm/client/gui/GUIScreenRecipeSelector;openSelector(Lcom/hbm/inventory/recipes/loader/GenericRecipes;Lnet/minecraft/core/BlockPos;Ljava/lang/String;ILjava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V"),
            index = 0)
    private GenericRecipes<?, ?> ntmad$recipes(GenericRecipes<?, ?> set, BlockPos pos, String current, int index, String pool, Screen previous) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof BlockEntityDefenseAssembler ? DefenseAssemblerRecipes.INSTANCE : set;
    }
}
