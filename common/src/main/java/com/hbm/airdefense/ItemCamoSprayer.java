package com.hbm.airdefense;

import com.hbm.blocks.multiblock.MultiblockSurface;
import com.hbm.items.IItemControlReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Camouflage spray gun: shift + use in the air picks what it applies (a paint scheme, or "net"), use on any block of
 * a launcher, the Nebo or an optoelectronic station paints it or throws the vegetation net on / pulls it off.
 */
public class ItemCamoSprayer extends Item implements IItemControlReceiver {

    /** What the gun can apply: the schemes by name, then the net. */
    public static final String NET = "net";

    public ItemCamoSprayer(Properties props) {
        super(props);
    }

    public static String selected(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("camo", "green");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) com.hbm.airdefense.client.ScreenCamo.open(selected(player.getItemInHand(hand)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos core = MultiblockSurface.coreOfAny(level, ctx.getClickedPos());
        if (core == null) core = ctx.getClickedPos();
        BlockState state = level.getBlockState(core);
        if (!state.hasProperty(Camo.CAMO)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            String sel = selected(ctx.getItemInHand());
            Player player = ctx.getPlayer();
            ParticleOptions puff;
            if (sel.equals(NET)) {
                boolean on = !state.getValue(Camo.NET);
                level.setBlock(core, state.setValue(Camo.NET, on), 3);
                level.playSound(null, core, on ? SoundEvents.AZALEA_LEAVES_PLACE : SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
                puff = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState());
                if (player != null) player.sendOverlayMessage(Component.translatable("gui.ntm_air_defense.camo.net_" + (on ? "on" : "off")));
            } else {
                Camo camo = Camo.valueOf(sel.toUpperCase(java.util.Locale.ROOT));
                level.setBlock(core, state.setValue(Camo.CAMO, camo), 3);
                level.playSound(null, core, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.6f);
                puff = new DustParticleOptions(switch (camo) {
                    case GREEN -> 0x55623E;
                    case DESERT -> 0xB49A6C;
                    case WINTER -> 0xE4E8E6;
                }, 2f);
            }
            var hit = ctx.getClickLocation();
            server.sendParticles(puff, hit.x, hit.y, hit.z, 24, 0.4, 0.4, 0.4, 0.05);
        }
        return InteractionResult.SUCCESS;
    }

    /** From the client's picker: "camo" = a scheme name or "net". */
    @Override
    public void receiveControl(ServerPlayer player, ItemStack stack, CompoundTag data) {
        String sel = data.getStringOr("camo", "");
        boolean known = sel.equals(NET);
        for (Camo c : Camo.values()) known |= c.getSerializedName().equals(sel);
        if (known) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("camo", sel));
    }
}
