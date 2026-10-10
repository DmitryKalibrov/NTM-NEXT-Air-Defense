package com.hbm.airdefense.mixin;

import com.hbm.airdefense.BlockEntitySAMBase;
import com.hbm.blockentity.machine.BlockEntityMachineRadar;
import com.hbm.blocks.multiblock.MultiblockSurface;
import com.hbm.items.ModDataComponents;
import com.hbm.items.tool.ItemRadarLinker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NTM's radar linker, extended: click a radar to remember it, then shift-click a SAM launcher to
 * hook it to that radar's scan. Any number of launchers can share one radar. A plain click on a
 * launcher keeps NTM's behaviour (it is a radar command receiver, like artillery).
 */
@Mixin(ItemRadarLinker.class)
abstract class RadarLinkerMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void ntmad$link(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = ctx.getLevel();
        BlockPos core = MultiblockSurface.coreOfAny(level, ctx.getClickedPos());
        if (core == null) core = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(core);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (be instanceof BlockEntityMachineRadar) {
            if (!level.isClientSide()) {
                stack.set(ModDataComponents.RADAR_LINKER_TARGET.get(), core.asLong());
                tell(player, Component.translatable("ntm_air_defense.linker.radar", core.toShortString()));
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
        } else if (be instanceof BlockEntitySAMBase sam && player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                BlockPos radar = ItemRadarLinker.getPosition(stack);
                if (radar != null && level.getBlockEntity(radar) instanceof BlockEntityMachineRadar) {
                    sam.linkRadar(radar);
                    tell(player, Component.translatable("ntm_air_defense.linker.linked", radar.toShortString()));
                } else {
                    tell(player, Component.translatable("ntm_air_defense.linker.no_radar"));
                }
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    private static void tell(Player player, Component msg) {
        if (player != null) player.sendSystemMessage(msg);
    }
}
