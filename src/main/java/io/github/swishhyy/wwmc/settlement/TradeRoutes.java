package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Each town has one checkpoint and one partner; cross-owner links require reciprocal consent. */
public final class TradeRoutes {
    private TradeRoutes() {}
    public static Station checkpoint(Settlement town) {
        return town==null ? null : town.stations.stream().filter(s -> s.role()==StructureRole.TRADER).findFirst().orElse(null);
    }
    public static boolean uniqueCheckpoint(Settlement town,net.minecraft.core.BlockPos pos) {
        return town.stations.stream().noneMatch(s -> s.role()==StructureRole.TRADER && !s.position().equals(pos));
    }
    public static boolean agreed(Settlement a,Settlement b) {
        return a!=null && b!=null && a!=b && (b.id.equals(a.trading.partner) && a.id.equals(b.trading.partner)
                || (TownAccess.allied(a,b) || a.trading.npc && b.trading.npc
                    && a.trading.relations.getOrDefault(b.owner,0)>=0 && b.trading.relations.getOrDefault(a.owner,0)>=0)
                    && a.campaign.extraRoutes.contains(b.id) && b.campaign.extraRoutes.contains(a.id));
    }
    public static Settlement partner(ServerLevel level,Settlement town) {
        List<Settlement> partners=partners(level,town);
        return partners.isEmpty() ? null : partners.get(Math.floorMod(town.campaign.routeCursor,partners.size()));
    }
    public static List<Settlement> partners(ServerLevel level,Settlement town) {
        var data=SettlementData.get(level); java.util.Set<UUID> ids=new java.util.LinkedHashSet<>();
        if(town.trading.partner!=null) ids.add(town.trading.partner); ids.addAll(town.campaign.extraRoutes);
        return ids.stream().map(data::byId).filter(other -> agreed(town,other)).toList();
    }
    public static boolean canDepart(ServerLevel level,Settlement town) {
        Settlement other=partner(level,town);
        return canDepart(level,town,other);
    }
    public static boolean canDepart(ServerLevel level,Settlement town,Settlement other) {
        return agreed(town,other) && !town.trading.paused && !other.trading.paused
                && usable(level,checkpoint(town)) && usable(level,checkpoint(other));
    }
    private static boolean usable(ServerLevel level,Station station) { return station!=null && (!level.hasChunkAt(station.position()) || SettlementService.active(level,station)); }
    /** A freshly broken block stops counting immediately, so its replacement need not wait for periodic cleanup. */
    public static boolean uniqueCheckpoint(ServerLevel level,Settlement town,net.minecraft.core.BlockPos pos) {
        if(town.stations.removeIf(s -> s.role()==StructureRole.TRADER && level.hasChunkAt(s.position()) && !SettlementService.active(level,s))) SettlementData.get(level).setDirty();
        return uniqueCheckpoint(town,pos);
    }
    /** NPCs accept a free route; another player confirms it at their own checkpoint. */
    public static String link(Settlement source,Settlement destination,List<Settlement> towns,int maxDistance) {
        if(destination.trading.npc && destination.trading.relations.getOrDefault(source.owner,0)<0) return "That town is hostile to you after an attack and refuses trade.";
        if(agreed(source,destination)) return "Already connected to "+destination.name+".";
        if(source==destination || checkpoint(source)==null || checkpoint(destination)==null) return "Both towns need their own Trader Block.";
        if(source.center.distSqr(destination.center)>(double)maxDistance*maxDistance) return "That town is beyond the trade route distance limit.";
        if(destination.trading.partner!=null && !destination.trading.partner.equals(source.id)) return "That town already has a route or proposal. Its owner must disconnect it first.";
        disconnect(source,towns);
        source.trading.partner=destination.id;
        if(source.owner.equals(destination.owner) || destination.trading.npc) destination.trading.partner=source.id;
        source.trading.paused=false;
        return agreed(source,destination) ? "Route connected to "+destination.name+"." : "Proposed to "+destination.name+". Its owner must select your town to accept.";
    }
    public static void attacked(Settlement town,UUID attacker,List<Settlement> towns) {
        if(!town.trading.npc || town.trading.relations.getOrDefault(attacker,0)<0) return;
        town.trading.relations.put(attacker,-100);
        Settlement partner=towns.stream().filter(t -> t.id.equals(town.trading.partner)).findFirst().orElse(null);
        if(partner!=null && partner.owner.equals(attacker)) disconnect(town,towns);
    }
    /** Disconnect only the reciprocal partner, never an unrelated town's pending proposal. */
    public static void disconnect(Settlement source,List<Settlement> towns) {
        UUID previous=source.trading.partner;
        if(previous!=null) for(Settlement other:towns) if(other.id.equals(previous) && source.id.equals(other.trading.partner)) other.trading.partner=null;
        source.trading.partner=null;
    }
}
