package io.github.swishhyy.wwmc.block;

import com.mojang.serialization.MapCodec;
import io.github.swishhyy.wwmc.WWMC;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Familiar furnace placement, inventory drops and comparator behavior, with two alloy inputs. */
public final class AlloyFurnaceBlock extends AbstractFurnaceBlock {
    private static final MapCodec<AlloyFurnaceBlock> CODEC=simpleCodec(AlloyFurnaceBlock::new);
    public AlloyFurnaceBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<AlloyFurnaceBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new AlloyFurnaceEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,WWMC.ALLOY_FURNACE_ENTITY.get(),AlloyFurnaceEntity::tick);
    }
    @Override protected void openContainer(Level level,BlockPos pos,Player player) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof AlloyFurnaceEntity furnace)
            server.openMenu(furnace);
    }
}
