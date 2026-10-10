package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** A moving 3x3 window per trader, never a force-loaded corridor between towns. */
public final class TradeChunks {
    private static final Map<ServerLevel,Map<UUID,Set<Long>>> HELD=new WeakHashMap<>();
    private static final TicketController CONTROLLER=new TicketController(Identifier.fromNamespaceAndPath(WWMC.MODID,"traders"),(level,helper) -> {
        var towns=SettlementData.get(level);
        Set<UUID> allowed=allowed(level);
        for(var entry:helper.getEntityTickets().entrySet()) {
            Settlement town=towns.byId(entry.getKey());
            if(town==null || !allowed.contains(town.id)) { helper.removeAllTickets(entry.getKey()); continue; }
            Set<Long> wanted=window(town.trading.runnerPos==null ? town.center : town.trading.runnerPos);
            Set<Long> restored=new HashSet<>();
            for(long chunk:entry.getValue().normal()) {
                if(wanted.contains(chunk)) restored.add(chunk); else helper.removeTicket(entry.getKey(),chunk,false);
            }
            for(long chunk:entry.getValue().naturalSpawning()) helper.removeTicket(entry.getKey(),chunk,true);
            HELD.computeIfAbsent(level,l -> new HashMap<>()).put(town.id,restored);
        }
    });
    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }
    /** A stranded carrier is kept ticking until it can return its goods, even after a route is removed. */
    private static boolean needed(ServerLevel level,Settlement town) { return town.trading.runner!=null || TradeRoutes.canDepart(level,town); }
    private static Set<UUID> allowed(ServerLevel level) {
        Set<UUID> result=new LinkedHashSet<>();
        // Autonomous NPC traffic uses spare capacity; it cannot fill the cap ahead of player-connected trade.
        SettlementData.get(level).settlements.stream().filter(t -> needed(level,t))
                .sorted(Comparator.comparingInt((Settlement t) -> t.trading.npc && TradeRoutes.partners(level,t).stream().allMatch(p -> p.trading.npc) ? 1 : 0)
                        .thenComparing(t -> t.id))
                .limit(Config.MAX_TRADERS.get()).forEach(t -> result.add(t.id));
        return result;
    }
    public static Set<Long> window(BlockPos pos) {
        Set<Long> result=new HashSet<>();
        int x=Math.floorDiv(pos.getX(),16),z=Math.floorDiv(pos.getZ(),16);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) result.add(new ChunkPos(x+dx,z+dz).pack());
        return result;
    }
    public static boolean keep(ServerLevel level,Settlement town,BlockPos pos) {
        if(!allowed(level).contains(town.id)) return false;
        var held=HELD.computeIfAbsent(level,l -> new HashMap<>());
        if(!held.containsKey(town.id) && held.size()>=Config.MAX_TRADERS.get()) return false;
        Set<Long> next=window(pos),old=held.getOrDefault(town.id,Set.of());
        for(long chunk:next) if(!old.contains(chunk)) { ChunkPos p=new ChunkPos((int)chunk,(int)(chunk >> 32)); CONTROLLER.forceChunk(level,town.id,p.x(),p.z(),true,false); }
        for(long chunk:old) if(!next.contains(chunk)) { ChunkPos p=new ChunkPos((int)chunk,(int)(chunk >> 32)); CONTROLLER.forceChunk(level,town.id,p.x(),p.z(),false,false); }
        held.put(town.id,next);
        return true;
    }
    public static void release(ServerLevel level,UUID town) {
        var held=HELD.get(level);
        Set<Long> old=held==null ? null : held.remove(town);
        if(old!=null) for(long chunk:old) { ChunkPos p=new ChunkPos((int)chunk,(int)(chunk >> 32)); CONTROLLER.forceChunk(level,town,p.x(),p.z(),false,false); }
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%40!=0) return;
        SettlementData data=SettlementData.get(level);
        var held=HELD.computeIfAbsent(level,l -> new HashMap<>());
        Set<UUID> allowed=allowed(level);
        for(UUID id:new ArrayList<>(held.keySet())) {
            Settlement town=data.byId(id);
            if(town==null || !allowed.contains(id)) release(level,id);
        }
        for(Settlement town:data.settlements) if(allowed.contains(town.id)) {
            if(!keep(level,town,town.trading.runnerPos==null ? town.center : town.trading.runnerPos)) town.trading.status="Waiting for a server trader slot";
        }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { HELD.keySet().removeIf(level -> level.getServer()==event.getServer()); }
}
