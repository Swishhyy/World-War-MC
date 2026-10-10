package io.github.swishhyy.wwmc.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Surface building materials and renewable paper supplies. Construction and plant bases stay intact. */
public final class Gathering {
    private Gathering() {}
    public static boolean plant(BlockState state) { return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO); }
    public static boolean material(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY);
    }
    public static boolean harvestable(ServerLevel level,Settlement town,BlockPos pos) {
        if(!town.contains(pos) || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.above())
                || level.getBlockEntity(pos)!=null || SettlementService.protectedFurniture(town,pos)) return false;
        BlockState state=level.getBlockState(pos);
        if(plant(state)) return level.getBlockState(pos.below()).is(state.getBlock())
                && !level.getBlockState(pos.below(2)).is(state.getBlock()) && column(level,town,pos)!=null;
        if(!material(state) || WorldWorkData.get(level).protectedBlocks.contains(pos)
                || !level.getBlockState(pos.above()).canBeReplaced() || !level.getFluidState(pos).isEmpty()) return false;
        for(Direction direction:Direction.values()) {
            BlockPos other=pos.relative(direction);
            if(!level.hasChunkAt(other) || !level.getFluidState(other).isEmpty()) return false;
        }
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(pos).expandTowards(0,2,0)).isEmpty();
    }
    private static List<BlockPos> column(ServerLevel level,Settlement town,BlockPos first) {
        var block=level.getBlockState(first).getBlock(); var column=new ArrayList<BlockPos>();
        for(int y=0;y<32;y++) {
            BlockPos pos=first.above(y);
            if(!town.contains(pos) || !level.hasChunkAt(pos)) return null;
            if(!level.getBlockState(pos).is(block)) return column;
            column.add(pos);
        }
        return null;
    }
    public record Harvest(List<ItemStack> drops,int blocks) {}
    /** Cut tall crops from the top down to keep every actual drop and leave the planted base. */
    public static Harvest cut(ServerLevel level,Settlement town,BlockPos first,LivingEntity worker) {
        if(!plant(level.getBlockState(first)) || !harvestable(level,town,first)) return null;
        var column=column(level,town,first); ItemStack tool=worker.getMainHandItem();
        if(column==null || column.isEmpty() || tool.isDamageableItem() && tool.getMaxDamage()-tool.getDamageValue()<column.size()) return null;
        var drops=new ArrayList<ItemStack>(); int cut=0;
        for(BlockPos pos:column.reversed()) {
            var state=level.getBlockState(pos);
            var harvested=Block.getDrops(state,level,pos,null,worker,tool);
            if(!level.destroyBlock(pos,false,worker)) break;
            drops.addAll(harvested); cut++;
        }
        return new Harvest(drops,cut);
    }

}
