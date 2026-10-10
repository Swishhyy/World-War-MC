package io.github.swishhyy.wwmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Early blacksmith furniture: slower work, with the same wear stages as an iron anvil. */
public final class BronzeAnvilBlock extends Block {
    // Retain all old values so placed anvils and dropped items keep their saved condition.
    public static final IntegerProperty WEAR=IntegerProperty.create("wear",0,11);
    public BronzeAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AnvilBlock.FACING,Direction.NORTH).setValue(WEAR,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(AnvilBlock.FACING,WEAR);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AnvilBlock.FACING,context.getHorizontalDirection().getClockWise());
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        return state.setValue(AnvilBlock.FACING,rotation.rotate(state.getValue(AnvilBlock.FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING,state.getValue(AnvilBlock.FACING)).getShape(level,pos,context);
    }
}
