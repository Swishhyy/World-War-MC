package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.Tags;

/** Block matching is shared by live scanning and station inspection. */
public final class StationDetection {
    private StationDetection() {}
    public static boolean storageBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
    }
    public static boolean completeBed(BlockState head, BlockState foot) {
        return head.getBlock() instanceof BedBlock && foot.is(head.getBlock())
                && head.getValue(BedBlock.PART)==BedPart.HEAD && foot.getValue(BedBlock.PART)==BedPart.FOOT
                && head.getValue(BedBlock.FACING)==foot.getValue(BedBlock.FACING);
    }
    public static boolean processingBlock(StructureRole role,BlockState state) {
        return switch(role) {
            case SMELTERY -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(io.github.swishhyy.wwmc.WWMC.ALLOY_FURNACE.get());
            case COOK -> state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
            default -> false;
        };
    }
    public static boolean anvil(BlockState state) { return state.is(BlockTags.ANVIL) || state.getBlock() instanceof io.github.swishhyy.wwmc.block.BronzeAnvilBlock; }
    public static boolean enchantingTable(BlockState state) { return state.getBlock() instanceof EnchantingTableBlock; }
    public static boolean workBlock(StructureRole role, BlockState state) {
        return switch(role) {
            case FARM -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                    && (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS));
            case LUMBER -> state.is(BlockTags.LOGS);
            case MINE -> state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES);
            default -> false;
        };
    }
}
