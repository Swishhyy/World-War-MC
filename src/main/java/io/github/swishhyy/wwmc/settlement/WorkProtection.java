package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Global placement provenance protects buildings even before a town is founded. */
public final class WorkProtection {
    private record Change(BlockPos pos,Block placed,boolean alreadyProtected) {}
    private record Move(BlockPos source,BlockPos target,Block block,boolean alreadyProtected,long until) {}
    private final Map<ServerLevel,List<Change>> pending=new WeakHashMap<>();
    private final Map<ServerLevel,List<Move>> moving=new WeakHashMap<>();
    private void placed(ServerLevel level,BlockPos pos,Block block) {
        // Saplings and crops are intentionally eligible for later forestry/farming.
        if(block instanceof SaplingBlock || block instanceof CropBlock || block==Blocks.SUGAR_CANE || block==Blocks.BAMBOO) return;
        WorldWorkData data=WorldWorkData.get(level);
        boolean alreadyProtected=data.protectedBlocks.contains(pos);
        data.protect(pos); // Visible to workers immediately, even earlier in this server tick.
        pending.computeIfAbsent(level,l -> new ArrayList<>()).add(new Change(pos.immutable(),block,alreadyProtected));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void place(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player)) return;
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            boolean grewSapling=multi.getReplacedBlockSnapshots().stream().anyMatch(s -> s.getState().getBlock() instanceof SaplingBlock)
                    && multi.getReplacedBlockSnapshots().stream().anyMatch(s -> s.getCurrentState().getBlock() instanceof LeavesBlock && !s.getCurrentState().getValue(LeavesBlock.PERSISTENT));
            if(grewSapling) return; // Bone-meal tree growth is forestry, not log construction.
            for(var snapshot:multi.getReplacedBlockSnapshots()) placed(level,snapshot.getPos(),snapshot.getCurrentState().getBlock());
        } else placed(level,event.getPos(),event.getPlacedBlock().getBlock());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void broken(BreakBlockEvent event) {
        if(event.getLevel() instanceof ServerLevel level)
            pending.computeIfAbsent(level,l -> new ArrayList<>()).add(new Change(event.getPos().immutable(),null,true));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void piston(PistonEvent.Pre event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        var resolver=event.getStructureHelper();
        if(resolver==null || !resolver.resolve()) return;
        WorldWorkData data=WorldWorkData.get(level);
        var direction=event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        // Snapshot sources before protecting destinations that may themselves be sources.
        Set<BlockPos> sources=new HashSet<>();
        for(BlockPos source:resolver.getToPush()) if(data.protectedBlocks.contains(source)) sources.add(source.immutable());
        for(BlockPos source:sources) {
            BlockPos target=source.relative(direction);
            boolean already=data.protectedBlocks.contains(target);
            data.protect(target);
            moving.computeIfAbsent(level,l -> new ArrayList<>()).add(new Move(source.immutable(),target,level.getBlockState(source).getBlock(),already,level.getGameTime()+10));
        }
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        List<Change> changes=pending.remove(level);
        if(changes==null && !moving.containsKey(level)) return;
        WorldWorkData data=WorldWorkData.get(level);
        for(Change change:changes==null ? List.<Change>of() : changes) {
            if(!level.hasChunkAt(change.pos())) continue;
            var state=level.getBlockState(change.pos());
            // Reconcile after all placement/break listeners have had a chance to cancel.
            if(change.placed()==null) {
                if(state.isAir() && data.protectedBlocks.remove(change.pos())) data.setDirty();
            } else if(state.is(change.placed())) data.protect(change.pos());
            else if(!change.alreadyProtected() && data.protectedBlocks.remove(change.pos())) data.setDirty();
        }
        var moves=moving.get(level);
        if(moves!=null) {
            moves.removeIf(move -> {
                if(!level.hasChunkAt(move.target())) return level.getGameTime()>=move.until();
                var state=level.getBlockState(move.target());
                if(state.is(Blocks.MOVING_PISTON) && level.getGameTime()<move.until()) return false;
                if(state.is(move.block())) {
                    data.protect(move.target());
                    if(level.hasChunkAt(move.source()) && level.getBlockState(move.source()).isAir() && data.protectedBlocks.remove(move.source())) data.setDirty();
                } else if(!move.alreadyProtected() && data.protectedBlocks.remove(move.target())) data.setDirty();
                return true;
            });
            if(moves.isEmpty()) moving.remove(level);
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        pending.keySet().removeIf(l -> l.getServer()==event.getServer());
        moving.keySet().removeIf(l -> l.getServer()==event.getServer());
    }
}
