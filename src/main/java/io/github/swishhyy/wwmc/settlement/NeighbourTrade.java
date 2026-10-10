package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** NPC neighbours exchange real warehouse surplus on saved routes, using their existing trader. */
public final class NeighbourTrade {
    private NeighbourTrade() {}
    public static int baseTarget(Settlement town,String item) {
        if(!town.trading.npc) return 0;
        return switch(item) {
            case "minecraft:bread" -> 16;
            case "minecraft:carrot" -> town.trading.specialty.equals("farming") ? 32 : 64;
            case "minecraft:oak_log" -> 32;
            case "minecraft:iron_ingot", "minecraft:coal" -> 16;
            default -> 0;
        };
    }
    public static void refreshRequests(Settlement town) {
        for(String item:List.of("minecraft:bread","minecraft:carrot","minecraft:oak_log","minecraft:iron_ingot","minecraft:coal"))
            town.campaign.requests.putIfAbsent(item,baseTarget(town,item));
    }
    public static List<Settlement> nearby(ServerLevel level,Settlement town) {
        double limit=(double)Config.TRADE_DISTANCE.get()*Config.TRADE_DISTANCE.get();
        return SettlementData.get(level).settlements.stream().filter(t -> t!=town && t.center.distSqr(town.center)<=limit)
                .sorted(Comparator.comparingDouble(t -> t.center.distSqr(town.center))).limit(40).toList();
    }
    private static boolean ready(ServerLevel level,Settlement town) {
        var checkpoint=TradeRoutes.checkpoint(town);
        return town.trading.npc && town.trading.buildIndex<0 && !town.trading.paused && checkpoint!=null
                && level.hasChunkAt(town.center) && level.isPositionEntityTicking(town.center)
                && level.hasChunkAt(checkpoint.position()) && SettlementService.active(level,checkpoint);
    }
    /** One NPC per existing campaign check; at most two neighbour routes leave its player-facing primary route free. */
    public static void update(ServerLevel level,Settlement town) {
        if(!ready(level,town)) return;
        var towns=SettlementData.get(level);
        if(town.campaign.extraRoutes.removeIf(id -> towns.byId(id)==null)) towns.setDirty();
        if(town.campaign.extraRoutes.size()>=2) return;
        Settlement other=nearby(level,town).stream().filter(t -> ready(level,t) && t.campaign.extraRoutes.size()<2
                && !TradeRoutes.agreed(town,t) && town.trading.relations.getOrDefault(t.owner,0)>=0 && t.trading.relations.getOrDefault(town.owner,0)>=0)
                .min(Comparator.comparingInt((Settlement t) -> town.trading.specialty.equals(t.trading.specialty) ? 1 : 0)
                        .thenComparingDouble(t -> t.center.distSqr(town.center))).orElse(null);
        if(other==null) return;
        refreshRequests(town); refreshRequests(other);
        town.campaign.extraRoutes.add(other.id); other.campaign.extraRoutes.add(town.id);
        CampaignService.journal(level,town,"Arranged neighbour trade with "+other.name+". Our trader carries surplus between the warehouses.");
        CampaignService.journal(level,other,"Arranged neighbour trade with "+town.name+". Our trader carries surplus between the warehouses.");
        WWMC.LOGGER.info("[WWMC] [neighbours] NPC trade route established: {} <-> {}",town.id,other.id);
    }
}
