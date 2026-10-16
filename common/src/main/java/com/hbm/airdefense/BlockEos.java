package com.hbm.airdefense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The optoelectronic station on its tripod: the block is the tripod's footprint, the head is drawn above it. */
public class BlockEos extends BaseEntityBlock {

    private static final MapCodec<BlockEos> CODEC = simpleCodec(BlockEos::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public BlockEos(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(Camo.CAMO, Camo.GREEN).setValue(Camo.NET, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Camo.CAMO, Camo.NET);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityEos(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (l, p, s, be) -> {
            if (be instanceof BlockEntityEos e) {
                if (l.isClientSide()) e.tickClient();
                else e.tickServer();
            }
        };
    }
}
