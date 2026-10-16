package com.hbm.airdefense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A control room module: console desk, wall panel or tilted top panel. Neighbours of the same kind and facing
 * merge into one console (the renderer drops the inner cheeks, plinths and cornices). FACING is the front, where
 * the operator stands. LIT: a desk lamp on it is switched on.
 */
public class BlockConsole extends HorizontalDirectionalBlock implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public final ConsoleGeometry.Kind kind;
    private final MapCodec<BlockConsole> codec;

    public BlockConsole(ConsoleGeometry.Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.codec = simpleCodec(p -> new BlockConsole(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player p && level.getBlockEntity(pos) instanceof BlockEntityConsole be) be.owner = p.getUUID();
    }

    /** Only the owner takes a console apart; explosions still can. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BlockEntityConsole be && !be.hasPermission(player)) return 0;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        ConsoleGeometry.Corner c = ConsoleGeometry.corner(level, pos, kind);
        return c != null ? ConsoleGeometry.cornerShape(kind, c) : ConsoleGeometry.shape(kind, state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityConsole(pos, state);
    }

    /** Server tick: polls the devices bound to its screens (a plain block has no ticker unless it hands one out). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return (l, p, s, be) -> {
            if (be instanceof BlockEntityConsole c) c.tickClient();
        };
        return (l, p, s, be) -> {
            if (be instanceof BlockEntityConsole c) c.tickServer();
        };
    }
}
